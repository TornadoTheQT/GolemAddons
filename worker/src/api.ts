// GolemAddons cosmetics API logic (the Worker entry point is index.ts).
// Synced custom names and capes for players running the mod.
// Storage (one KV namespace, binding COSMETICS):
//   p:<uuid32>   -> {"v":1,"name":"&6Zach"|null,"cape":"<sha256>"|null,"updatedAt":<ms>}
//   c:<sha256>   -> raw PNG bytes (stored once, however many players use the image)
// Reads go memory (per isolate) -> Cache API -> KV, to stay well inside the free plan's KV read quota.

import {
	checkKeyProof, createNonce, createToken, MIN_SECRET_LENGTH, openNonce, openToken, secretEquals,
} from "./auth";
import { ApiError, fetchCape } from "./cape";
import { normalizeUuid, parseName, SHA256_HEX } from "./validate";

export const API_VERSION = "1";
export const BATCH_MAX = 100;
const BODY_MAX_BYTES = 4 * 1024;
const UPDATE_INTERVAL_MS = 30_000;
const PROFILE_TTL_S = 60;
const ABSENT_TTL_S = 300;
const MEMORY_MAX = 10_000;
const VERIFY_PER_MINUTE = 10;

export interface KV {
	get(key: string, type: "text"): Promise<string | null>;
	get(key: string, type: "arrayBuffer"): Promise<ArrayBuffer | null>;
	put(key: string, value: string | ArrayBuffer | Uint8Array, options?: { metadata?: unknown }): Promise<void>;
	delete(key: string): Promise<void>;
}

export interface CacheLike {
	match(request: Request): Promise<Response | undefined>;
	put(request: Request, response: Response): Promise<void>;
	delete(request: Request): Promise<boolean>;
}

/** The bits of a Durable Object namespace binding the Worker uses. */
export interface RoomNamespace {
	idFromName(name: string): unknown;
	get(id: unknown): { fetch(request: Request): Promise<Response> };
}

export interface Env {
	COSMETICS: KV;
	/** Party finder rooms (see partyRoom.ts). Without this binding the party finder answers 404. */
	PARTIES?: RoomNamespace;
	TOKEN_SECRET?: string;
	ADMIN_SECRET?: string;
}

export interface Deps {
	env: Env;
	kv: KV;
	cache: CacheLike | null;
	fetch: typeof fetch;
	now: () => number;
	waitUntil: (p: Promise<unknown>) => void;
	/** Overrides Mojang's certificate keys (tests only). */
	mojangKeys?: readonly string[];
}

interface Profile {
	v: 1;
	name: string | null;
	cape: string | null;
	updatedAt: number;
}

/** Per-isolate cache of profiles (null = known to have none). Cheap and works on workers.dev too. */
const memory = new Map<string, { profile: Profile | null; expires: number }>();
/** Per-isolate sign-in limiter, so nobody can use us to hammer the verification. */
const verifyCounts = new Map<string, { count: number; resetAt: number }>();
/** Per-isolate party finder connection limiter, per account. */
const connectCounts = new Map<string, { count: number; resetAt: number }>();
const CONNECTS_PER_MINUTE = 12;

export function resetMemoryForTests(): void {
	memory.clear();
	verifyCounts.clear();
	connectCounts.clear();
}

export async function handle(request: Request, deps: Deps): Promise<Response> {
	try {
		return await route(request, deps);
	} catch (e) {
		if (e instanceof ApiError) return error(e.status, e.code, e.retryAfter);
		console.error("unhandled", e);
		return error(500, "internal");
	}
}

async function route(request: Request, deps: Deps): Promise<Response> {
	const url = new URL(request.url);
	const path = url.pathname;
	const method = request.method;

	if (path === "/v1/health") return method === "GET" ? json({ ok: true, version: API_VERSION }) : error(405, "method_not_allowed");
	if (path === "/v1/profiles/batch") return method === "POST" ? batch(request, deps) : error(405, "method_not_allowed");
	if (path === "/v1/auth/challenge") return method === "POST" ? challenge(request, deps) : error(405, "method_not_allowed");
	if (path === "/v1/auth/verify") return method === "POST" ? verify(request, deps) : error(405, "method_not_allowed");
	if (path === "/v1/profile") return method === "PUT" ? update(request, deps) : error(405, "method_not_allowed");
	const cape = /^\/v1\/cape\/([0-9a-f]{64})\.png$/.exec(path);
	if (cape) return method === "GET" ? getCape(request, deps, cape[1]) : error(405, "method_not_allowed");
	if (path === "/v1/pf/ws") return method === "GET" ? partyFinder(request, deps, url) : error(405, "method_not_allowed");
	const admin = /^\/v1\/admin\/profile\/([0-9a-fA-F-]{32,36})$/.exec(path);
	if (admin) return method === "DELETE" ? adminDelete(request, deps, admin[1]) : error(405, "method_not_allowed");
	return error(404, "not_found");
}

