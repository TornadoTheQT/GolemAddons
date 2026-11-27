package com.golemaddons.scoreboard;

import com.golemaddons.config.GolemConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Builds the board's lines from the user's element list and the classified server lines. Pure. */
public final class ScoreboardLayout {
	public static final int MAX_LINES = 30;

	private ScoreboardLayout() {
	}

	/**
	 * Lines in the user's order. Disabled elements and elements the server isn't showing are skipped; blank lines
	 * never stack up, start or end the board.
	 */
	public static <T> List<T> build(List<GolemConfig.Scoreboard.Element> elements, Map<ScoreboardElement, List<T>> server, T blank,
			Function<String, T> custom) {
		List<T> out = new ArrayList<>();
		boolean lastBlank = true; // no blank at the very top
		for (GolemConfig.Scoreboard.Element e : elements) {
			if (e == null || !e.enabled) continue;
			ScoreboardElement type = ScoreboardElement.of(e.type);
			if (type == null) continue;
			switch (type) {
				case BLANK -> {
					if (!lastBlank) {
						out.add(blank);
						lastBlank = true;
					}
				}
				case CUSTOM -> {
					if (e.text == null || e.text.isBlank()) continue;
					out.add(custom.apply(e.text));
					lastBlank = false;
				}
				default -> {
					List<T> lines = server.get(type);
					if (lines == null) continue;
					for (T line : lines) {
						boolean isBlank = line == blank;
						if (isBlank && lastBlank) continue;
						out.add(line);
						lastBlank = isBlank;
					}
				}
			}
			if (out.size() >= MAX_LINES) break;
		}
		while (!out.isEmpty() && out.get(out.size() - 1) == blank) out.remove(out.size() - 1);
		return out.size() > MAX_LINES ? new ArrayList<>(out.subList(0, MAX_LINES)) : out;
	}
}
