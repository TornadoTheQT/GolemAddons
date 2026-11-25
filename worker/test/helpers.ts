import { crc32 } from "../src/validate";
import type { CacheLike, Deps, KV } from "../src/api";

export const SECRET = "x".repeat(40);
export const ADMIN = "a".repeat(40);
export const UUID = "069a79f444e94726a5befca90e38aaf5";

export class FakeKV implements KV {
	data = new Map<string, string | Uint8Array>();
	reads = 0;
	writes = 0;
	get(key: string, type: "text"): Promise<string | null>;
	get(key: string, type: "arrayBuffer"): Promise<ArrayBuffer | null>;
	async get(key: string, type: "text" | "arrayBuffer"): Promise<string | ArrayBuffer | null> {
		this.reads++;
		const v = this.data.get(key);
		if (v === undefined) return null;
		if (type === "text") return typeof v === "string" ? v : new TextDecoder().decode(v);
		const bytes = typeof v === "string" ? new TextEncoder().encode(v) : v;
		return bytes.slice().buffer;
	}
	async put(key: string, value: string | ArrayBuffer | Uint8Array): Promise<void> {
		this.writes++;
		this.data.set(key, typeof value === "string" ? value : new Uint8Array(value instanceof ArrayBuffer ? value : value.slice()));
	}
	async delete(key: string): Promise<void> {
		this.data.delete(key);
	}
}

export class FakeCache implements CacheLike {
	data = new Map<string, Response>();
	async match(request: Request) {
		return this.data.get(request.url)?.clone();
	}
	async put(request: Request, response: Response) {
		this.data.set(request.url, response.clone());
	}
	async delete(request: Request) {
		return this.data.delete(request.url);
	}
}

export type Route = (url: URL, init?: RequestInit) => Response | Promise<Response>;

export function makeDeps(routes: Record<string, Route> = {}, now = 1_700_000_000_000) {
	const kv = new FakeKV();
	const cache = new FakeCache();
	const pending: Promise<unknown>[] = [];
	const fetched: string[] = [];
	const clock = { now };
	const fetchImpl = (async (input: RequestInfo | URL, init?: RequestInit) => {
		const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url);
		fetched.push(url.toString());
		const route = routes[url.origin + url.pathname] ?? routes[url.origin];
		if (!route) throw new TypeError("network error: " + url);
		return route(url, init);
	}) as typeof fetch;
	const deps: Deps = {
		env: { COSMETICS: kv, TOKEN_SECRET: SECRET, ADMIN_SECRET: ADMIN },
		kv,
		cache,
		fetch: fetchImpl,
		now: () => clock.now,
		waitUntil: (p) => pending.push(p),
	};
	return { deps, kv, cache, fetched, clock, settle: () => Promise.all(pending) };
}

export function png(width: number, height: number, extra = 0): Uint8Array<ArrayBuffer> {
	const sig = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
	const ihdr = new Uint8Array(13);
	const v = new DataView(ihdr.buffer);
	v.setInt32(0, width);
	v.setInt32(4, height);
	ihdr[8] = 8;
	ihdr[9] = 6;
	const chunk = (type: string, data: Uint8Array) => {
		const out = new Uint8Array(12 + data.length);
		const dv = new DataView(out.buffer);
		dv.setUint32(0, data.length);
		for (let i = 0; i < 4; i++) out[4 + i] = type.charCodeAt(i);
		out.set(data, 8);
		dv.setUint32(8 + data.length, crc32(out.subarray(4, 8 + data.length)));
		return out;
	};
	const parts = [new Uint8Array(sig), chunk("IHDR", ihdr), chunk("IEND", new Uint8Array(0)), new Uint8Array(extra)];
	const total = parts.reduce((n, p) => n + p.length, 0);
	const out = new Uint8Array(total);
	let o = 0;
	for (const p of parts) {
		out.set(p, o);
		o += p.length;
	}
	return out;
}

export function pngResponse(bytes: Uint8Array<ArrayBuffer>, headers: Record<string, string> = { "Content-Type": "image/png" }): Response {
	return new Response(bytes, { status: 200, headers });
}

export function req(method: string, path: string, body?: unknown, headers: Record<string, string> = {}): Request {
	const init: RequestInit = { method, headers: { ...headers } };
	if (body !== undefined) {
		init.body = typeof body === "string" ? body : JSON.stringify(body);
		(init.headers as Record<string, string>)["Content-Type"] ??= "application/json";
	}
	return new Request("https://api.example.workers.dev" + path, init);
}
