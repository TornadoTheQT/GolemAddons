package com.golemaddons.update;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Turns the small amount of Markdown GitHub release notes use (headings, bullets, **bold**, `code`, [links](url))
 * into styled lines. Release notes are untrusted: everything becomes literal text with colour and bold only, never a
 * click or hover event, and links show their text without the address.
 */
public final class ReleaseNotes {
	private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\[([^\\]]+)]\\([^)]*\\)");
	private static final int HEADING = 0xFF9D7BFF;
	private static final int CODE = 0xFF5CE1C6;

	private ReleaseNotes() {
	}

	public static List<Component> lines(String markdown) {
		List<Component> out = new ArrayList<>();
		if (markdown == null) return out;
		boolean blank = true;
		for (String raw : markdown.replace("\r", "").split("\n", -1)) {
			String line = raw.strip();
			if (line.isEmpty()) {
				if (!blank) out.add(Component.empty());
				blank = true;
				continue;
			}
			blank = false;
			if (line.startsWith("#")) {
				String text = line.replaceFirst("^#+\\s*", "");
				out.add(inline(text, Style.EMPTY.withBold(true).withColor(HEADING & 0xFFFFFF)));
			} else if (line.matches("^[-*+]\\s+.*")) {
				out.add(Component.literal("  • ").append(inline(line.substring(1).strip(), Style.EMPTY)));
			} else {
				out.add(inline(line, Style.EMPTY));
			}
		}
		while (!out.isEmpty() && out.getLast().getString().isEmpty()) out.removeLast();
		return out;
	}

	static MutableComponent inline(String text, Style base) {
		MutableComponent line = Component.empty().withStyle(base);
		Matcher m = INLINE.matcher(text);
		int at = 0;
		while (m.find()) {
			if (m.start() > at) line.append(Component.literal(text.substring(at, m.start())));
			if (m.group(1) != null || m.group(2) != null) {
				line.append(Component.literal(m.group(1) != null ? m.group(1) : m.group(2)).withStyle(ChatFormatting.BOLD));
			} else if (m.group(3) != null) {
				line.append(Component.literal(m.group(3)).withColor(CODE & 0xFFFFFF));
			} else {
				line.append(Component.literal(m.group(4)).withStyle(ChatFormatting.UNDERLINE));
			}
			at = m.end();
		}
		if (at < text.length()) line.append(Component.literal(text.substring(at)));
		return line;
	}
}
