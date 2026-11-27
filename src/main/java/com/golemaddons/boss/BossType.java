package com.golemaddons.boss;

import java.util.List;

/**
 * The bosses that have helpers, each recognised from the scoreboard sidebar while its fight is on. Add a new boss
 * here, then write {@link BossFeature}s for it and list them in {@link Bosses}.
 */
public enum BossType {
	/** Dragsim's Riftborn Seraph; the sidebar shows it as "Voidgloom Seraph V" with "Slay the boss!". */
	RIFTBORN_SERAPH("Riftborn Seraph", List.of("Voidgloom Seraph V", "Slay the boss!"));

	public final String displayName;
	/** Every one of these must appear (as part of a line) for the fight to count as on. */
	private final List<String> sidebarLines;

	BossType(String displayName, List<String> sidebarLines) {
		this.displayName = displayName;
		this.sidebarLines = sidebarLines;
	}

	public boolean matches(List<String> lines) {
		for (String wanted : sidebarLines) {
			boolean found = false;
			for (String line : lines) {
				if (line.contains(wanted)) {
					found = true;
					break;
				}
			}
			if (!found) return false;
		}
		return true;
	}
}
