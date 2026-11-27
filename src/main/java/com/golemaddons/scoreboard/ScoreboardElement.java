package com.golemaddons.scoreboard;

import com.golemaddons.config.GolemConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What a line of the custom scoreboard shows: one of the server's lines, a blank line, or the user's own text. */
public enum ScoreboardElement {
	NAME("Name", false),
	PURSE("Purse", false),
	GEMS("Gems", false),
	CARROT_BUFF("Carrot Buff", false),
	PET("Pet", false),
	BOOSTER("Booster", false),
	LOCATION("Location", false),
	/** Everything else the server shows (quests, slayer progress, events), in the server's order and grouping. */
	ACTIVITY("Activity", false),
	WEBSITE("Website", false),
	BLANK("Blank line", true),
	CUSTOM("Custom text", true);

	public final String label;
	/** Can appear more than once (blank and custom lines). */
	public final boolean repeatable;

	ScoreboardElement(String label, boolean repeatable) {
		this.label = label;
		this.repeatable = repeatable;
	}

	public String key() {
		return "golemaddons.scoreboard.element." + name().toLowerCase(Locale.ROOT);
	}

	public static ScoreboardElement of(String name) {
		if (name == null) return null;
		try {
			return valueOf(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The server's layout, with a clock line, and the website line off (the footer shows it). */
	public static List<GolemConfig.Scoreboard.Element> defaults() {
		List<GolemConfig.Scoreboard.Element> out = new ArrayList<>();
		out.add(el(CUSTOM, true, "&7{date} &8{time}"));
		out.add(el(BLANK, true, ""));
		out.add(el(NAME, true, ""));
		out.add(el(PURSE, true, ""));
		out.add(el(GEMS, true, ""));
		out.add(el(CARROT_BUFF, true, ""));
		out.add(el(PET, true, ""));
		out.add(el(BOOSTER, true, ""));
		out.add(el(BLANK, true, ""));
		out.add(el(LOCATION, true, ""));
		out.add(el(BLANK, true, ""));
		out.add(el(ACTIVITY, true, ""));
		out.add(el(WEBSITE, false, ""));
		return out;
	}

	private static GolemConfig.Scoreboard.Element el(ScoreboardElement type, boolean enabled, String text) {
		return new GolemConfig.Scoreboard.Element(type.name(), enabled, text);
	}
}
