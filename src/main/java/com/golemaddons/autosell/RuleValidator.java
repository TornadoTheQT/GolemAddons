package com.golemaddons.autosell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates and compiles user regexes. Plain Java with no Minecraft classes, so it can be unit tested.
 *
 * <p>Every check here fails closed: a pattern that isn't clearly safe is rejected and never used.
 */
public final class RuleValidator {
	public static final int MAX_PATTERN_LENGTH = 128;
	/** Max characters the regex engine may read for a single match before it counts as "too complex". */
	static final int MATCH_BUDGET = 20_000;

	public enum Problem {
		BLANK,
		TOO_LONG,
		SYNTAX,
		EMBEDDED_FLAGS,
		NO_LITERAL_TEXT,
		MATCHES_EMPTY,
		TOO_BROAD,
		TOO_COMPLEX,
		BAD_ITEM_ID
	}

	/** Outcome of one safe match attempt. */
	public enum MatchOutcome { MATCH, NO_MATCH, TOO_COMPLEX }

	/**
	 * @param problem null when valid
	 * @param detail extra context for the user (syntax error text, or the canary name a broad pattern hit)
	 * @param pattern the compiled pattern, only set when valid
	 */
	public record Result(Problem problem, String detail, Pattern pattern) {
		public boolean valid() {
			return problem == null;
		}

		static Result ok(Pattern pattern) {
			return new Result(null, "", pattern);
		}

		static Result fail(Problem problem, String detail) {
			return new Result(problem, detail == null ? "" : detail, null);
		}
	}

	/** @param itemId normalized ID ("" means any item); only meaningful when {@code problem} is null */
	public record ItemIdResult(Problem problem, String itemId) {
		public boolean valid() {
			return problem == null;
		}
	}

	/**
	 * Names a sell rule must never match. Catches catch-all patterns like {@code .*}, {@code \w+},
	 * {@code [A-Za-z ]+} or {@code Enchanted .*}.
	 */
	static final List<String> ITEM_CANARIES;
	/** Titles of vanilla containers a sell-screen pattern must never match. */
	static final List<String> SCREEN_CANARIES = List.of(
			"Chest", "Large Chest", "Ender Chest", "Barrel", "Shulker Box", "Inventory", "Crafting",
			"Crafting Table", "Furnace", "Hopper", "Dispenser", "Dropper", "Minecart with Chest");

	private static final Pattern ITEM_ID_FORMAT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	static {
		List<String> canaries = new ArrayList<>(List.of(
				"Diamond Sword", "Netherite Pickaxe", "Netherite Chestplate", "Elytra", "Enchanted Golden Apple",
				"Enchanted Book", "Totem of Undying", "Shulker Box", "Diamond", "Beacon", "Trident", "Mace",
				"a", "A", "aaaaaaaaaaaaaaaa", "aaaaaaaaaaaaaaaaaaaaaaaaa!", "123", " "));
		// A few pseudo-random "item names" from a fixed seed, so validation results are stable between runs.
		Random random = new Random(0x601E_A5L);
		for (int i = 0; i < 8; i++) {
			StringBuilder name = new StringBuilder();
			int words = 1 + random.nextInt(3);
			for (int w = 0; w < words; w++) {
				if (w > 0) name.append(' ');
				name.append((char) ('A' + random.nextInt(26)));
				int len = 2 + random.nextInt(8);
				for (int c = 0; c < len; c++) {
					name.append((char) ('a' + random.nextInt(26)));
				}
			}
			canaries.add(name.toString());
		}
		ITEM_CANARIES = List.copyOf(canaries);
	}

	private RuleValidator() {
	}

	public static Result validateNamePattern(String pattern, boolean caseInsensitive) {
		return validate(pattern, caseInsensitive ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0, ITEM_CANARIES);
	}

	public static Result validateScreenTitlePattern(String pattern) {
		return validate(pattern, 0, SCREEN_CANARIES);
	}

	/** Normalizes an optional item ID: trims, lowercases, adds {@code minecraft:} when no namespace is given. */
	public static ItemIdResult normalizeItemId(String raw) {
		String id = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
		if (id.isEmpty()) {
			return new ItemIdResult(null, "");
		}
		if (id.indexOf(':') < 0) {
			id = "minecraft:" + id;
		}
		return ITEM_ID_FORMAT.matcher(id).matches() ? new ItemIdResult(null, id) : new ItemIdResult(Problem.BAD_ITEM_ID, id);
	}

	/** Full match with a step budget. Never throws; a runaway pattern reports {@link MatchOutcome#TOO_COMPLEX}. */
	public static MatchOutcome fullMatch(Pattern pattern, String text) {
		try {
			return pattern.matcher(new BoundedCharSequence(text, MATCH_BUDGET)).matches() ? MatchOutcome.MATCH : MatchOutcome.NO_MATCH;
		} catch (BoundedCharSequence.BudgetExceededException | StackOverflowError e) {
			return MatchOutcome.TOO_COMPLEX;
		}
	}

