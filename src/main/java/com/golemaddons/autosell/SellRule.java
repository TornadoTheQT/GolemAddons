package com.golemaddons.autosell;

/**
 * One user-defined sell rule, as stored in the config file.
 * Mutable on purpose so Gson and the edit screen can fill it in; validate with {@link RuleValidator} before use.
 */
public final class SellRule {
	public boolean enabled = false;
	public String label = "";
	public String namePattern = "";
	/** Optional exact item registry ID (e.g. {@code minecraft:obsidian}); blank means any item. */
	public String itemId = "";
	public boolean caseInsensitive = false;

	public SellRule() {
	}

	public SellRule(boolean enabled, String label, String namePattern, String itemId, boolean caseInsensitive) {
		this.enabled = enabled;
		this.label = label;
		this.namePattern = namePattern;
		this.itemId = itemId;
		this.caseInsensitive = caseInsensitive;
	}

	public SellRule copy() {
		return new SellRule(enabled, label, namePattern, itemId, caseInsensitive);
	}

	/** Replaces nulls that a hand-edited config file may contain. */
	public void sanitize() {
		if (label == null) label = "";
		if (namePattern == null) namePattern = "";
		if (itemId == null) itemId = "";
	}

	public String displayName() {
		return label.isBlank() ? namePattern : label;
	}
}
