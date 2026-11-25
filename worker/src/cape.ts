import { CAPE_MAX_BYTES, checkCapeUrl, checkPng } from "./validate";

export class ApiError extends Error {
	constructor(readonly status: number, readonly code: string, readonly retryAfter = 0) {
		super(code);
	}
}

const MAX_REDIRECTS = 3;
const TOTAL_TIMEOUT_MS = 5_000;

/**
 * Downloads a cape image safely. Every hop is re-validated (https, no IPs or private names, no credentials, port
 * 443); redirects are followed by hand, at most 3; the whole download has 5 s; the body is cut off at 256 KiB even
 * if Content-Length lies; then the PNG header is checked. Returns the bytes, their SHA-256 hex and the size.
 *
 * Limitation: a Worker can't control DNS, so a public name that resolves to a private address can't be excluded by
 * name checks alone. Workers run on Cloudflare's network with no route into your own network, which limits that.
 */
export async function fetchCape(fetchImpl: typeof fetch, input: string, ownHost: string | null, userAgent: string):
	Promise<{ bytes: Uint8Array<ArrayBuffer>; sha256: string; width: number; height: number }> {
	const deadline = Date.now() + TOTAL_TIMEOUT_MS;
	let target = input;
	for (let hop = 0; ; hop++) {
		const check = checkCapeUrl(target, ownHost);
		if (!check.ok) throw new ApiError(400, hop === 0 ? "cape_url_invalid" : "cape_redirect");
		const remaining = deadline - Date.now();
		if (remaining <= 0) throw new ApiError(400, "cape_timeout");
		let res: Response;
		try {
			res = await fetchImpl(check.url.toString(), {
				redirect: "manual",
				signal: AbortSignal.timeout(remaining),
				headers: { "User-Agent": userAgent, Accept: "image/png" },
			});
		} catch (e) {
			throw new ApiError(400, isTimeout(e) ? "cape_timeout" : "cape_fetch_failed");
		}
		if (res.status >= 300 && res.status < 400) {
			await res.body?.cancel();
			const location = res.headers.get("Location");
			if (!location || hop >= MAX_REDIRECTS) throw new ApiError(400, "cape_redirect");
			try {
				target = new URL(location, check.url).toString();
			} catch {
				throw new ApiError(400, "cape_redirect");
			}
			continue;
		}
		if (res.status !== 200) {
			await res.body?.cancel();
			throw new ApiError(400, "cape_fetch_failed");
		}
		const type = res.headers.get("Content-Type");
		if (type && !type.toLowerCase().startsWith("image/png")) {
			await res.body?.cancel();
			throw new ApiError(400, "cape_not_png");
		}
		const length = Number(res.headers.get("Content-Length") ?? "0");
		if (length > CAPE_MAX_BYTES) {
			await res.body?.cancel();
			throw new ApiError(400, "cape_too_large");
		}
		const bytes = await readCapped(res, CAPE_MAX_BYTES);
		const png = checkPng(bytes);
		if (!png.ok) throw new ApiError(400, png.problem);
		const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
		const sha256 = Array.from(digest, (b) => b.toString(16).padStart(2, "0")).join("");
		return { bytes, sha256, width: png.width, height: png.height };
	}
}

/** Reads at most {@code max} bytes; more is an error, not a truncation. */
export async function readCapped(res: Response, max: number): Promise<Uint8Array<ArrayBuffer>> {
	if (!res.body) return new Uint8Array(0);
	const reader = res.body.getReader();
	const chunks: Uint8Array[] = [];
	let total = 0;
	try {
		for (;;) {
			const { done, value } = await reader.read();
			if (done) break;
			total += value.byteLength;
			if (total > max) {
				await reader.cancel();
				throw new ApiError(400, "cape_too_large");
			}
			chunks.push(value);
		}
	} catch (e) {
		if (e instanceof ApiError) throw e;
		throw new ApiError(400, isTimeout(e) ? "cape_timeout" : "cape_fetch_failed");
	}
	const out = new Uint8Array(total);
	let offset = 0;
	for (const c of chunks) {
		out.set(c, offset);
		offset += c.byteLength;
	}
	return out;
}

function isTimeout(e: unknown): boolean {
	return e instanceof Error && (e.name === "TimeoutError" || e.name === "AbortError");
}
