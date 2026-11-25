import { beforeEach, describe, expect, it } from "vitest";
import { certificatePayload, createNonce, createToken, mojangCertificateKeys } from "../src/auth";
import { MOJANG_PLAYER_CERTIFICATE_KEYS } from "../src/mojangKeys";
import { handle, resetMemoryForTests } from "../src/api";
import { ADMIN, makeDeps, png, pngResponse, req, SECRET, UUID } from "./helpers";

/**
 * A stand-in for Mojang: a certificate key pair (SHA-1, like the real ones) that signs a player's chat key pair
 * (SHA-256), plus a helper that builds a verify request the way the mod does.
 */
async function fakeMojang() {
	const rsa = (hash: string) => crypto.subtle.generateKey(
		{ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash }, true, ["sign", "verify"]) as Promise<CryptoKeyPair>;
	const mojang = await rsa("SHA-1");
	const mojangSpki = b64(new Uint8Array(await crypto.subtle.exportKey("spki", mojang.publicKey) as ArrayBuffer));
	return {
		keys: [mojangSpki] as readonly string[],
		/** A player's key pair with a certificate for {@code uuid}, valid until {@code expiresAt}. */
		async player(uuid: string, expiresAt: number, signer: CryptoKey = mojang.privateKey) {
			const pair = await rsa("SHA-256");
			const spki = new Uint8Array(await crypto.subtle.exportKey("spki", pair.publicKey) as ArrayBuffer);
			const cert = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", signer, certificatePayload(uuid, expiresAt, spki)));
			return {
				async proof(nonce: string, overrides: Record<string, unknown> = {}) {
					const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", pair.privateKey,
						new TextEncoder().encode("golemaddons-cosmetics:" + nonce)));
					return { nonce, publicKey: b64(spki), keyExpiresAt: expiresAt, keySignature: b64(cert), signature: b64(sig), ...overrides };
				},
			};
		},
	};
}

type FakeMojang = Awaited<ReturnType<typeof fakeMojang>>;

function b64(bytes: Uint8Array): string {
	let s = "";
	for (const b of bytes) s += String.fromCharCode(b);
	return btoa(s);
}

async function bearer(now: number, uuid = UUID) {
	return "Bearer " + (await createToken(SECRET, uuid, now)).token;
}

beforeEach(() => resetMemoryForTests());

describe("health and routing", () => {
	it("health", async () => {
		const { deps } = makeDeps();
		const res = await handle(req("GET", "/v1/health"), deps);
		expect(await res.json()).toEqual({ ok: true, version: "1" });
	});
	it("unknown route and wrong method", async () => {
		const { deps } = makeDeps();
		expect((await handle(req("GET", "/nope"), deps)).status).toBe(404);
		expect((await handle(req("GET", "/v1/profile"), deps)).status).toBe(405);
	});
});

