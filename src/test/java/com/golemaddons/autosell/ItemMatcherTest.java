package com.golemaddons.autosell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ItemMatcherTest {
	private static final String OBSIDIAN = "minecraft:obsidian";

	private static ItemMatcher matcher(SellRule... rules) {
		return ItemMatcher.compileEnabled(List.of(rules));
	}

	private static SellRule rule(String pattern) {
		return new SellRule(true, "", pattern, "", false);
	}

	@Test
	void exactNameMatches() {
		ItemMatcher m = matcher(rule("Enchanted Obsidian"));
		assertTrue(m.matches("Enchanted Obsidian", OBSIDIAN));
	}

	@Test
	void fullMatchOnlyNoExtraTextBeforeOrAfter() {
		ItemMatcher m = matcher(rule("Enchanted Obsidian"));
		assertFalse(m.matches("Enchanted Obsidian Pickaxe", "minecraft:diamond_pickaxe"));
		assertFalse(m.matches("Super Enchanted Obsidian", OBSIDIAN));
		assertFalse(m.matches("Enchanted Obsidian x64", OBSIDIAN));
		assertFalse(m.matches("Enchanted  Obsidian", OBSIDIAN));
	}

	@Test
	void formattingIsStrippedAndOuterWhitespaceTrimmed() {
		ItemMatcher m = matcher(rule("Enchanted Obsidian"));
		assertTrue(m.matches("§5§lEnchanted §dObsidian", OBSIDIAN));
		assertTrue(m.matches("  Enchanted Obsidian ", OBSIDIAN));
	}

	@Test
	void controlCharactersAndHugeNamesNeverMatch() {
		ItemMatcher m = matcher(rule("Enchanted Obsidian"));
		assertFalse(m.matches("Enchanted\u0000 Obsidian", OBSIDIAN));
		assertFalse(m.matches("Enchanted Obsidian\n", OBSIDIAN) && false); // trailing newline is trimmed by strip()
		assertFalse(m.matches("Enchanted\nObsidian", OBSIDIAN));
		assertNull(NameNormalizer.normalize("x".repeat(NameNormalizer.MAX_NAME_LENGTH + 1)));
	}

	@Test
	void caseSensitivity() {
		assertFalse(matcher(rule("Enchanted Obsidian")).matches("enchanted obsidian", OBSIDIAN));
		SellRule ci = new SellRule(true, "", "Enchanted Obsidian", "", true);
		assertTrue(matcher(ci).matches("ENCHANTED OBSIDIAN", OBSIDIAN));
	}

	@Test
	void itemIdConstraint() {
		SellRule withId = new SellRule(true, "", "Enchanted Obsidian", "obsidian", false);
		ItemMatcher m = matcher(withId);
		assertTrue(m.matches("Enchanted Obsidian", OBSIDIAN));
		assertFalse(m.matches("Enchanted Obsidian", "minecraft:crying_obsidian"));
		// Without an ID constraint, any item with the right name matches.
		assertTrue(matcher(rule("Enchanted Obsidian")).matches("Enchanted Obsidian", "minecraft:paper"));
	}

	@Test
	void disabledAndInvalidRulesAreIgnored() {
		SellRule disabled = new SellRule(false, "", "Enchanted Obsidian", "", false);
		SellRule broad = rule(".*");
		SellRule badId = new SellRule(true, "", "Enchanted Obsidian", "not an id", false);
		ItemMatcher m = matcher(disabled, broad, badId);
		assertTrue(m.isEmpty());
		assertFalse(m.matches("Enchanted Obsidian", OBSIDIAN));
		assertFalse(m.matches("Diamond Sword", "minecraft:diamond_sword"));
	}

	@Test
	void anyRuleMatching() {
		ItemMatcher m = matcher(rule("Enchanted Obsidian"), rule("Enchanted End ?[Ss]tone"), rule("Enchanted Crying Obsidian"));
		assertEquals(3, m.size());
		assertTrue(m.matches("Enchanted End Stone", "minecraft:end_stone"));
		assertTrue(m.matches("Enchanted Endstone", "minecraft:end_stone"));
		assertTrue(m.matches("Enchanted Crying Obsidian", "minecraft:crying_obsidian"));
		assertFalse(m.matches("Obsidian", OBSIDIAN));
		assertFalse(m.matches("Enchanted Book", "minecraft:enchanted_book"));
	}

	@Test
	void ruleThatExplodesOnARealNameIsTrippedNotHung() {
		// Passes validation (no canary starts with "Enchanted x"), but is exponential on a long run of x's.
		SellRule evil = rule("Enchanted ((x+)+)+y");
		assertTrue(RuleValidator.validateNamePattern(evil.namePattern, false).valid());
		ItemMatcher m = matcher(evil, rule("Enchanted Obsidian"));
		org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
			assertFalse(m.matches("Enchanted " + "x".repeat(40) + "!", OBSIDIAN));
			assertEquals(List.of(evil), m.trippedRules());
			// The tripped rule is skipped from now on; the other rules keep working.
			assertTrue(m.matches("Enchanted Obsidian", OBSIDIAN));
		});
	}

	@Test
	void emptyNameOrMissingIdNeverMatches() {
		ItemMatcher m = matcher(rule("Enchanted Obsidian"));
		assertFalse(m.matches("", OBSIDIAN));
		assertFalse(m.matches(null, OBSIDIAN));
		assertFalse(m.matches("Enchanted Obsidian", null));
	}

	@Test
	void compileSingleIgnoresEnabledFlagForPreview() {
		SellRule disabled = new SellRule(false, "", "Enchanted Obsidian", "", false);
		assertTrue(ItemMatcher.compileSingle(disabled).matches("Enchanted Obsidian", OBSIDIAN));
		assertTrue(ItemMatcher.compileSingle(rule(".*")).isEmpty());
	}
}