// ---- reads ----

async function batch(request: Request, deps: Deps): Promise<Response> {
	const body = await readJson(request, ["uuids"]);
	const list = body.uuids;
	if (!Array.isArray(list) || list.length > BATCH_MAX) throw new ApiError(400, "bad_request");
	const uuids = new Set<string>();
	for (const item of list) {
		const uuid = normalizeUuid(item);
		if (!uuid) throw new ApiError(400, "bad_request");
		uuids.add(uuid);
	}
	const origin = new URL(request.url).origin;
	const results = await Promise.all([...uuids].map(async (uuid) => [uuid, await loadProfile(deps, origin, uuid)] as const));
	const profiles: Record<string, { name?: string; cape?: string }> = {};
	for (const [uuid, profile] of results) {
		if (!profile) continue;
		const entry: { name?: string; cape?: string } = {};
		if (profile.name) entry.name = profile.name;
		if (profile.cape) entry.cape = profile.cape;
		profiles[uuid] = entry;
	}
	return json({ profiles });
}

function cacheKey(origin: string, uuid: string): Request {
	return new Request(`${origin}/__cache/v1/p/${uuid}`);
}

async function loadProfile(deps: Deps, origin: string, uuid: string): Promise<Profile | null> {
	const now = deps.now();
	const hit = memory.get(uuid);
	if (hit && hit.expires > now) return hit.profile;

	let profile: Profile | null | undefined;
	const key = cacheKey(origin, uuid);
	if (deps.cache) {
		const cached = await deps.cache.match(key).catch(() => undefined);
		if (cached) profile = sanitizeProfile(await cached.text());
	}
	if (profile === undefined) {
		profile = sanitizeProfile(await deps.kv.get(`p:${uuid}`, "text"));
		if (deps.cache) {
			const ttl = profile ? PROFILE_TTL_S : ABSENT_TTL_S;
			const res = new Response(JSON.stringify(profile), { headers: { "Cache-Control": `public, max-age=${ttl}` } });
			deps.waitUntil(deps.cache.put(key, res).catch(() => undefined));
		}
	}
	remember(uuid, profile, now);
	return profile;
}

function remember(uuid: string, profile: Profile | null, now: number): void {
	if (memory.size >= MEMORY_MAX) memory.clear();
	memory.set(uuid, { profile, expires: now + (profile ? PROFILE_TTL_S : ABSENT_TTL_S) * 1000 });
}

/** Stored data is re-validated on the way out, so a bad write can never reach clients. */
function sanitizeProfile(text: string | null): Profile | null {
	if (!text) return null;
	try {
		const raw = JSON.parse(text) as Partial<Profile> | null;
		if (!raw || typeof raw !== "object") return null;
		const name = typeof raw.name === "string" && parseName(raw.name).ok ? raw.name : null;
		const cape = typeof raw.cape === "string" && SHA256_HEX.test(raw.cape) ? raw.cape : null;
		if (!name && !cape) return null;
		return { v: 1, name, cape, updatedAt: typeof raw.updatedAt === "number" ? raw.updatedAt : 0 };
	} catch {
		return null;
	}
}

async function getCape(request: Request, deps: Deps, hash: string): Promise<Response> {
	const key = new Request(new URL(request.url).toString());
	if (deps.cache) {
		const cached = await deps.cache.match(key).catch(() => undefined);
		if (cached) return cached;
	}
	const bytes = await deps.kv.get(`c:${hash}`, "arrayBuffer");
	if (!bytes) return error(404, "not_found");
	const res = new Response(bytes, {
		headers: {
			"Content-Type": "image/png",
			"Cache-Control": "public, max-age=31536000, immutable",
			"X-Content-Type-Options": "nosniff",
			"Content-Security-Policy": "default-src 'none'; sandbox",
		},
	});
	if (deps.cache) deps.waitUntil(deps.cache.put(key, res.clone()).catch(() => undefined));
	return res;
}

// ---- sign-in ----

