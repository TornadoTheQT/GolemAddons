package com.golemaddons.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognises the slayer boss messages that repeat every fight (hitshields, mechanics, anchors, splits, Soulcry).
 * Pure: matches plain text with formatting already removed, so tests can drive it directly.
 */
public final class SlayerChatFilter {
	/** Groups the user can hide separately. */
	public enum Category {
		/** Spawn, hitshield up, hitshield broken. */
		HITSHIELD,
		/** Boss mechanics announcements: Broken Heart Radiation, Nukekubi Fixation, "pulls itself back together". */
		MECHANICS,
		/** The seraph split and its "match the mark" lines. */
		SPLIT,
		/** Anchor phase: hanging, each anchor down, all anchors down. */
		ANCHORS,
		/** Soulcry starting and fading. */
		SOULCRY
	}

	private static final String N = "[\\d.,]+[kKmMbB]?";

	private record Rule(Category category, Pattern pattern) {
	}

	private static final List<Rule> RULES = List.of(
			rule(Category.HITSHIELD, "Malevolent Hitshield! Land " + N + " hits? to break it"),
			rule(Category.HITSHIELD, "The Riftborn Seraph has spawned! Land " + N + " hits? to break its hitshield"),
			rule(Category.HITSHIELD, "The hitshield shatters! The Seraph can be damaged now"),
			rule(Category.MECHANICS, "BROKEN HEART RADIATION! Stay within " + N + " blocks and dodge the beams"),
			rule(Category.MECHANICS, "It pulls itself back together\\. Hit it while it is still loose"),
			rule(Category.MECHANICS, "NUKEKUBI FIXATION! Stare at the heads to destroy them"),
			rule(Category.SPLIT, "The seraph splits! You have " + N + " seconds? to prepare"),
			rule(Category.SPLIT, "^-\\s*\\S{0,2}\\s*(AETHER|VOID|FLESH|ICHOR)\\s*[•·]\\s*(Voidfang|Fleshrend) Dagger$"),
			rule(Category.ANCHORS, "It hangs itself on " + N + " anchors?\\. Shoot every one with a bow"),
			rule(Category.ANCHORS, "^Anchor down\\. " + N + " left"),
			rule(Category.ANCHORS, "Every anchor is down\\. It drops out of the air"),
			rule(Category.SOULCRY, "Soulcry! \\+" + N + " \\S* ?Ferocity against Endermen for " + N + "s"),
			rule(Category.SOULCRY, "Soulcry fades as the katana leaves your hand"));

	private SlayerChatFilter() {
	}

	private static Rule rule(Category category, String regex) {
		return new Rule(category, Pattern.compile(regex));
	}

	/** Which group the message belongs to, or null if it isn't one of the repeated slayer messages. */
	public static Category match(String plain) {
		if (plain == null) return null;
		String line = plain.strip();
		if (line.isEmpty() || line.length() > 200) return null;
		for (Rule r : RULES) {
			if (r.pattern().matcher(line).find()) return r.category();
		}
		return null;
	}

	/** The user's own filters: "|"-separated pieces of text; a message containing any of them (any case) is hidden. */
	public static List<String> customFilters(String spec) {
		List<String> out = new ArrayList<>();
		if (spec == null) return out;
		for (String part : spec.split("\\|")) {
			String p = part.strip().toLowerCase(Locale.ROOT);
			if (p.length() >= 2) out.add(p);
		}
		return out;
	}

	public static boolean matchesCustom(String plain, List<String> filters) {
		if (plain == null || filters.isEmpty()) return false;
		String lower = plain.toLowerCase(Locale.ROOT);
		for (String f : filters) if (lower.contains(f)) return true;
		return false;
	}
}
