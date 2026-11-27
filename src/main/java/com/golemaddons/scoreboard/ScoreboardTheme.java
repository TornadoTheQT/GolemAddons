package com.golemaddons.scoreboard;

import com.golemaddons.config.GolemConfig;

/** Background themes. Colours are only the defaults: each theme's colours can be changed. */
public enum ScoreboardTheme {
	SOLID(0xFF0E0C18, 0xFF0E0C18, 0xFF9D7BFF, 0xFFFFFFFF),
	GRADIENT(0xFF2A1450, 0xFF0B3A44, 0xFF9D7BFF, 0xFFFFFFFF),
	/** Night sky with drifting green and violet curtains of light and twinkling stars. */
	AURORA(0xFF070A1C, 0xFF31E8A6, 0xFF9D7BFF, 0xFFE8FFF6),
	/** Black with falling green glyphs. */
	MATRIX(0xFF000502, 0xFF00170A, 0xFF00FF66, 0xFF3BFF7A),
	/** Embers rising over a glowing base. */
	FIRE(0xFF140302, 0xFF5A1003, 0xFFFF7A1A, 0xFFFFC24A),
	/** Sunlit water fading to the deep, with light shafts and bubbles. */
	OCEAN(0xFF0B5C7A, 0xFF03122E, 0xFF7FE8FF, 0xFFCFF6FF);

	private final int primary;
	private final int secondary;
	private final int accent;
	private final int particle;

	ScoreboardTheme(int primary, int secondary, int accent, int particle) {
		this.primary = primary;
		this.secondary = secondary;
		this.accent = accent;
		this.particle = particle;
	}

	public GolemConfig.Scoreboard.ThemeColors defaults() {
		return new GolemConfig.Scoreboard.ThemeColors(primary, secondary, accent, particle);
	}

	public String key() {
		return "golemaddons.scoreboard.theme." + name().toLowerCase(java.util.Locale.ROOT);
	}

	public static ScoreboardTheme of(String name) {
		if (name != null) {
			for (ScoreboardTheme t : values()) if (t.name().equalsIgnoreCase(name)) return t;
		}
		return AURORA;
	}
}