function secret(deps: Deps): string {
	const s = deps.env.TOKEN_SECRET;
	if (!s || s.length < MIN_SECRET_LENGTH) {
		console.error("TOKEN_SECRET is missing or shorter than 32 characters; run `wrangler secret put TOKEN_SECRET`");
		throw new ApiError(500, "server_misconfigured");
	}
	return s;
}

async function challenge(request: Request, deps: Deps): Promise<Response> {
	const body = await readJson(request, ["uuid"]);
	const uuid = normalizeUuid(body.uuid);
	if (!uuid) throw new ApiError(400, "bad_request");
	const { nonce, expiresAt } = await createNonce(secret(deps), uuid, deps.now());
	return json({ nonce, expiresAt });
}

async function verify(request: Request, deps: Deps): Promise<Response> {
	const body = await readJson(request, ["nonce", "publicKey", "keyExpiresAt", "keySignature", "signature"]);
	const key = secret(deps);
	limitVerify(request, deps.now());
	const uuid = await openNonce(key, body.nonce, deps.now());
	if (!uuid) throw new ApiError(401, "unauthorized");
	const problem = await checkKeyProof(uuid, body.nonce as string, {
		publicKey: body.publicKey, keyExpiresAt: body.keyExpiresAt, keySignature: body.keySignature, signature: body.signature,
	}, deps.now(), deps.mojangKeys);
	if (problem) throw new ApiError(problem === "bad_request" ? 400 : 401, problem);
	const { token, expiresAt } = await createToken(key, uuid, deps.now());
	return json({ token, uuid, expiresAt });
}

function limitVerify(request: Request, now: number): void {
	const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
	const entry = verifyCounts.get(ip);
	if (!entry || entry.resetAt <= now) {
		if (verifyCounts.size > 10_000) verifyCounts.clear();
		verifyCounts.set(ip, { count: 1, resetAt: now + 60_000 });
		return;
	}
	if (++entry.count > VERIFY_PER_MINUTE) {
		throw new ApiError(429, "rate_limited", Math.ceil((entry.resetAt - now) / 1000));
	}
}

// ---- party finder ----

/**
 * Opens a party finder WebSocket. The session token (from the same sign-in as cosmetics) proves the UUID; the room is
 * chosen by {@code server}, a hash of the Minecraft server's address, so players only see parties on their server.
 * The room only ever sees the UUID this Worker verified.
 */
async function partyFinder(request: Request, deps: Deps, url: URL): Promise<Response> {
	const rooms = deps.env.PARTIES;
	if (!rooms) return error(404, "not_found");
	const uuid = await openToken(secret(deps), request.headers.get("Authorization"), deps.now());
	if (!uuid) throw new ApiError(401, "unauthorized");
	if ((request.headers.get("Upgrade") ?? "").toLowerCase() !== "websocket") throw new ApiError(426, "upgrade_required");
	const server = url.searchParams.get("server") ?? "";
	const name = url.searchParams.get("name") ?? "";
	if (!/^[0-9a-f]{32}$/.test(server) || !/^[A-Za-z0-9_]{1,16}$/.test(name)) throw new ApiError(400, "bad_request");

	const now = deps.now();
	const entry = connectCounts.get(uuid);
	if (!entry || entry.resetAt <= now) {
		if (connectCounts.size > 10_000) connectCounts.clear();
		connectCounts.set(uuid, { count: 1, resetAt: now + 60_000 });
	} else if (++entry.count > CONNECTS_PER_MINUTE) {
		throw new ApiError(429, "rate_limited", Math.ceil((entry.resetAt - now) / 1000));
	}

	// A fresh request: nothing the client sent besides the upgrade reaches the room.
	const inner = new Request("https://party-room/ws", {
		headers: { Upgrade: "websocket", "X-GA-UUID": uuid, "X-GA-Name": name },
	});
	return rooms.get(rooms.idFromName(server)).fetch(inner);
}

// ---- writes ----

