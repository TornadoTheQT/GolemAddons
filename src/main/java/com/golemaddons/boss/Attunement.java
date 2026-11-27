package com.golemaddons.boss;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The Riftborn Seraph's marks and daggers. Fleshrend Dagger attunes to FLESH or ICHOR, Voidfang Dagger to VOID or
 * AETHER; right-clicking a dagger switches it to its other attunement (0.3 s cooldown). Pure text logic, tested
 * on its own.
 */
public enum Attunement {
	FLESH, ICHOR, VOID, AETHER;

	private final Pattern word = Pattern.compile("\\b" + name() + "\\b", Pattern.CASE_INSENSITIVE);

	public enum Dagger {
		FLESHREND("fleshrend", FLESH, ICHOR), VOIDFANG("voidfang", VOID, AETHER);

		public final String nameFragment;
		public final Attunement first;
		public final Attunement second;

		Dagger(String nameFragment, Attunement first, Attunement second) {
			this.nameFragment = nameFragment;
			this.first = first;
			this.second = second;
		}

		public boolean isIn(String itemName) {
			return itemName != null && itemName.toLowerCase(Locale.ROOT).contains(nameFragment);
		}

		public Attunement other(Attunement a) {
			return a == first ? second : first;
		}
	}

	private static final Pattern HINT = Pattern.compile("(Fleshrend|Voidfang) Dagger\\s*[\u00b7\u2022-]\\s*Right-click\\s*(?:\u2192|->)\\s*\\W*\\s*(FLESH|ICHOR|VOID|AETHER)",
			Pattern.CASE_INSENSITIVE);

	/**
	 * The attunement the action bar says to switch to: "Fleshrend Dagger · Right-click → ♥ FLESH | 39s" means the
	 * Fleshrend Dagger should be in FLESH. Null for any other message.
	 */
	public static Attunement fromHint(String actionBar) {
		if (actionBar == null) return null;
		java.util.regex.Matcher m = HINT.matcher(actionBar);
		if (!m.find()) return null;
		Attunement a = valueOf(m.group(2).toUpperCase(Locale.ROOT));
		return a.dagger().nameFragment.equalsIgnoreCase(m.group(1)) ? a : null;
	}

	public Dagger dagger() {
		return this == FLESH || this == ICHOR ? Dagger.FLESHREND : Dagger.VOIDFANG;
	}

	boolean in(String text) {
		return text != null && word.matcher(text).find();
	}

	/**
	 * The mark in a mob's name tag. Dragsim sends the Riftborn halves' tags with their text reversed
	 * ("[Lv8000] reggaD gnafdioV • DIOV ☯ 960M/960M" is "Voidfang Dagger • VOID"), so the text is read both ways.
	 * Marks are written in capitals, so only capital words count: a lower-case "void" in a name is not a mark.
	 */
	public static Attunement markIn(String text) {
		if (text == null) return null;
		Attunement forward = markInForward(text);
		return forward != null ? forward : markInForward(new StringBuilder(text).reverse().toString());
	}

	private static Attunement markInForward(String text) {
		Attunement found = null;
		for (Attunement a : values()) {
			if (Pattern.compile("\\b" + a.name() + "\\b").matcher(text).find()) {
				if (found != null) return null; // two marks in one tag: can't tell
				found = a;
			}
		}
		return found;
	}

	/**
	 * Which of its two attunements a dagger is in, from its name and lore: a line about the attunement ("Attuned:
	 * ICHOR") that names just one of them, else the item name if it names just one. Null when it can't be told.
	 */
	public static Attunement current(Dagger dagger, String name, List<String> lore) {
		for (String line : lore) {
			String l = line.toLowerCase(Locale.ROOT);
			if (!l.contains("attun") && !l.contains("mode") && !l.contains("current")) continue;
			Attunement a = only(dagger, line);
			if (a != null) return a;
		}
		Attunement fromName = only(dagger, name);
		if (fromName != null) return fromName;
		return null;
	}

	private static Attunement only(Dagger d, String text) {
		boolean a = d.first.in(text);
		boolean b = d.second.in(text);
		return a == b ? null : a ? d.first : d.second;
	}
}
