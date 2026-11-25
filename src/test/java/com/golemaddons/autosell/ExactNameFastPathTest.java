package com.golemaddons.autosell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.golemaddons.config.GolemConfig;
import com.golemaddons.dragon.DragonCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The hash lookup for quoted-literal rules must match exactly what the regex path matched, nothing more. */
class ExactNameFastPathTest {
	private static final String ID = "minecraft:player_head";

	private static GolemConfig.DragonSeller everything() {
		GolemConfig.DragonSeller all = new GolemConfig.DragonSeller();
		for (DragonCatalog.DragonType d : DragonCatalog.DragonType.values()) all.setDragon(d, true);
		return all;
	}

	/** Names to try: every catalog name plus near misses a server could plausibly produce. */
	private static List<String> probes() {
		List<String> out = new ArrayList<>();
		for (String name : DragonCatalog.itemNames(everything())) {
			out.add(name);
			out.add(name.toLowerCase());
			out.add(name.toUpperCase());
			out.add(name + " ✪");
			out.add("Strong " + name);
			out.add(" " + name + " ");
			out.add("§6" + name);
			out.add("§6" + name + "§r");
			out.add(name + "s");
			out.add(name.substring(0, name.length() - 1));
			out.add(name.replace(" ", "  "));
		}
		out.addAll(List.of("", "Dragon", "Diamond Sword", "Enchanted Obsidian", "Young Dragon", "Dragon Helmet"));
		return out;
	}

	@Test
	void sameResultsAsPlainRegexForEveryCatalogRule() {
		List<SellRule> rules = DragonCatalog.rulesFor(everything());
		ItemMatcher fast = ItemMatcher.compileEnabled(rules);
		List<Pattern> reference = rules.stream().map(r -> Pattern.compile(r.namePattern)).toList();
		for (String raw : probes()) {
			String normalized = NameNormalizer.normalize(raw);
			boolean expected = normalized != null && !normalized.isEmpty()
					&& reference.stream().anyMatch(p -> p.matcher(normalized).matches());
			assertEquals(expected, fast.matches(raw, ID), raw);
		}
	}

	@Test
	void literalDetectionOnlyAcceptsPlainCaseSensitiveQuotesWithoutItemId() {
		assertEquals("Young Dragon Helmet", literal(new SellRule(true, "", Pattern.quote("Young Dragon Helmet"), "", false)));
		assertNull(literal(new SellRule(true, "", Pattern.quote("Young Dragon Helmet"), "", true)), "case-insensitive");
		assertNull(literal(new SellRule(true, "", Pattern.quote("Young Dragon Helmet"), ID, false)), "has item id");
		assertNull(literal(new SellRule(true, "", "Young Dragon Helmet", "", false)), "unquoted regex");
		assertNull(literal(new SellRule(true, "", "\\QYoung\\E Dragon \\QHelmet\\E", "", false)), "several quotes");
	}

	@Test
	void caseInsensitiveQuotedRuleStillGoesThroughRegex() {
		ItemMatcher m = ItemMatcher.compileEnabled(List.of(
				new SellRule(true, "", Pattern.quote("Young Dragon Helmet"), "", true)));
		assertTrue(m.matches("young dragon helmet", ID));
	}

	@Test
	void itemIdStillRestrictsQuotedRule() {
		ItemMatcher m = ItemMatcher.compileEnabled(List.of(
				new SellRule(true, "", Pattern.quote("Young Dragon Helmet"), "minecraft:leather_helmet", false)));
		assertTrue(m.matches("Young Dragon Helmet", "minecraft:leather_helmet"));
		assertFalse(m.matches("Young Dragon Helmet", ID));
	}

	@Test
	void mixedRegexAndLiteralRulesBothWork() {
		List<SellRule> rules = new ArrayList<>(DragonCatalog.rulesFor(new GolemConfig.DragonSeller()));
		rules.add(new SellRule(true, "", "Enchanted End ?[Ss]tone", "", false));
		ItemMatcher m = ItemMatcher.compileEnabled(rules);
		assertTrue(m.matches("Enchanted Endstone", ID));
		assertTrue(m.matches("Young Dragon Boots", ID));
		assertFalse(m.matches("Golden Dragon Boots", ID), "Golden is kept by default");
	}

	private static String literal(SellRule rule) {
		ItemMatcher m = ItemMatcher.compileEnabled(List.of(rule));
		assertEquals(1, m.size(), "rule should be valid: " + rule.namePattern);
		RuleValidator.Result r = RuleValidator.validateNamePattern(rule.namePattern, rule.caseInsensitive);
		return ItemMatcher.literalName(new ItemMatcher.CompiledRule(rule, r.pattern(), RuleValidator.normalizeItemId(rule.itemId).itemId()));
	}
}
