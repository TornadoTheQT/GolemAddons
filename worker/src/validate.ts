// Validation shared in spirit with the mod (src/main/java/com/golemaddons/cosmetics/*). Keep the rules identical:
// CosmeticName.java, CapeUrlRules.java, CapePng.java, Hashes.java.

export const NAME_MAX_RAW = 64;
export const NAME_MAX_VISIBLE = 24;
export const URL_MAX_LENGTH = 512;
export const CAPE_MAX_BYTES = 256 * 1024;

/** Characters never allowed in names: section sign, control, format (bidi, zero-width), separators, private use, unassigned, lone surrogates. */
const BAD_NAME_CHAR = /[§\p{Cc}\p{Cf}\p{Zl}\p{Zp}\p{Co}\p{Cn}\p{Cs}]/u;
const FORMAT_CODES = "klmno";

export type NameResult = { ok: true; raw: string } | { ok: false; problem: string };

export function parseName(input: unknown): NameResult {
	if (typeof input !== "string") return { ok: false, problem: "empty" };
	const raw = input.trim();
	if (raw.length === 0) return { ok: false, problem: "empty" };
	if (raw.length > NAME_MAX_RAW) return { ok: false, problem: "too_long" };

	let visible = 0;
	let obfuscated = false;
	let anyClear = false;
	let anyNonSpace = false;
	const cps = Array.from(raw); // code points; a lone surrogate stays a single element
	for (let i = 0; i < cps.length; i++) {
		let ch = cps[i];
		if (ch === "&") {
			if (i + 1 >= cps.length) return { ok: false, problem: "bad_code" };
			const code = cps[++i].toLowerCase();
			if (code !== "&") {
				const isColor = /^[0-9a-f]$/.test(code);
				if (!isColor && !FORMAT_CODES.includes(code) && code !== "r") return { ok: false, problem: "bad_code" };
				if (code === "k") obfuscated = true;
				else if (isColor || code === "r") obfuscated = false;
				continue;
			}
			ch = "&";
		}
		if (BAD_NAME_CHAR.test(ch)) return { ok: false, problem: "bad_character" };
		visible++;
		if (!/^\s$/u.test(ch)) {
			anyNonSpace = true;
			if (!obfuscated) anyClear = true;
		}
	}
	if (visible > NAME_MAX_VISIBLE) return { ok: false, problem: "too_many_visible" };
	if (!anyNonSpace || !anyClear) return { ok: false, problem: "no_visible_text" };
	return { ok: true, raw };
}

const NUMERIC_HOST = /^(?:0x[0-9a-f]*|[0-9]+)(?:\.(?:0x[0-9a-f]*|[0-9]+))*\.?$/;
const PRIVATE_SUFFIXES = [".localhost", ".local", ".internal", ".lan", ".home.arpa", ".intranet", ".corp", ".private"];

