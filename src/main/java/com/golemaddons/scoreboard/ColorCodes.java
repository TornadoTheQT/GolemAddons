package com.golemaddons.scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * "&" colour codes in user text: {@code &0-&f} colours, {@code &k-&o} styles, {@code &r} reset and
 * {@code &#RRGGBB} hex colours. {@code &&} is a literal "&". Pure: produces styled runs.
 */
public final class ColorCodes {
	/** A run of text in one style. {@code color} is RGB, or -1 for the default. */
	public record Run(String text, int color, boolean bold, boolean italic, boolean underline, boolean strike, boolean obfuscated) {
	}

	private static final int[] LEGACY = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555,
			0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	private ColorCodes() {
	}

	public static List<Run> parse(String text) {
		List<Run> out = new ArrayList<>();
		if (text == null || text.isEmpty()) return out;
		StringBuilder cur = new StringBuilder();
		int color = -1;
		boolean bold = false, italic = false, underline = false, strike = false, obf = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if ((c == '&' || c == '§') && i + 1 < text.length()) {
				char k = Character.toLowerCase(text.charAt(i + 1));
				if (c == '&' && k == '&') {
					cur.append('&');
					i++;
					continue;
				}
				int newColor = Integer.MIN_VALUE;
				if (k == '#' && isHex(text, i + 2, 6)) {
					newColor = Integer.parseInt(text.substring(i + 2, i + 8), 16);
				} else if ((k >= '0' && k <= '9') || (k >= 'a' && k <= 'f')) {
					newColor = LEGACY[Character.digit(k, 16)];
				}
				boolean style = "klmnor".indexOf(k) >= 0;
				if (newColor != Integer.MIN_VALUE || style) {
					if (!cur.isEmpty()) {
						out.add(new Run(cur.toString(), color, bold, italic, underline, strike, obf));
						cur.setLength(0);
					}
					if (newColor != Integer.MIN_VALUE) {
						color = newColor;
						bold = italic = underline = strike = obf = false;
						i += k == '#' ? 7 : 1;
					} else {
						switch (k) {
							case 'k' -> obf = true;
							case 'l' -> bold = true;
							case 'm' -> strike = true;
							case 'n' -> underline = true;
							case 'o' -> italic = true;
							default -> {
								color = -1;
								bold = italic = underline = strike = obf = false;
							}
						}
						i++;
					}
					continue;
				}
			}
			cur.append(c);
		}
		if (!cur.isEmpty()) out.add(new Run(cur.toString(), color, bold, italic, underline, strike, obf));
		return out;
	}

	private static boolean isHex(String s, int from, int count) {
		if (from + count > s.length()) return false;
		for (int i = from; i < from + count; i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
		return true;
	}

	/** The text with every code removed. */
	public static String strip(String text) {
		StringBuilder b = new StringBuilder();
		for (Run r : parse(text)) b.append(r.text());
		return b.toString();
	}
}
