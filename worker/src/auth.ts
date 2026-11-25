// Sign-in without ever seeing the player's Minecraft access token, and without calling Mojang (Mojang refuses
// requests from Cloudflare):
//  1. /v1/auth/challenge hands out a nonce bound to a UUID, signed with TOKEN_SECRET, valid 60 s (stateless: no KV).
//  2. The mod signs "golemaddons-cosmetics:" + nonce with the private half of the player's chat key pair. Mojang
//     issues that key pair to the logged-in account and signs a certificate binding its public key to the UUID.
//  3. /v1/auth/verify checks Mojang's certificate signature (with Mojang's built-in public keys), that it is for the
//     nonce's UUID and unexpired, and the player's signature over the nonce. Then it returns a session token (signed,
//     1 hour) for PUT /v1/profile. Only the account owner's game holds that private key.
import { MOJANG_PLAYER_CERTIFICATE_KEYS } from "./mojangKeys";

export const NONCE_TTL_MS = 60_000;
export const TOKEN_TTL_MS = 60 * 60_000;
export const MIN_SECRET_LENGTH = 32;

const encoder = new TextEncoder();

export function b64url(bytes: Uint8Array): string {
	let s = "";
	for (const b of bytes) s += String.fromCharCode(b);
	return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromB64url(text: string): Uint8Array<ArrayBuffer> | null {
	if (!/^[A-Za-z0-9_-]*$/.test(text)) return null;
	try {
		const s = atob(text.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((text.length + 3) % 4));
		const out = new Uint8Array(s.length);
		for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
		return out;
	} catch {
		return null;
	}
}

async function hmacKey(secret: string): Promise<CryptoKey> {
	return crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"]);
}

async function sign(secret: string, purpose: string, payload: string): Promise<string> {
	const mac = await crypto.subtle.sign("HMAC", await hmacKey(secret), encoder.encode(purpose + "\n" + payload));
	return b64url(new Uint8Array(mac));
}

/** HMAC verify is constant time. */
async function verifySig(secret: string, purpose: string, payload: string, sig: string): Promise<boolean> {
	const mac = fromB64url(sig);
	if (!mac || mac.length !== 32) return false;
	return crypto.subtle.verify("HMAC", await hmacKey(secret), mac, encoder.encode(purpose + "\n" + payload));
}

/** nonce = uuid.expiry.random.signature */
export async function createNonce(secret: string, uuid: string, now: number): Promise<{ nonce: string; expiresAt: number }> {
	const random = b64url(crypto.getRandomValues(new Uint8Array(16)));
	const expiresAt = now + NONCE_TTL_MS;
	const payload = `${uuid}.${expiresAt}.${random}`;
	return { nonce: `${payload}.${await sign(secret, "nonce", payload)}`, expiresAt };
}

/** The UUID the nonce was issued for, if the nonce is authentic and unexpired. */
export async function openNonce(secret: string, nonce: unknown, now: number): Promise<string | null> {
	if (typeof nonce !== "string" || nonce.length > 200) return null;
	const parts = nonce.split(".");
	if (parts.length !== 4) return null;
	const [uuid, exp, random, sig] = parts;
	if (!/^[0-9a-f]{32}$/.test(uuid) || !/^[0-9]{1,15}$/.test(exp) || !/^[A-Za-z0-9_-]{22}$/.test(random)) return null;
	if (!(await verifySig(secret, "nonce", `${uuid}.${exp}.${random}`, sig))) return null;
	return Number(exp) > now ? uuid : null;
}

/** token = uuid.expiry.signature */
export async function createToken(secret: string, uuid: string, now: number): Promise<{ token: string; expiresAt: number }> {
	const expiresAt = now + TOKEN_TTL_MS;
	const payload = `${uuid}.${expiresAt}`;
	return { token: `${payload}.${await sign(secret, "token", payload)}`, expiresAt };
}

export async function openToken(secret: string, header: string | null, now: number): Promise<string | null> {
	if (!header || !header.startsWith("Bearer ") || header.length > 300) return null;
	const parts = header.slice(7).split(".");
	if (parts.length !== 3) return null;
	const [uuid, exp, sig] = parts;
	if (!/^[0-9a-f]{32}$/.test(uuid) || !/^[0-9]{1,15}$/.test(exp)) return null;
	if (!(await verifySig(secret, "token", `${uuid}.${exp}`, sig))) return null;
	return Number(exp) > now ? uuid : null;
}

export const CHALLENGE_PREFIX = "golemaddons-cosmetics:";
const MAX_KEY_B64 = 1024;
const MAX_SIG_B64 = 1400;

export function fromB64(text: unknown, maxLength: number): Uint8Array<ArrayBuffer> | null {
	if (typeof text !== "string" || text.length === 0 || text.length > maxLength || !/^[A-Za-z0-9+/]*={0,2}$/.test(text)) return null;
	try {
		const s = atob(text);
		const out = new Uint8Array(s.length);
		for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
		return out;
	} catch {
		return null;
	}
}

const importedKeys = new Map<readonly string[], Promise<CryptoKey[]>>();

/** Mojang's certificate keys, imported once per isolate. Vanilla verifies these signatures with SHA1withRSA. */
export function mojangCertificateKeys(keys: readonly string[] = MOJANG_PLAYER_CERTIFICATE_KEYS): Promise<CryptoKey[]> {
	let imported = importedKeys.get(keys);
	if (!imported) {
		imported = Promise.all(keys.map((b64) =>
			crypto.subtle.importKey("spki", fromB64(b64, 4096)!, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-1" }, false, ["verify"])));
		importedKeys.set(keys, imported);
	}
	return imported;
}

export interface KeyProof {
	publicKey: unknown;
	keyExpiresAt: unknown;
	keySignature: unknown;
	signature: unknown;
}

/** What vanilla's ProfilePublicKey.Data signs: UUID (16 bytes), expiry millis (8 bytes, big-endian), X.509 key. */
export function certificatePayload(uuidHex: string, expiresAt: number, spki: Uint8Array): Uint8Array<ArrayBuffer> {
	const out = new Uint8Array(24 + spki.length);
	for (let i = 0; i < 16; i++) out[i] = parseInt(uuidHex.slice(i * 2, i * 2 + 2), 16);
	new DataView(out.buffer).setBigInt64(16, BigInt(expiresAt));
	out.set(spki, 24);
	return out;
}

/**
 * Returns null if the proof is good, else why not: "bad_request" (malformed), "key_expired", "bad_certificate"
 * (Mojang didn't sign this key for this UUID) or "bad_signature" (the nonce wasn't signed with that key).
 */
export async function checkKeyProof(uuid: string, nonce: string, proof: KeyProof, now: number,
	certificateKeys: readonly string[] = MOJANG_PLAYER_CERTIFICATE_KEYS): Promise<string | null> {
	const spki = fromB64(proof.publicKey, MAX_KEY_B64);
	const keySignature = fromB64(proof.keySignature, MAX_SIG_B64);
	const signature = fromB64(proof.signature, MAX_SIG_B64);
	const expiresAt = proof.keyExpiresAt;
	if (!spki || !keySignature || !signature || typeof expiresAt !== "number" || !Number.isSafeInteger(expiresAt)) return "bad_request";
	if (expiresAt <= now) return "key_expired";

	const payload = certificatePayload(uuid, expiresAt, spki);
	let certified = false;
	for (const key of await mojangCertificateKeys(certificateKeys)) {
		if (await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, keySignature, payload)) {
			certified = true;
			break;
		}
	}
	if (!certified) return "bad_certificate";

	let playerKey: CryptoKey;
	try {
		playerKey = await crypto.subtle.importKey("spki", spki, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
	} catch {
		return "bad_request";
	}
	const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", playerKey, signature, encoder.encode(CHALLENGE_PREFIX + nonce));
	return ok ? null : "bad_signature";
}

/** Constant-time comparison for the admin secret (hash both sides so lengths match). */
export async function secretEquals(a: string, b: string): Promise<boolean> {
	const [x, y] = await Promise.all([
		crypto.subtle.digest("SHA-256", encoder.encode(a)),
		crypto.subtle.digest("SHA-256", encoder.encode(b)),
	]);
	const ua = new Uint8Array(x);
	const ub = new Uint8Array(y);
	let diff = 0;
	for (let i = 0; i < ua.length; i++) diff |= ua[i] ^ ub[i];
	return diff === 0;
}
