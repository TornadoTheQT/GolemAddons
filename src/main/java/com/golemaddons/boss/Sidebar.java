package com.golemaddons.boss;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** The scoreboard sidebar as plain text lines, the way it is drawn (team prefix + name + suffix). */
public final class Sidebar {
	private Sidebar() {
	}

	public static List<String> lines(Minecraft mc) {
		List<String> out = new ArrayList<>();
		if (mc.level == null) return out;
		Scoreboard scoreboard = mc.level.getScoreboard();
		Objective objective;
		com.golemaddons.scoreboard.ScoreboardFeature.beginRead();
		try {
			objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		} finally {
			com.golemaddons.scoreboard.ScoreboardFeature.endRead();
		}
		if (objective == null) return out;
		out.add(clean(objective.getDisplayName().getString()));
		for (PlayerScoreEntry score : scoreboard.listPlayerScores(objective)) {
			if (score.isHidden()) continue;
			PlayerTeam team = scoreboard.getPlayersTeam(score.owner());
			out.add(clean(PlayerTeam.formatNameForTeam(team, score.ownerName()).getString()));
		}
		return out;
	}

	/** Drops formatting codes and the invisible characters servers pad lines with. */
	public static String clean(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§') {
				i++;
			} else if (Character.getType(c) != Character.FORMAT && c != '​') {
				b.append(c);
			}
		}
		return b.toString().strip();
	}
}
