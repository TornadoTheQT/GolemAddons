package com.golemaddons.autosell;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pets and their rarity, read the way the game shows them: a pet's name is "[Lvl N] Name", and the colour of the
 * name gives its rarity. Pure: works on plain text and an RGB colour.
 */
public enum PetRarity {
	COMMON(0xFFFFFF),
	UNCOMMON(0x55FF55),
	RARE(0x5555FF),
	EPIC(0xAA00AA),
	LEGENDARY(0xFFAA00),
	MYTHIC(0xFF55FF);

	/** The colour of a pet's name at this rarity (RGB). */
	public final int color;

	/** "[Lvl 100] Ender Dragon": the level and the name after it. */
	private static final Pattern PET = Pattern.compile("^\\[Lvl (\\d{1,4})\\] (\\S.*)$");
	/** A name colour further than this from every rarity colour isn't trusted (fail closed: not sold). */
	private static final int MAX_DISTANCE_SQ = 60 * 60;

	PetRarity(int color) {
		this.color = color;
	}

	public String key() {
		return "golemaddons.pets.rarity." + name().toLowerCase(Locale.ROOT);
	}

	/** The pet level in a plain item name, or -1 when it isn't a pet. */
	public static int level(String plainName) {
		if (plainName == null) return -1;
		Matcher m = PET.matcher(plainName.strip());
		return m.matches() ? Integer.parseInt(m.group(1)) : -1;
	}

	/** The rarity whose colour is closest to {@code rgb}, or null if none is close (unknown colour, no colour). */
	public static PetRarity ofColor(int rgb) {
		if (rgb < 0) return null;
		PetRarity best = null;
		int bestDist = Integer.MAX_VALUE;
		for (PetRarity r : values()) {
			int dr = ((rgb >> 16) & 0xFF) - ((r.color >> 16) & 0xFF);
			int dg = ((rgb >> 8) & 0xFF) - ((r.color >> 8) & 0xFF);
			int db = (rgb & 0xFF) - (r.color & 0xFF);
			int d = dr * dr + dg * dg + db * db;
			if (d < bestDist) {
				bestDist = d;
				best = r;
			}
		}
		return bestDist <= MAX_DISTANCE_SQ ? best : null;
	}
}
