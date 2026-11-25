import { describe, expect, it } from "vitest";
import { checkCapeUrl, checkPng, normalizeUuid, parseName } from "../src/validate";
import { png } from "./helpers";

describe("parseName (mirrors CosmeticName.java)", () => {
	it("accepts codes and literal ampersands", () => {
		expect(parseName("&6&lZach &r&bX")).toEqual({ ok: true, raw: "&6&lZach &r&bX" });
		expect(parseName("  A&&B ")).toEqual({ ok: true, raw: "A&&B" });
		expect(parseName("&kxx&rZach").ok).toBe(true);
		expect(parseName("\u{1F525}".repeat(24)).ok).toBe(true);
	});
	it.each(["&zName", "Name&", "&", "&&&"])("rejects bad code %s", (n) => {
		expect(parseName(n)).toEqual({ ok: false, problem: "bad_code" });
	});
	it.each(["Na§cme", "Na‮me", "Na​me", "Na\nme", "Na\tme", "Na me", "Na\ud800me", "Name"])(
		"rejects bad character %#", (n) => {
			expect(parseName(n)).toEqual({ ok: false, problem: "bad_character" });
		});
	it("enforces lengths and visible text", () => {
		expect(parseName("a".repeat(24)).ok).toBe(true);
		expect(parseName("a".repeat(25))).toEqual({ ok: false, problem: "too_many_visible" });
		expect(parseName("&a".repeat(32) + "b")).toEqual({ ok: false, problem: "too_long" });
		expect(parseName("\u{1F525}".repeat(25))).toEqual({ ok: false, problem: "too_many_visible" });
		for (const n of ["", "   ", "&a", "&a   &b ", "&kSecret", 42, null]) {
			expect(parseName(n).ok).toBe(false);
		}
	});
});

describe("checkCapeUrl (mirrors CapeUrlRules.java)", () => {
	const own = "api.example.workers.dev";
	it.each(["https://i.imgur.com/abc.png", "https://example.com:443/cape.png", "https://cdn.example.co.uk/a/b.png?x=1"])(
		"accepts %s", (u) => {
			expect(checkCapeUrl(u, own).ok).toBe(true);
		});
	it.each([
		["http://example.com/c.png", "not_https"],
		["file:///etc/passwd", "not_https"],
		["javascript:alert(1)", "not_https"],
		["https://example.com:8443/c.png", "bad_port"],
		["https://user:pw@example.com/c.png", "has_credentials"],
		["https://127.0.0.1/c.png", "ip_address"],
		["https://2130706433/c.png", "ip_address"],
		["https://0x7f.1/c.png", "ip_address"],
		["https://0177.0.0.1/c.png", "ip_address"],
		["https://[::1]/c.png", "ip_address"],
		["https://localhost/c.png", "private_host"],
		["https://foo.localhost/c.png", "private_host"],
		["https://metadata.internal/c.png", "private_host"],
		["https://intranet/c.png", "private_host"],
		["https://api.example.workers.dev/x.png", "private_host"],
		["https://exa mple.com/c.png", "malformed"],
		["https://exämple.com/c.png", "malformed"],
		["https://example.com/" + "a".repeat(600), "too_long"],
	])("rejects %s", (u, problem) => {
		expect(checkCapeUrl(u, own)).toEqual({ ok: false, problem });
	});
});

describe("checkPng (mirrors CapePng.java)", () => {
	it("accepts 2:1 sizes 64..512", () => {
		for (const w of [64, 128, 256, 512]) expect(checkPng(png(w, w / 2)).ok).toBe(true);
	});
	it("rejects other sizes, bombs, junk and corruption", () => {
		expect(checkPng(png(64, 64))).toEqual({ ok: false, problem: "cape_bad_size" });
		expect(checkPng(png(100000, 50000))).toEqual({ ok: false, problem: "cape_bad_size" });
		expect(checkPng(new TextEncoder().encode("GIF89a" + ".".repeat(40)))).toEqual({ ok: false, problem: "cape_not_png" });
		const bad = png(64, 32);
		bad[20] ^= 1;
		expect(checkPng(bad)).toEqual({ ok: false, problem: "cape_not_png" });
		expect(checkPng(png(64, 32, 256 * 1024))).toEqual({ ok: false, problem: "cape_too_large" });
	});
});

describe("ids and digests", () => {
	it("normalizes UUIDs", () => {
		expect(normalizeUuid("069A79F4-44E9-4726-A5BE-FCA90E38AAF5")).toBe("069a79f444e94726a5befca90e38aaf5");
		expect(normalizeUuid("069a79f444e94726a5befca90e38aaf")).toBeNull();
		expect(normalizeUuid("069a79f4444e9-4726-a5be-fca90e38aaf5")).toBeNull();
		expect(normalizeUuid(7)).toBeNull();
	});
});
