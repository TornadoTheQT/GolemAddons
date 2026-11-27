package com.golemaddons.boss;

import java.util.List;

/**
 * The bosses that have helpers, each recognised from the scoreboard sidebar while its fight is on. Add a new boss
 * here, then write {@link BossFeature}s for it and list them in {@link Bosses}.
 */
public enum BossType {
	/** Dragsim's Riftborn Seraph: the sidebar shows "Riftborn Seraph" (or "Voidgloom Seraph V") and "Slay the boss!". */
	RIFTBORN_SERAPH("Riftborn Seraph", List.of(List.of("Riftborn Seraph", "Voidgloom Seraph"), List.of("Slay the boss!")));

	public final String displayName;
	/** Each group needs one of its texts somewhere in the sidebar (as part of a line) for the fight to count as on. */
	private final List<List<String>> sidebarLines;

	BossType(String displayName, List<List<String>> sidebarLines) {
		this.displayName = displayName;
		this.sidebarLines = sidebarLines;
	}

	public boolean matches(List<String> lines) {
		for (List<String> anyOf : sidebarLines) {
			boolean found = false;
			for (String line : lines) {
				for (String wanted : anyOf) {
					if (line.contains(wanted)) {
						found = true;
						break;
					}
				}
				if (found) break;
			}
			if (!found) return false;
		}
		return true;
	}
}
