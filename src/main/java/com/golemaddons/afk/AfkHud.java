package com.golemaddons.afk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Draws the AFK dragon tracker panel at a given place and scale. Also used, with sample numbers, by the HUD editor. */
public final class AfkHud {
	private static final int PAD = 5;
	private static final int LINE = 10;

	/** What the panel shows. */
	public record Model(String elapsed, Component status, int statusColor, Map<String, Integer> counts, int total, int reconnects) {
	}

	private AfkHud() {
	}

	/** Unscaled size of the panel. */
	public static int[] size(Font font, Model m) {
		List<Line> lines = lines(m);
		int w = 0;
		for (Line l : lines) w = Math.max(w, font.width(l.left()) + (l.right().isEmpty() ? 0 : font.width(l.right()) + 12));
		return new int[] {w + 2 * PAD + 2, lines.size() * LINE + 2 * PAD};
	}

	public static void draw(GuiGraphicsExtractor g, Font font, int x, int y, float scale, Model m) {
		int[] size = size(font, m);
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		int w = size[0];
		int h = size[1];
		g.fill(1, 0, w - 1, h, 0xC0100E1A);
		g.fill(0, 1, w, h - 1, 0xC0100E1A);
		g.fill(0, 1, 2, h - 1, 0xFF9D7BFF);
		g.fill(2, 0, w / 2, 1, 0xCC5CE1C6);
		int ly = PAD;
		for (Line l : lines(m)) {
			g.text(font, l.left(), PAD + 2, ly, l.color(), true);
			if (!l.right().isEmpty()) g.text(font, l.right(), w - PAD - font.width(l.right()), ly, l.rightColor(), true);
			ly += LINE;
		}
		g.pose().popMatrix();
	}

	private record Line(String left, int color, String right, int rightColor) {
	}

	private static List<Line> lines(Model m) {
		List<Line> out = new ArrayList<>();
		out.add(new Line("AFK Dragons", 0xFFE0B04A, m.elapsed(), 0xFFA0A0A0));
		out.add(new Line(m.status().getString(), m.statusColor(), "", 0));
		if (m.counts().isEmpty()) {
			out.add(new Line("No dragons yet", 0xFF6A6A72, "", 0));
		} else {
			for (Map.Entry<String, Integer> e : m.counts().entrySet()) {
				out.add(new Line(e.getKey(), colorOf(e.getKey()), "x" + e.getValue(), 0xFFFFFFFF));
			}
		}
		out.add(new Line("Total", 0xFFFFFFFF, Integer.toString(m.total()), 0xFF5CE1C6));
		if (m.reconnects() > 0) out.add(new Line("Reconnects", 0xFFA0A0A0, Integer.toString(m.reconnects()), 0xFFE0B04A));
		return out;
	}

	/** A colour per dragon, roughly like its head. */
	static int colorOf(String dragon) {
		String d = dragon.toLowerCase(java.util.Locale.ROOT);
		if (d.contains("superior")) return 0xFFFFD84A;
		if (d.contains("strong")) return 0xFFFF6E4A;
		if (d.contains("unstable")) return 0xFFB07BFF;
		if (d.contains("wise")) return 0xFF5CD6E1;
		if (d.contains("young")) return 0xFFF0F0F0;
		if (d.contains("old")) return 0xFFC8B48C;
		if (d.contains("protector")) return 0xFFB0B0B8;
		if (d.contains("holy")) return 0xFFFFF0A0;
		return 0xFFD0D0D8;
	}

	/** "1h 05m" / "4m 12s". */
	public static String elapsed(long ms) {
		long s = Math.max(0, ms / 1000);
		return s >= 3600 ? String.format(java.util.Locale.ROOT, "%dh %02dm", s / 3600, s / 60 % 60)
				: String.format(java.util.Locale.ROOT, "%dm %02ds", s / 60, s % 60);
	}
}
