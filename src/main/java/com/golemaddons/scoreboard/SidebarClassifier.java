package com.golemaddons.scoreboard;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sorts the server's sidebar lines into {@link ScoreboardElement}s from their plain text. Pure.
 *
 * <p>Known lines ("Name:", "Purse:", the location line, the website) get their own element; every other non-blank
 * line goes to {@link ScoreboardElement#ACTIVITY}, keeping the server's blank lines between groups of them.
 */
public final class SidebarClassifier {
	private static final Pattern WEBSITE = Pattern.compile("^[\\w-]+(\\.[\\w-]+)*\\.(co|com|net|gg|org|io|me|us|uk)$", Pattern.CASE_INSENSITIVE);

	private SidebarClassifier() {
	}

	/** Lines grouped by element, in server order. Blank lines only appear inside ACTIVITY, between its groups. */
	public static <T> Map<ScoreboardElement, List<T>> classify(List<String> plain, List<T> lines, T blank) {
		Map<ScoreboardElement, List<T>> out = new EnumMap<>(ScoreboardElement.class);
		boolean pendingGap = false;
		int n = Math.min(plain.size(), lines.size());
		for (int i = 0; i < n; i++) {
			String text = plain.get(i) == null ? "" : plain.get(i).strip();
			if (text.isEmpty()) {
				pendingGap = true;
				continue;
			}
			ScoreboardElement type = typeOf(text);
			List<T> list = out.computeIfAbsent(type, k -> new ArrayList<>());
			if (type == ScoreboardElement.ACTIVITY && pendingGap && !list.isEmpty()) list.add(blank);
			list.add(lines.get(i));
			pendingGap = false;
		}
		return out;
	}

	public static ScoreboardElement typeOf(String text) {
		String t = text.strip();
		String lower = t.toLowerCase(Locale.ROOT);
		if (lower.startsWith("name:")) return ScoreboardElement.NAME;
		if (lower.startsWith("purse:") || lower.startsWith("piggy:")) return ScoreboardElement.PURSE;
		if (lower.startsWith("gems:")) return ScoreboardElement.GEMS;
		if (lower.startsWith("carrot buff:")) return ScoreboardElement.CARROT_BUFF;
		if (lower.startsWith("pet:")) return ScoreboardElement.PET;
		if (lower.startsWith("booster:")) return ScoreboardElement.BOOSTER;
		if (WEBSITE.matcher(t).matches()) return ScoreboardElement.WEBSITE;
		if (isLocation(t)) return ScoreboardElement.LOCATION;
		return ScoreboardElement.ACTIVITY;
	}

	/** "⏣ Dragon's Den", "◎ Hub": a symbol, a space, then a name. */
	static boolean isLocation(String t) {
		if (t.length() < 3) return false;
		int first = t.codePointAt(0);
		int next = Character.charCount(first);
		if (next >= t.length() || t.charAt(next) != ' ') return false;
		int type = Character.getType(first);
		return type == Character.OTHER_SYMBOL || type == Character.MATH_SYMBOL;
	}
}
