package com.golemaddons.scoreboard;

import java.util.Map;

/** {name} placeholders in custom lines, filled from a map; unknown ones are left as typed. Pure. */
public final class Placeholders {
	public static final String[] NAMES = {"time", "date", "fps", "ping", "x", "y", "z", "players"};

	private Placeholders() {
	}

	public static boolean has(String text) {
		return text != null && text.indexOf('{') >= 0;
	}

	public static String fill(String text, Map<String, String> values) {
		if (!has(text)) return text;
		StringBuilder b = new StringBuilder(text.length() + 16);
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '{') {
				int end = text.indexOf('}', i + 1);
				if (end > i + 1 && end - i <= 12) {
					String v = values.get(text.substring(i + 1, end).toLowerCase(java.util.Locale.ROOT));
					if (v != null) {
						b.append(v);
						i = end + 1;
						continue;
					}
				}
			}
			b.append(c);
			i++;
		}
		return b.toString();
	}
}
