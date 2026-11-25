package com.golemaddons.cosmetics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;

/**
 * Swaps players' usernames for their custom names inside text the server sends: chat, the sidebar, tab list names,
 * nametags. Servers often put a rank in front, e.g. {@code [DRAGON] qtTornado}; a bracketed rank directly before
 * the username (only spaces between) is removed together with it. Without a rank only the username is replaced.
 *
 * <p>A username only matches as a whole word ({@code qtTornado} never matches inside {@code qtTornado2}), exactly as
 * written, and only usernames that are valid Minecraft names are considered. Text is read as it is displayed, so
 * legacy {@code §} colors and names split across differently colored parts still match; everything around the match
 * keeps its exact styling, including click and hover actions.
 *
 * <p>Render code calls this every frame with freshly built but identical components, so results are memoized by
 * component equality. With no custom names around it returns immediately.
 */
public final class NameRewriter {
	/** Minecraft usernames: 1-16 of A-Z, a-z, 0-9, underscore. */
	public static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	/** A rank in brackets directly before the name, e.g. "[DRAGON] " or "[MVP+]". */
	private static final String RANK = "(?:\\[[^\\[\\]\\n]{1,32}\\] *)?";
	private static final int MEMO_SIZE = 512;

	private record Snapshot(Map<String, Component> names, Pattern pattern) {
	}

	private static final Snapshot EMPTY = new Snapshot(Map.of(), null);

	private volatile Snapshot snapshot = EMPTY;
	private final Map<Component, Component> memo = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Component, Component> eldest) {
			return size() > MEMO_SIZE;
		}
	};

	/** Sets the usernames to replace and their custom names. Invalid usernames are ignored. Returns true if changed. */
	public boolean update(Map<String, Component> names) {
		Snapshot current = snapshot;
		if (sameNames(current.names(), names)) {
			return false;
		}
		Map<String, Component> valid = new LinkedHashMap<>();
		names.forEach((user, custom) -> {
			if (user != null && custom != null && USERNAME.matcher(user).matches()) valid.put(user, custom);
		});
		Snapshot next = valid.isEmpty() ? EMPTY : new Snapshot(Map.copyOf(valid), compile(valid.keySet()));
		synchronized (memo) {
			memo.clear();
			snapshot = next;
		}
		return true;
	}

	public void clear() {
		update(Map.of());
	}

	public boolean isEmpty() {
		return snapshot.names().isEmpty();
	}

	/** The text with custom names swapped in, or {@code input} itself when nothing matched. */
	public Component rewrite(Component input) {
		Snapshot s = snapshot;
		if (input == null || s.pattern() == null) {
			return input;
		}
		synchronized (memo) {
			Component cached = memo.get(input);
			if (cached != null) {
				return cached;
			}
		}
		Component result = apply(input, s);
		synchronized (memo) {
			if (snapshot == s) memo.put(input, result);
		}
		return result;
	}

	private static boolean sameNames(Map<String, Component> current, Map<String, Component> next) {
		if (current.size() != next.size()) return false;
		for (Map.Entry<String, Component> e : next.entrySet()) {
			if (current.get(e.getKey()) != e.getValue()) return false;
		}
		return true;
	}

	private static Pattern compile(Iterable<String> usernames) {
		List<String> sorted = new ArrayList<>();
		usernames.forEach(sorted::add);
		sorted.sort(Comparator.comparingInt(String::length).reversed());
		StringBuilder alternatives = new StringBuilder();
		for (String name : sorted) {
			if (!alternatives.isEmpty()) alternatives.append('|');
			alternatives.append(Pattern.quote(name));
		}
		return Pattern.compile(RANK + "(?<![A-Za-z0-9_])(" + alternatives + ")(?![A-Za-z0-9_])");
	}

	static Component apply(Component input, Snapshot s) {
		// Flatten to displayed text with one resolved style per char (legacy § codes applied, like rendering does).
		StringBuilder text = new StringBuilder();
		List<Style> styles = new ArrayList<>();
		StringDecomposer.iterateFormatted(input, Style.EMPTY, (index, style, codePoint) -> {
			text.appendCodePoint(codePoint);
			styles.add(style);
			if (Character.isSupplementaryCodePoint(codePoint)) styles.add(style);
			return true;
		});
		Matcher m = s.pattern().matcher(text);
		if (!m.find()) {
			return input;
		}
		MutableComponent out = Component.empty();
		int pos = 0;
		do {
			appendStyled(out, text, styles, pos, m.start());
			// The custom name takes the style the username had (its color, click and hover actions); its own
			// colors and formats override that.
			// Hovering the custom name (in chat) shows whose it really is, so it can't be used to pass as someone else.
			Style base = styles.get(m.start(1)).withHoverEvent(new HoverEvent.ShowText(
					Component.translatable("golemaddons.cosmetics.real_name", m.group(1))));
			out.append(Component.empty().setStyle(base).append(s.names().get(m.group(1))));
			pos = m.end();
		} while (m.find());
		appendStyled(out, text, styles, pos, text.length());
		return out;
	}

	private static void appendStyled(MutableComponent out, CharSequence text, List<Style> styles, int from, int to) {
		int start = from;
		while (start < to) {
			Style style = styles.get(start);
			int end = start + 1;
			while (end < to && styles.get(end).equals(style)) end++;
			out.append(Component.literal(text.subSequence(start, end).toString()).setStyle(style));
			start = end;
		}
	}
}