/** Returns the normalized URL or a problem code. Run on the original URL and again on every redirect target. */
export function checkCapeUrl(input: unknown, ownHost: string | null): { ok: true; url: URL } | { ok: false; problem: string } {
	if (typeof input !== "string" || input.trim() === "") return { ok: false, problem: "empty" };
	const text = input.trim();
	if (text.length > URL_MAX_LENGTH) return { ok: false, problem: "too_long" };
	for (let i = 0; i < text.length; i++) {
		const c = text.charCodeAt(i);
		if (c <= 0x20 || c >= 0x7f) return { ok: false, problem: "malformed" };
	}
	// The WHATWG parser would silently "fix" hosts like 0x7f.1 into 127.0.0.1, so check the raw authority first.
	const m = /^([a-zA-Z][a-zA-Z0-9+.-]*):\/\/([^/?#]*)/.exec(text);
	if (!m) return { ok: false, problem: /^https:/i.test(text) ? "malformed" : "not_https" };
	if (m[1].toLowerCase() !== "https") return { ok: false, problem: "not_https" };
	const authority = m[2];
	if (authority.includes("@")) return { ok: false, problem: "has_credentials" };
	let rawHost = authority;
	let port = "";
	if (authority.startsWith("[")) return { ok: false, problem: "ip_address" };
	const colon = authority.lastIndexOf(":");
	if (colon >= 0) {
		rawHost = authority.slice(0, colon);
		port = authority.slice(colon + 1);
	}
	if (port !== "" && port !== "443") return { ok: false, problem: "bad_port" };
	const hostProblem = checkHost(rawHost, ownHost);
	if (hostProblem) return { ok: false, problem: hostProblem };
	let url: URL;
	try {
		url = new URL(text);
	} catch {
		return { ok: false, problem: "malformed" };
	}
	// Belt and braces: the parsed URL must agree with what we checked.
	if (url.protocol !== "https:" || url.username || url.password || (url.port !== "" && url.port !== "443")) {
		return { ok: false, problem: "malformed" };
	}
	const parsedProblem = checkHost(url.hostname, ownHost);
	if (parsedProblem) return { ok: false, problem: parsedProblem };
	return { ok: true, url };
}

export function checkHost(rawHost: string, ownHost: string | null): string | null {
	if (rawHost.startsWith("[")) return "ip_address";
	let host = rawHost.toLowerCase();
	if (host.endsWith(".")) host = host.slice(0, -1);
	if (host === "" || !/^[a-z0-9.-]+$/.test(host) || host.startsWith(".") || host.includes("..")) return "bad_host";
	if (NUMERIC_HOST.test(host)) return "ip_address";
	const last = host.slice(host.lastIndexOf(".") + 1);
	if (last !== "" && /^[0-9]+$/.test(last)) return "ip_address";
	if (!host.includes(".") || host === "localhost") return "private_host";
	if (PRIVATE_SUFFIXES.some((s) => host.endsWith(s))) return "private_host";
	if (ownHost && host === ownHost.toLowerCase()) return "private_host";
	return null;
}

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

export function allowedCapeSize(width: number, height: number): boolean {
	return height * 2 === width && [64, 128, 256, 512].includes(width);
}

/** Header-only PNG check: signature, IHDR first with a valid CRC, sane depth, and an allowed cape size. */
export function checkPng(data: Uint8Array): { ok: true; width: number; height: number } | { ok: false; problem: string } {
	if (data.length > CAPE_MAX_BYTES) return { ok: false, problem: "cape_too_large" };
	if (data.length < 8 + 25) return { ok: false, problem: "cape_not_png" };
	for (let i = 0; i < 8; i++) if (data[i] !== PNG_SIGNATURE[i]) return { ok: false, problem: "cape_not_png" };
	const view = new DataView(data.buffer, data.byteOffset, data.byteLength);
	if (view.getUint32(8) !== 13 || String.fromCharCode(data[12], data[13], data[14], data[15]) !== "IHDR") {
		return { ok: false, problem: "cape_not_png" };
	}
	if (crc32(data.subarray(12, 29)) !== view.getUint32(29)) return { ok: false, problem: "cape_not_png" };
	const width = view.getInt32(16);
	const height = view.getInt32(20);
	const depth = data[24];
	const colorType = data[25];
	const depthOk =
		colorType === 0 ? [1, 2, 4, 8, 16].includes(depth)
		: colorType === 3 ? [1, 2, 4, 8].includes(depth)
		: colorType === 2 || colorType === 4 || colorType === 6 ? depth === 8 || depth === 16
		: false;
	if (!depthOk || data[26] !== 0 || data[27] !== 0 || data[28] > 1) return { ok: false, problem: "cape_not_png" };
	if (!allowedCapeSize(width, height)) return { ok: false, problem: "cape_bad_size" };
	return { ok: true, width, height };
}

let CRC_TABLE: Uint32Array | null = null;

export function crc32(bytes: Uint8Array): number {
	if (!CRC_TABLE) {
		CRC_TABLE = new Uint32Array(256);
		for (let n = 0; n < 256; n++) {
			let c = n;
			for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
			CRC_TABLE[n] = c >>> 0;
		}
	}
	let crc = 0xffffffff;
	for (const b of bytes) crc = CRC_TABLE[(crc ^ b) & 0xff] ^ (crc >>> 8);
	return (crc ^ 0xffffffff) >>> 0;
}

/** 32 lowercase hex digits, or null. Accepts the dashed 36-character form too. */
export function normalizeUuid(value: unknown): string | null {
	if (typeof value !== "string") return null;
	if (value.length === 36) {
		if (!/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(value)) return null;
		return value.replace(/-/g, "").toLowerCase();
	}
	return /^[0-9a-fA-F]{32}$/.test(value) ? value.toLowerCase() : null;
}

export const SHA256_HEX = /^[0-9a-f]{64}$/;
