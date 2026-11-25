package com.golemaddons.autosell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An immutable, precompiled set of sell rules. Plain Java: callers pass the item's raw display name and registry ID.
 *
 * <p>A stack matches only when its normalized name fully matches a valid, enabled rule and, if that rule names an
 * item ID, the IDs are equal. Anything unexpected counts as no match.
 *
 * <p>Rules that are a plain quoted literal ({@code \Q...\E}, case sensitive, no item ID), such as every Dragon
 * Seller rule, are looked up in a hash map instead of run as regexes. A full match of a quoted literal is exactly
 * string equality, so this matches the same names, only faster.
 */
public final class ItemMatcher {
	public record CompiledRule(SellRule source, Pattern pattern, String itemId) {
	}

	public static final ItemMatcher EMPTY = new ItemMatcher(List.of());
	private static final String QUOTE_START = "\\Q";
	private static final String QUOTE_END = "\\E";

	private final List<CompiledRule> rules;
	/** Literal rules keyed by the exact name they match. First rule wins, like the regex loop. */
	private final Map<String, CompiledRule> exact = new HashMap<>();
	/** Every other rule, in config order. */
	private final List<CompiledRule> regexRules = new ArrayList<>();
	/** Rules that blew the regex step budget on a real item; they are skipped from then on. */
	private final Set<CompiledRule> tripped = Collections.newSetFromMap(new IdentityHashMap<>());

	private ItemMatcher(List<CompiledRule> rules) {
		this.rules = List.copyOf(rules);
		for (CompiledRule rule : this.rules) {
			String literal = literalName(rule);
			if (literal != null) {
				exact.putIfAbsent(literal, rule);
			} else {
				regexRules.add(rule);
			}
		}
	}

	/** The exact name a rule matches when it is a plain quoted literal with no flags or item ID, else null. */
	static String literalName(CompiledRule rule) {
		if (!rule.itemId().isEmpty() || rule.pattern().flags() != 0) {
			return null;
		}
		String p = rule.pattern().pattern();
		if (p.length() < 4 || !p.startsWith(QUOTE_START) || !p.endsWith(QUOTE_END)) {
			return null;
		}
		String inner = p.substring(2, p.length() - 2);
		return inner.contains(QUOTE_END) ? null : inner;
	}

	/** Compiles every enabled rule that passes validation. Invalid or disabled rules are left out. */
	public static ItemMatcher compileEnabled(List<SellRule> rules) {
		List<CompiledRule> compiled = new ArrayList<>();
		for (SellRule rule : rules) {
			if (rule.enabled) {
				CompiledRule c = compile(rule);
				if (c != null) compiled.add(c);
			}
		}
		return new ItemMatcher(compiled);
	}

	/** Compiles one rule whether or not it is enabled (used for the edit screen's live preview). */
	public static ItemMatcher compileSingle(SellRule rule) {
		CompiledRule c = compile(rule);
		return c == null ? EMPTY : new ItemMatcher(List.of(c));
	}

	private static CompiledRule compile(SellRule rule) {
		RuleValidator.Result name = RuleValidator.validateNamePattern(rule.namePattern, rule.caseInsensitive);
		RuleValidator.ItemIdResult id = RuleValidator.normalizeItemId(rule.itemId);
		if (!name.valid() || !id.valid()) {
			return null;
		}
		return new CompiledRule(rule, name.pattern(), id.itemId());
	}

	public boolean isEmpty() {
		return rules.isEmpty();
	}

	public int size() {
		return rules.size();
	}

	/**
	 * @param rawName the item's display name as plain text (legacy formatting codes are stripped here)
	 * @param itemId the item's registry ID, e.g. {@code minecraft:obsidian}
	 * @return the first rule that matches, or null
	 */
	public CompiledRule match(String rawName, String itemId) {
		String name = NameNormalizer.normalize(rawName);
		if (name == null || name.isEmpty() || itemId == null) {
			return null;
		}
		CompiledRule literal = exact.get(name);
		if (literal != null) {
			return literal;
		}
		for (CompiledRule rule : regexRules) {
			if (tripped.contains(rule)) continue;
			if (!rule.itemId().isEmpty() && !rule.itemId().equals(itemId)) continue;
			RuleValidator.MatchOutcome outcome = RuleValidator.fullMatch(rule.pattern(), name);
			if (outcome == RuleValidator.MatchOutcome.TOO_COMPLEX) {
				tripped.add(rule);
			} else if (outcome == RuleValidator.MatchOutcome.MATCH) {
				return rule;
			}
		}
		return null;
	}

	public boolean matches(String rawName, String itemId) {
		return match(rawName, itemId) != null;
	}

	/** Rules disabled for this session because a real item name made them exceed the step budget. */
	public List<SellRule> trippedRules() {
		List<SellRule> out = new ArrayList<>();
		for (CompiledRule rule : tripped) out.add(rule.source());
		return out;
	}
}
