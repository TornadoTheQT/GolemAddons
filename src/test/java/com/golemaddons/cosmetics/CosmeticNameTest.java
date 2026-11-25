package com.golemaddons.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CosmeticNameTest {
	@Test
	void parsesColorsAndFormats() {
		CosmeticName.Result r = CosmeticName.parse("&6&lZach &r&bX");
		assertTrue(r.valid());
		assertEquals("Zach X", r.plain());
		assertEquals(2, r.runs().size());
		CosmeticName.Run first = r.runs().get(0);
		assertEquals("Zach ", first.text());
		assertEquals(6, first.color());
		assertTrue(first.bold());
		CosmeticName.Run second = r.runs().get(1);
		assertEquals(11, second.color());
		assertFalse(second.bold(), "&r then a color clears bold");
	}

	@Test
	void colorCodeClearsFormatsLikeVanilla() {
		CosmeticName.Run run = CosmeticName.parse("&l&aHi").runs().get(0);
		assertEquals(10, run.color());
		assertFalse(run.bold());
	}

	@Test
	void doubleAmpersandIsLiteral() {
		CosmeticName.Result r = CosmeticName.parse("A&&B");
		assertTrue(r.valid());
		assertEquals("A&B", r.plain());
	}

	@Test
	void uppercaseCodesWork() {
		assertTrue(CosmeticName.parse("&6&LBold").valid());
	}

	@Test
	void trimsAndKeepsRaw() {
		assertEquals("&aHi", CosmeticName.parse("  &aHi  ").raw());
	}

	@ParameterizedTest
	@ValueSource(strings = {"&zName", "Name&", "&", "&&&"})
	void badCodes(String raw) {
		assertEquals(CosmeticName.Problem.BAD_CODE, CosmeticName.parse(raw).problem(), raw);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Na§cme", // section sign
			"Na‮me", // right-to-left override
			"Na​me", // zero-width space
			"Na\nme",
			"Na\tme",
			"Na me", // line separator
			"Na\uD800me", // lone surrogate
			"Name", // private use
	})
	void badCharacters(String raw) {
		assertEquals(CosmeticName.Problem.BAD_CHARACTER, CosmeticName.parse(raw).problem(), raw);
	}

	@Test
	void lengthLimits() {
		assertTrue(CosmeticName.parse("a".repeat(24)).valid());
		assertEquals(CosmeticName.Problem.TOO_MANY_VISIBLE, CosmeticName.parse("a".repeat(25)).problem());
		assertEquals(CosmeticName.Problem.TOO_LONG, CosmeticName.parse("&a".repeat(32) + "b").problem());
		assertTrue(CosmeticName.parse("&a".repeat(20) + "abc").valid());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   ", "&a", "&a   &b ", "&kSecret"})
	void needsVisibleClearText(String raw) {
		CosmeticName.Problem p = CosmeticName.parse(raw).problem();
		assertTrue(p == CosmeticName.Problem.EMPTY || p == CosmeticName.Problem.NO_VISIBLE_TEXT, raw + " -> " + p);
	}

	@Test
	void obfuscatedAllowedWithSomeClearText() {
		assertTrue(CosmeticName.parse("&kxx&rZach").valid());
	}

	@Test
	void emojiCountsAsOneCodePoint() {
		String fire = new String(Character.toChars(0x1F525));
		assertTrue(CosmeticName.parse(fire.repeat(24)).valid());
		assertEquals(CosmeticName.Problem.TOO_MANY_VISIBLE, CosmeticName.parse(fire.repeat(25)).problem());
	}

	@ParameterizedTest
	@ValueSource(strings = {"192.168.1.20", "ip 10 . 0 . 0 . 1", "fe80::1:2:3", "me@mail.com", "call 555 123 4567",
			"5551234567", "go to x.com", "www.site", "discord.gg/abc", "https://evil", "&6visit &lmy.net"})
	void personalInfoAndLinksAreRejected(String raw) {
		assertEquals(CosmeticName.Problem.CONTACT_INFO, CosmeticName.parse(raw).problem(), raw);
	}

	@ParameterizedTest
	@ValueSource(strings = {"Zach", "Mr.Bean", "Team 42", "v1.2", "Joe.Mendez", "x_X 2024 X_x", "A&&B"})
	void normalNamesAreFine(String raw) {
		assertTrue(CosmeticName.parse(raw).valid(), raw);
	}
}