	private static Result validate(String raw, int flags, List<String> canaries) {
		if (raw == null || raw.isBlank()) {
			return Result.fail(Problem.BLANK, null);
		}
		if (raw.length() > MAX_PATTERN_LENGTH) {
			return Result.fail(Problem.TOO_LONG, Integer.toString(MAX_PATTERN_LENGTH));
		}
		Pattern pattern;
		try {
			pattern = Pattern.compile(raw, flags);
		} catch (PatternSyntaxException e) {
			return Result.fail(Problem.SYNTAX, e.getDescription());
		}
		PatternScan scan = PatternScan.of(raw);
		if (scan.hasEmbeddedFlags()) {
			return Result.fail(Problem.EMBEDDED_FLAGS, null);
		}
		if (!scan.hasLiteralLetter()) {
			return Result.fail(Problem.NO_LITERAL_TEXT, null);
		}
		MatchOutcome empty = fullMatch(pattern, "");
		if (empty == MatchOutcome.TOO_COMPLEX) {
			return Result.fail(Problem.TOO_COMPLEX, null);
		}
		if (empty == MatchOutcome.MATCH) {
			return Result.fail(Problem.MATCHES_EMPTY, null);
		}
		for (String canary : canaries) {
			MatchOutcome outcome = fullMatch(pattern, canary);
			if (outcome == MatchOutcome.TOO_COMPLEX) {
				return Result.fail(Problem.TOO_COMPLEX, null);
			}
			if (outcome == MatchOutcome.MATCH) {
				return Result.fail(Problem.TOO_BROAD, canary);
			}
		}
		return Result.ok(pattern);
	}

	/**
	 * Lexical scan of a pattern that already compiled. Tracks escapes, character classes, quantifier braces
	 * and group names so that only literal text outside them counts as a "literal letter".
	 */
	record PatternScan(boolean hasLiteralLetter, boolean hasEmbeddedFlags) {
		private static final String FLAG_CHARS = "idmsuxUc-";

		static PatternScan of(String p) {
			boolean literalLetter = false;
			boolean embeddedFlags = false;
			int classDepth = 0;
			int n = p.length();
			for (int i = 0; i < n; i++) {
				char c = p.charAt(i);
				if (c == '\\') {
					if (i + 1 >= n) break;
					char e = p.charAt(i + 1);
					if (e == 'Q') {
						int end = p.indexOf("\\E", i + 2);
						String quoted = end < 0 ? p.substring(i + 2) : p.substring(i + 2, end);
						if (classDepth == 0 && quoted.chars().anyMatch(Character::isLetter)) literalLetter = true;
						i = end < 0 ? n : end + 1;
					} else if ((e == 'p' || e == 'P' || e == 'x' || e == 'N') && i + 2 < n && p.charAt(i + 2) == '{') {
						int close = p.indexOf('}', i + 2);
						i = close < 0 ? n : close;
					} else if (e == 'k' && i + 2 < n && p.charAt(i + 2) == '<') {
						int close = p.indexOf('>', i + 2);
						i = close < 0 ? n : close;
					} else if (e == 'x') {
						i = Math.min(n, i + 3);
					} else if (e == 'u') {
						i = Math.min(n, i + 5);
					} else if (e == 'p' || e == 'P' || e == 'c') {
						i = Math.min(n, i + 2);
					} else {
						i++;
					}
					continue;
				}
				if (classDepth > 0) {
					if (c == '[') classDepth++;
					else if (c == ']') classDepth--;
					continue;
				}
				if (c == '[') {
					classDepth = 1;
					// A ']' straight after '[' or '[^' is a literal member of the class, not its end.
					if (i + 1 < n && p.charAt(i + 1) == '^') i++;
					if (i + 1 < n && p.charAt(i + 1) == ']') i++;
					continue;
				}
				if (c == '{') {
					int close = p.indexOf('}', i);
					i = close < 0 ? n : close;
					continue;
				}
				if (c == '(' && i + 2 < n && p.charAt(i + 1) == '?') {
					char g = p.charAt(i + 2);
					if (FLAG_CHARS.indexOf(g) >= 0) {
						embeddedFlags = true;
					} else if (g == '<' && i + 3 < n && p.charAt(i + 3) != '=' && p.charAt(i + 3) != '!') {
						int close = p.indexOf('>', i + 3);
						i = close < 0 ? n : close;
					}
					continue;
				}
				if (Character.isLetter(c)) {
					literalLetter = true;
				}
			}
			return new PatternScan(literalLetter, embeddedFlags);
		}
	}
}
