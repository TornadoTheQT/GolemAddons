package com.golemaddons.autosell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuleValidatorTest {
	private static RuleValidator.Problem problem(String pattern) {
		return RuleValidator.validateNamePattern(pattern, false).problem();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Enchanted Obsidian",
			"Enchanted End ?Stone",
			"Enchanted (Crying )?Obsidian",
			"Enchanted (End Stone|Obsidian|Crying Obsidian)",
			"\\QEnchanted Obsidian\\E"
	})
	void intendedPatternsAreValid(String pattern) {
		RuleValidator.Result result = RuleValidator.validateNamePattern(pattern, false);
		assertTrue(result.valid(), () -> pattern + " rejected: " + result.problem() + " " + result.detail());
	}

	@Test
	void blankAndTooLongAreRejected() {
		assertEquals(RuleValidator.Problem.BLANK, problem(""));
		assertEquals(RuleValidator.Problem.BLANK, problem("   "));
		assertEquals(RuleValidator.Problem.BLANK, problem(null));
		assertEquals(RuleValidator.Problem.TOO_LONG, problem("a".repeat(RuleValidator.MAX_PATTERN_LENGTH + 1)));
	}

	@Test
	void syntaxErrorsAreRejectedWithDetail() {
		RuleValidator.Result result = RuleValidator.validateNamePattern("Enchanted (Obsidian", false);
		assertEquals(RuleValidator.Problem.SYNTAX, result.problem());
		assertFalse(result.detail().isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {".*", ".+", "\\w+", "[A-Za-z ]+", "\\S.*", "[^x]+", "\\p{L}+", "(.)+", "\\D+"})
	void wildcardOnlyPatternsAreRejected(String pattern) {
		RuleValidator.Problem p = problem(pattern);
		assertTrue(p == RuleValidator.Problem.NO_LITERAL_TEXT || p == RuleValidator.Problem.MATCHES_EMPTY
				|| p == RuleValidator.Problem.TOO_BROAD, () -> pattern + " gave " + p);
	}

	@Test
	void patternsWithoutLiteralLettersAreRejected() {
		assertEquals(RuleValidator.Problem.NO_LITERAL_TEXT, problem("[A-Z][a-z]+ [A-Z][a-z]+"));
		assertEquals(RuleValidator.Problem.NO_LITERAL_TEXT, problem("\\x{41}\\x{62}+"));
	}

	@Test
	void patternMatchingEmptyIsRejected() {
		assertEquals(RuleValidator.Problem.MATCHES_EMPTY, problem("(Enchanted Obsidian)?"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"Enchanted .*", ".*Sword", "Diamond.*", "(Enchanted Obsidian|.+)", ".*a.*", "E?l?ytra"})
	void broadPatternsAreRejectedWithTheCanaryTheyHit(String pattern) {
		RuleValidator.Result result = RuleValidator.validateNamePattern(pattern, false);
		assertEquals(RuleValidator.Problem.TOO_BROAD, result.problem(), pattern);
		assertFalse(result.detail().isEmpty());
	}

	@Test
	void caseInsensitiveRulesAreCheckedAgainstCanariesCaseInsensitively() {
		assertTrue(RuleValidator.validateNamePattern("enchanted obsidian", true).valid());
		assertEquals(RuleValidator.Problem.TOO_BROAD, RuleValidator.validateNamePattern("diamond sword", true).problem());
	}

	@ParameterizedTest
	@ValueSource(strings = {"(?i)Enchanted Obsidian", "(?s)Enchanted.Obsidian", "Enchanted (?i:obsidian)", "(?-i)Enchanted Obsidian", "(?u)Enchanted Obsidian"})
	void embeddedFlagsAreRejected(String pattern) {
		assertEquals(RuleValidator.Problem.EMBEDDED_FLAGS, problem(pattern));
	}

	@Test
	void groupsThatAreNotFlagsAreAllowed() {
		assertTrue(RuleValidator.validateNamePattern("Enchanted (?:End Stone|Obsidian)", false).valid());
		assertTrue(RuleValidator.validateNamePattern("Enchanted (?<kind>Obsidian)", false).valid());
		assertTrue(RuleValidator.validateNamePattern("Enchanted Obsidian(?! Pickaxe)", false).valid());
		// "(?i)" inside a character class is literal text, not a flag.
		assertTrue(RuleValidator.validateNamePattern("Enchanted [(?i)]Obsidian", false).valid());
	}

	@ParameterizedTest
	@ValueSource(strings = {"(a+)+$", "((a+)+)+b", "(.*a){12}", "(.*a){20}Obsidian", "((\\w+)+)+z"})
	void catastrophicPatternsAreRejectedQuickly(String pattern) {
		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			RuleValidator.Result result = RuleValidator.validateNamePattern(pattern, false);
			assertFalse(result.valid(), pattern + " should be rejected");
		});
	}

	@Test
	void fullMatchReportsBudgetExhaustion() {
		java.util.regex.Pattern evil = java.util.regex.Pattern.compile("((a+)+)+b");
		assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
				assertEquals(RuleValidator.MatchOutcome.TOO_COMPLEX, RuleValidator.fullMatch(evil, "a".repeat(40))));
	}

	@Test
	void itemIdsAreNormalized() {
		assertEquals("", RuleValidator.normalizeItemId("  ").itemId());
		assertEquals("minecraft:obsidian", RuleValidator.normalizeItemId("obsidian").itemId());
		assertEquals("minecraft:crying_obsidian", RuleValidator.normalizeItemId(" Minecraft:Crying_Obsidian ").itemId());
		assertEquals(RuleValidator.Problem.BAD_ITEM_ID, RuleValidator.normalizeItemId("minecraft:end stone").problem());
		assertEquals(RuleValidator.Problem.BAD_ITEM_ID, RuleValidator.normalizeItemId("::").problem());
	}

	@Test
	void screenTitlePatterns() {
		assertTrue(RuleValidator.validateScreenTitlePattern("Sell Items").valid());
		assertTrue(RuleValidator.validateScreenTitlePattern("Merchant - Sell( Items)?").valid());
		assertEquals(RuleValidator.Problem.BLANK, RuleValidator.validateScreenTitlePattern("").problem());
		assertEquals(RuleValidator.Problem.TOO_BROAD, RuleValidator.validateScreenTitlePattern("(Large )?Chest").problem());
		assertEquals(RuleValidator.Problem.TOO_BROAD, RuleValidator.validateScreenTitlePattern("Ender Chest").problem());
		assertFalse(RuleValidator.validateScreenTitlePattern(".*").valid());
	}
}
