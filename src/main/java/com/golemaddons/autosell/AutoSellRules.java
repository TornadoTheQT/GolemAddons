package com.golemaddons.autosell;

import com.golemaddons.config.GolemConfig;
import com.golemaddons.dragon.DragonCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Immutable, compiled snapshot of everything the sell button acts on: Mining Auto-Sell rules plus Dragon Seller
 * picks, and the shared selling settings. A sell run keeps the snapshot it started with, so editing the config
 * mid-run can't change what that run sells.
 */
public record AutoSellRules(ItemMatcher matcher, RuleValidator.Result titleResult, int clickDelayTicks, boolean protectHotbar) {
	public static final AutoSellRules DISABLED = new AutoSellRules(
			ItemMatcher.EMPTY, RuleValidator.validateScreenTitlePattern(""), GolemConfig.Selling.MIN_DELAY_TICKS, true);

	public static AutoSellRules from(GolemConfig config) {
		List<SellRule> all = new ArrayList<>(config.miningAutoSell.rules);
		all.addAll(DragonCatalog.rulesFor(config.dragonSeller));
		return new AutoSellRules(
				ItemMatcher.compileEnabled(all),
				RuleValidator.validateScreenTitlePattern(config.selling.sellScreenTitlePattern),
				config.selling.clickDelayTicks,
				config.selling.protectHotbar);
	}

	/** True when the plain-text screen title fully matches the configured, valid sell-screen pattern. */
	public boolean isSellScreenTitle(String rawTitle) {
		Pattern title = titleResult.pattern();
		if (title == null) {
			return false;
		}
		String name = NameNormalizer.normalize(rawTitle);
		return name != null && RuleValidator.fullMatch(title, name) == RuleValidator.MatchOutcome.MATCH;
	}
}
