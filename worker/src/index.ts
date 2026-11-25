// Worker entry point. Only entry points live here: the Workers runtime treats every named export of the main module
// as one, so all logic and constants are in api.ts. PartyRoom is the party finder's Durable Object class.
import { handle, type CacheLike, type Env } from "./api";

export { PartyRoom } from "./partyRoom";

export default {
	async fetch(request: Request, env: Env, ctx: { waitUntil(p: Promise<unknown>): void }): Promise<Response> {
		const cache = (globalThis as unknown as { caches?: { default?: CacheLike } }).caches?.default ?? null;
		return handle(request, {
			env,
			kv: env.COSMETICS,
			cache,
			fetch: fetch.bind(globalThis),
			now: Date.now,
			waitUntil: (p) => ctx.waitUntil(p),
		});
	},
};