describe("sign-in", () => {
	it("the built-in Mojang keys are valid RSA keys", async () => {
		expect(MOJANG_PLAYER_CERTIFICATE_KEYS.length).toBeGreaterThan(0);
		expect((await mojangCertificateKeys()).length).toBe(MOJANG_PLAYER_CERTIFICATE_KEYS.length);
	});

	it("challenge then verify gives a token for the certified UUID", async () => {
		const mojang = await fakeMojang();
		const { deps, clock } = makeDeps();
		deps.mojangKeys = mojang.keys;
		const ch = await handle(req("POST", "/v1/auth/challenge", { uuid: UUID }), deps);
		const { nonce } = (await ch.json()) as { nonce: string };
		const player = await mojang.player(UUID, clock.now + 86_400_000);
		const res = await handle(req("POST", "/v1/auth/verify", await player.proof(nonce)), deps);
		expect(res.status).toBe(200);
		const body = (await res.json()) as { token: string; uuid: string };
		expect(body.uuid).toBe(UUID);
		expect(body.token.startsWith(UUID + ".")).toBe(true);
	});

	async function verifyError(build: (m: FakeMojang, now: number) => Promise<Record<string, unknown>>) {
		const mojang = await fakeMojang();
		const { deps, clock } = makeDeps();
		deps.mojangKeys = mojang.keys;
		const res = await handle(req("POST", "/v1/auth/verify", await build(mojang, clock.now)), deps);
		return { status: res.status, error: ((await res.json()) as { error: string }).error };
	}

	it("rejects a certificate for another account", async () => {
		expect(await verifyError(async (m, now) => {
			const nonce = (await createNonce(SECRET, UUID, now)).nonce;
			return (await m.player("f".repeat(32), now + 86_400_000)).proof(nonce);
		})).toEqual({ status: 401, error: "bad_certificate" });
	});

	it("rejects a key that Mojang didn't sign", async () => {
		expect(await verifyError(async (m, now) => {
			const forger = (await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048,
				publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-1" }, true, ["sign", "verify"]) as CryptoKeyPair).privateKey;
			const nonce = (await createNonce(SECRET, UUID, now)).nonce;
			return (await m.player(UUID, now + 86_400_000, forger)).proof(nonce);
		})).toEqual({ status: 401, error: "bad_certificate" });
	});

	it("rejects a tampered expiry, an expired key and a signature over a different nonce", async () => {
		expect(await verifyError(async (m, now) => {
			const nonce = (await createNonce(SECRET, UUID, now)).nonce;
			return (await m.player(UUID, now + 1000)).proof(nonce, { keyExpiresAt: now + 999_999_999 });
		})).toEqual({ status: 401, error: "bad_certificate" });
		expect(await verifyError(async (m, now) => {
			const nonce = (await createNonce(SECRET, UUID, now)).nonce;
			return (await m.player(UUID, now - 1)).proof(nonce);
		})).toEqual({ status: 401, error: "key_expired" });
		expect(await verifyError(async (m, now) => {
			const player = await m.player(UUID, now + 86_400_000);
			const nonce = (await createNonce(SECRET, UUID, now)).nonce;
			const other = (await createNonce(SECRET, UUID, now)).nonce;
			return { ...(await player.proof(other)), nonce };
		})).toEqual({ status: 401, error: "bad_signature" });
	});

	it("rejects tampered, foreign-secret and expired nonces", async () => {
		const mojang = await fakeMojang();
		const { deps, clock } = makeDeps();
		deps.mojangKeys = mojang.keys;
		const player = await mojang.player(UUID, clock.now + 86_400_000);
		const good = (await createNonce(SECRET, UUID, clock.now)).nonce;
		const tampered = good.slice(0, -2) + (good.endsWith("AA") ? "BB" : "AA");
		expect((await handle(req("POST", "/v1/auth/verify", await player.proof(tampered)), deps)).status).toBe(401);
		const forged = (await createNonce("y".repeat(40), UUID, clock.now)).nonce;
		expect((await handle(req("POST", "/v1/auth/verify", await player.proof(forged)), deps)).status).toBe(401);
		clock.now += 61_000;
		expect((await handle(req("POST", "/v1/auth/verify", await player.proof(good)), deps)).status).toBe(401);
	});

	it("rejects malformed proofs and unknown fields", async () => {
		const { deps, clock } = makeDeps();
		const nonce = (await createNonce(SECRET, UUID, clock.now)).nonce;
		const base = { nonce, publicKey: "AAAA", keyExpiresAt: clock.now + 1000, keySignature: "AAAA", signature: "AAAA" };
		expect((await handle(req("POST", "/v1/auth/verify", { ...base, publicKey: "not base64!" }), deps)).status).toBe(400);
		expect((await handle(req("POST", "/v1/auth/verify", { ...base, keyExpiresAt: "soon" }), deps)).status).toBe(400);
		expect((await handle(req("POST", "/v1/auth/verify", { ...base, username: "Notch" }), deps)).status).toBe(400);
	});

	it("refuses to sign anything without a strong secret", async () => {
		const { deps } = makeDeps();
		deps.env.TOKEN_SECRET = "short";
		const res = await handle(req("POST", "/v1/auth/challenge", { uuid: UUID }), deps);
		expect(res.status).toBe(500);
		expect(await res.json()).toEqual({ error: "server_misconfigured" });
	});

	it("rate limits verify per IP", async () => {
		const { deps } = makeDeps();
		let last = 0;
		for (let i = 0; i < 11; i++) {
			last = (await handle(req("POST", "/v1/auth/verify", { nonce: "x.y.z.w" }, { "CF-Connecting-IP": "1.2.3.4" }), deps)).status;
		}
		expect(last).toBe(429);
	});
});

describe("profile updates", () => {
	it("needs a valid token; a UUID in the body is never trusted", async () => {
		const { deps, clock } = makeDeps();
		expect((await handle(req("PUT", "/v1/profile", { name: "&aHi" }), deps)).status).toBe(401);
		const forged = "Bearer " + (await createToken("z".repeat(40), UUID, clock.now)).token;
		expect((await handle(req("PUT", "/v1/profile", { name: "&aHi" }, { Authorization: forged }), deps)).status).toBe(401);
		const expired = await bearer(clock.now - 2 * 3600_000);
		expect((await handle(req("PUT", "/v1/profile", { name: "&aHi" }, { Authorization: expired }), deps)).status).toBe(401);
		const withUuid = await handle(req("PUT", "/v1/profile", { name: "&aHi", uuid: "f".repeat(32) }, { Authorization: await bearer(clock.now) }), deps);
		expect(withUuid.status).toBe(400); // unknown field rejected outright
	});

	it("sets, reads back in a batch, and clears a name", async () => {
		const { deps, kv, clock } = makeDeps();
		const auth = { Authorization: await bearer(clock.now) };
		const set = await handle(req("PUT", "/v1/profile", { name: " &6&lZach " }, auth), deps);
		expect(await set.json()).toEqual({ name: "&6&lZach", cape: null });
		expect(JSON.parse(kv.data.get(`p:${UUID}`) as string).name).toBe("&6&lZach");

		const batch = await handle(req("POST", "/v1/profiles/batch", { uuids: [UUID, "f".repeat(32)] }), deps);
		expect(await batch.json()).toEqual({ profiles: { [UUID]: { name: "&6&lZach" } } });

		clock.now += 31_000;
		const cleared = await handle(req("PUT", "/v1/profile", { name: null }, { Authorization: await bearer(clock.now) }), deps);
		expect(await cleared.json()).toEqual({ name: null, cape: null });
		expect(kv.data.has(`p:${UUID}`)).toBe(false);
	});

	it("rejects invalid names and wrong types", async () => {
		const { deps, clock } = makeDeps();
		const auth = { Authorization: await bearer(clock.now) };
		expect(await (await handle(req("PUT", "/v1/profile", { name: "&zBad" }, auth), deps)).json()).toEqual({ error: "name_invalid" });
		expect((await handle(req("PUT", "/v1/profile", { name: 5 }, auth), deps)).status).toBe(400);
		expect((await handle(req("PUT", "/v1/profile", {}, auth), deps)).status).toBe(400);
		expect((await handle(req("PUT", "/v1/profile", "not json", auth), deps)).status).toBe(400);
		expect((await handle(req("PUT", "/v1/profile", { name: "x".repeat(5000) }, auth), deps)).status).toBe(413);
	});

	it("rate limits updates to one per 30 s", async () => {
		const { deps, clock } = makeDeps();
		await handle(req("PUT", "/v1/profile", { name: "One" }, { Authorization: await bearer(clock.now) }), deps);
		clock.now += 10_000;
		const res = await handle(req("PUT", "/v1/profile", { name: "Two" }, { Authorization: await bearer(clock.now) }), deps);
		expect(res.status).toBe(429);
		expect(res.headers.get("Retry-After")).toBe("20");
	});
});

describe("capes", () => {
	const IMG = "https://img.example.com/cape.png";

	it("downloads, validates, stores once, and serves by hash", async () => {
		const bytes = png(64, 32);
		const { deps, kv, clock } = makeDeps({ [IMG]: () => pngResponse(bytes) });
		const res = await handle(req("PUT", "/v1/profile", { capeUrl: IMG }, { Authorization: await bearer(clock.now) }), deps);
		const { cape } = (await res.json()) as { cape: string };
		expect(cape).toMatch(/^[0-9a-f]{64}$/);
		const writesAfterFirst = kv.writes;

		// Another player with the same image: the c: key is not written again.
		const other = "e".repeat(32);
		await handle(req("PUT", "/v1/profile", { capeUrl: IMG }, { Authorization: await bearer(clock.now, other) }), deps);
		expect(kv.writes).toBe(writesAfterFirst + 1); // only the second profile

		const served = await handle(req("GET", `/v1/cape/${cape}.png`), deps);
		expect(served.headers.get("Content-Type")).toBe("image/png");
		expect(served.headers.get("Cache-Control")).toContain("immutable");
		expect(new Uint8Array(await served.arrayBuffer())).toEqual(bytes);
		expect((await handle(req("GET", `/v1/cape/${"0".repeat(64)}.png`), deps)).status).toBe(404);
		expect((await handle(req("GET", "/v1/cape/../../x.png"), deps)).status).toBe(404);
	});

	async function capeError(routes: Record<string, (u: URL) => Response | Promise<Response>>, url = IMG): Promise<string> {
		const { deps, clock } = makeDeps(routes);
		const res = await handle(req("PUT", "/v1/profile", { capeUrl: url }, { Authorization: await bearer(clock.now) }), deps);
		return ((await res.json()) as { error: string }).error;
	}

	it("rejects bad URLs before fetching", async () => {
		expect(await capeError({}, "https://127.0.0.1/c.png")).toBe("cape_url_invalid");
		expect(await capeError({}, "http://img.example.com/c.png")).toBe("cape_url_invalid");
		expect(await capeError({}, "https://api.example.workers.dev/v1/cape/x.png")).toBe("cape_url_invalid");
	});

	it("re-validates redirects and limits them", async () => {
		const redirect = (to: string) => () => new Response(null, { status: 302, headers: { Location: to } });
		expect(await capeError({ [IMG]: redirect("https://127.0.0.1/x.png") })).toBe("cape_redirect");
		expect(await capeError({ [IMG]: redirect("http://localhost/x.png") })).toBe("cape_redirect");
		expect(await capeError({ [IMG]: redirect("https://metadata.internal/x") })).toBe("cape_redirect");
		const loop = {
			[IMG]: redirect("https://img.example.com/a"),
			"https://img.example.com/a": redirect("https://img.example.com/b"),
			"https://img.example.com/b": redirect("https://img.example.com/c"),
			"https://img.example.com/c": redirect("https://img.example.com/d"),
		};
		expect(await capeError(loop)).toBe("cape_redirect");
	});

	it("follows a valid relative redirect", async () => {
		const bytes = png(128, 64);
		const { deps, clock } = makeDeps({
			[IMG]: () => new Response(null, { status: 301, headers: { Location: "/real.png" } }),
			"https://img.example.com/real.png": () => pngResponse(bytes),
		});
		const res = await handle(req("PUT", "/v1/profile", { capeUrl: IMG }, { Authorization: await bearer(clock.now) }), deps);
		expect(res.status).toBe(200);
	});

	it("rejects non-PNGs, wrong sizes, oversized bodies (with or without Content-Length) and errors", async () => {
		expect(await capeError({ [IMG]: () => new Response("<html>", { headers: { "Content-Type": "text/html" } }) })).toBe("cape_not_png");
		expect(await capeError({ [IMG]: () => pngResponse(new TextEncoder().encode("GIF89a" + ".".repeat(40)), {}) })).toBe("cape_not_png");
		expect(await capeError({ [IMG]: () => pngResponse(png(64, 64)) })).toBe("cape_bad_size");
		expect(await capeError({ [IMG]: () => pngResponse(png(64, 32), { "Content-Type": "image/png", "Content-Length": "999999" }) })).toBe("cape_too_large");
		const huge = () => new Response(new ReadableStream({
			pull(c) { c.enqueue(new Uint8Array(64 * 1024)); },
		}), { headers: { "Content-Type": "image/png" } });
		expect(await capeError({ [IMG]: huge })).toBe("cape_too_large");
		expect(await capeError({ [IMG]: () => new Response("no", { status: 404 }) })).toBe("cape_fetch_failed");
		expect(await capeError({})).toBe("cape_fetch_failed"); // network error
	});

	it("times out slow hosts", async () => {
		const slow = (_u: URL, init?: RequestInit) => new Promise<Response>((_, reject) => {
			init?.signal?.addEventListener("abort", () => reject(init.signal!.reason));
		});
		expect(await capeError({ [IMG]: slow as never })).toBe("cape_timeout");
	}, 10_000);
});

describe("batch", () => {
	it("validates input and limits size", async () => {
		const { deps } = makeDeps();
		const many = Array.from({ length: 101 }, (_, i) => i.toString(16).padStart(32, "0"));
		expect((await handle(req("POST", "/v1/profiles/batch", { uuids: many }), deps)).status).toBe(400);
		expect((await handle(req("POST", "/v1/profiles/batch", { uuids: ["nope"] }), deps)).status).toBe(400);
		expect((await handle(req("POST", "/v1/profiles/batch", { uuids: [UUID], extra: 1 }), deps)).status).toBe(400);
		expect((await handle(req("POST", "/v1/profiles/batch", { uuids: [UUID] }, { "Content-Type": "text/plain" }), deps)).status).toBe(415);
	});

	it("serves repeat lookups from memory instead of KV", async () => {
		const { deps, kv } = makeDeps();
		await handle(req("POST", "/v1/profiles/batch", { uuids: [UUID] }), deps);
		const reads = kv.reads;
		await handle(req("POST", "/v1/profiles/batch", { uuids: [UUID] }), deps);
		expect(kv.reads).toBe(reads);
	});

	it("drops corrupt stored data instead of passing it on", async () => {
		const { deps, kv } = makeDeps();
		kv.data.set(`p:${UUID}`, JSON.stringify({ v: 1, name: "Na\u202eme", cape: "../etc", updatedAt: 0 }));
		const res = await handle(req("POST", "/v1/profiles/batch", { uuids: [UUID] }), deps);
		expect(await res.json()).toEqual({ profiles: {} });
	});
});

describe("admin", () => {
	it("deletes only with the right secret", async () => {
		const { deps, kv } = makeDeps();
		kv.data.set(`p:${UUID}`, JSON.stringify({ v: 1, name: "Bad", cape: null, updatedAt: 0 }));
		expect((await handle(req("DELETE", `/v1/admin/profile/${UUID}`, undefined, { "X-Admin-Secret": "wrong" }), deps)).status).toBe(403);
		expect(kv.data.has(`p:${UUID}`)).toBe(true);
		expect((await handle(req("DELETE", `/v1/admin/profile/${UUID}`, undefined, { "X-Admin-Secret": ADMIN }), deps)).status).toBe(200);
		expect(kv.data.has(`p:${UUID}`)).toBe(false);
	});

	it("is disabled when no admin secret is set", async () => {
		const { deps } = makeDeps();
		deps.env.ADMIN_SECRET = undefined;
		expect((await handle(req("DELETE", `/v1/admin/profile/${UUID}`, undefined, { "X-Admin-Secret": "" }), deps)).status).toBe(404);
	});
});