async function update(request: Request, deps: Deps): Promise<Response> {
	const key = secret(deps);
	const uuid = await openToken(key, request.headers.get("Authorization"), deps.now());
	if (!uuid) throw new ApiError(401, "unauthorized");
	const body = await readJson(request, ["name", "capeUrl"]);
	const hasName = "name" in body;
	const hasCape = "capeUrl" in body;
	if (!hasName && !hasCape) throw new ApiError(400, "bad_request");
	if (hasName && body.name !== null && typeof body.name !== "string") throw new ApiError(400, "bad_request");
	if (hasCape && body.capeUrl !== null && typeof body.capeUrl !== "string") throw new ApiError(400, "bad_request");

	const now = deps.now();
	const current = sanitizeProfile(await deps.kv.get(`p:${uuid}`, "text"));
	if (current && now - current.updatedAt < UPDATE_INTERVAL_MS) {
		throw new ApiError(429, "rate_limited", Math.ceil((current.updatedAt + UPDATE_INTERVAL_MS - now) / 1000));
	}

	let name = current?.name ?? null;
	let cape = current?.cape ?? null;
	if (hasName) {
		if (body.name === null) {
			name = null;
		} else {
			const parsed = parseName(body.name);
			if (!parsed.ok) throw new ApiError(400, "name_invalid");
			name = parsed.raw;
		}
	}
	if (hasCape) {
		if (body.capeUrl === null) {
			cape = null;
		} else {
			const ownHost = new URL(request.url).hostname;
			const image = await fetchCape(deps.fetch, body.capeUrl as string, ownHost, `golemaddons-cosmetics/${API_VERSION}`);
			cape = image.sha256;
			if (!(await deps.kv.get(`c:${cape}`, "arrayBuffer"))) {
				await deps.kv.put(`c:${cape}`, image.bytes, { metadata: { w: image.width, h: image.height, size: image.bytes.byteLength } });
			}
		}
	}

	const origin = new URL(request.url).origin;
	let saved: Profile | null = null;
	if (name || cape) {
		saved = { v: 1, name, cape, updatedAt: now };
		await deps.kv.put(`p:${uuid}`, JSON.stringify(saved));
	} else {
		await deps.kv.delete(`p:${uuid}`);
	}
	remember(uuid, saved, now);
	if (deps.cache) deps.waitUntil(deps.cache.delete(cacheKey(origin, uuid)).catch(() => false));
	return json({ name: saved?.name ?? null, cape: saved?.cape ?? null });
}

async function adminDelete(request: Request, deps: Deps, rawUuid: string): Promise<Response> {
	const configured = deps.env.ADMIN_SECRET;
	if (!configured || configured.length < MIN_SECRET_LENGTH) return error(404, "not_found");
	const given = request.headers.get("X-Admin-Secret") ?? "";
	if (!(await secretEquals(given, configured))) throw new ApiError(403, "forbidden");
	const uuid = normalizeUuid(rawUuid);
	if (!uuid) throw new ApiError(400, "bad_request");
	await deps.kv.delete(`p:${uuid}`);
	memory.delete(uuid);
	if (deps.cache) deps.waitUntil(deps.cache.delete(cacheKey(new URL(request.url).origin, uuid)).catch(() => false));
	return json({ deleted: uuid });
}

// ---- helpers ----

async function readJson(request: Request, allowed: string[]): Promise<Record<string, unknown>> {
	const type = request.headers.get("Content-Type") ?? "";
	if (!type.toLowerCase().startsWith("application/json")) throw new ApiError(415, "bad_request");
	const declared = Number(request.headers.get("Content-Length") ?? "0");
	if (declared > BODY_MAX_BYTES) throw new ApiError(413, "too_large");
	const bytes = await readBody(request.body, BODY_MAX_BYTES);
	let value: unknown;
	try {
		value = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
	} catch {
		throw new ApiError(400, "bad_request");
	}
	if (!value || typeof value !== "object" || Array.isArray(value)) throw new ApiError(400, "bad_request");
	for (const key of Object.keys(value)) {
		if (!allowed.includes(key)) throw new ApiError(400, "bad_request");
	}
	return value as Record<string, unknown>;
}

async function readBody(stream: ReadableStream<Uint8Array> | null, max: number): Promise<Uint8Array> {
	if (!stream) return new Uint8Array(0);
	const reader = stream.getReader();
	const chunks: Uint8Array[] = [];
	let total = 0;
	for (;;) {
		const { done, value } = await reader.read();
		if (done) break;
		total += value.byteLength;
		if (total > max) {
			await reader.cancel();
			throw new ApiError(413, "too_large");
		}
		chunks.push(value);
	}
	const out = new Uint8Array(total);
	let offset = 0;
	for (const c of chunks) {
		out.set(c, offset);
		offset += c.byteLength;
	}
	return out;
}

const JSON_HEADERS = {
	"Content-Type": "application/json; charset=utf-8",
	"Cache-Control": "no-store",
	"X-Content-Type-Options": "nosniff",
};

function json(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

function error(status: number, code: string, retryAfter = 0): Response {
	const headers: Record<string, string> = { ...JSON_HEADERS };
	if (retryAfter > 0) headers["Retry-After"] = String(retryAfter);
	return new Response(JSON.stringify({ error: code }), { status, headers });
}
