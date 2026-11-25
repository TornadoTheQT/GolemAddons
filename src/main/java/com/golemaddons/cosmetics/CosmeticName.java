package com.golemaddons.cosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parses and validates custom names written with {@code &} codes, e.g. {@code &6&lZach}. Plain Java, unit tested.
 * The Cloudflare Worker applies the same rules (worker/src/validate.ts); keep the two in sync.
 *
 * <p>Rules: at most {@value #MAX_RAW} characters after trimming; {@code &0-9 a-f} colors, {@code &k-o} formats,
 * {@code &r} reset, {@code &&} a literal ampersand, any other {@code &x} is an error. Visible text is 1 to
 * {@value #MAX_VISIBLE} code points, not only whitespace, and has at least one character that isn't obfuscated.
 * {@code §}, control and format characters (this covers bidi overrides and zero-width characters), line and
 * paragraph separators, private-use, unassigned and lone surrogate code points are rejected.
 */
public final class CosmeticName {
	public static final int MAX_RAW = 64;
	public static final int MAX_VISIBLE = 24;

	public enum Problem { EMPTY, TOO_LONG, BAD_CHARACTER, BAD_CODE, TOO_MANY_VISIBLE, NO_VISIBLE_TEXT, CONTACT_INFO }

	/**
	 * Names are shown to everyone, so they may not carry links, IP addresses, emails or phone numbers (someone
	 * else's or your own). The Worker enforces the same patterns (worker/src/validate.ts containsContactInfo).
	 */
	private static final Pattern[] CONTACT_INFO_PATTERNS = {
			Pattern.compile("\\d{1,3}(?:\\s*[.,:]\\s*\\d{1,3}){3}"), // IPv4, also "1 . 2 . 3 . 4"
			Pattern.compile("[0-9a-f]{1,4}(?::[0-9a-f]{0,4}){3,}"), // IPv6
			Pattern.compile("[^\\s@]+@[^\\s@]+\\.[a-z]{2,}"), // email
			Pattern.compile("(?:\\d[\\s.\\-()]*){7,}"), // phone-like: 7+ digits
			Pattern.compile(":/{2}|www\\.|discord\\.gg|\\.(?:com|net|org|gg|io|xyz|me|co|tk|ru|de|uk|us|info|dev|app|ly|link|site|online|club|top)\\b"),
	};

	static boolean containsContactInfo(String plain) {
		String text = plain.toLowerCase(Locale.ROOT);
		for (Pattern p : CONTACT_INFO_PATTERNS) {
			if (p.matcher(text).find()) return true;
		}
		return false;
	}

	/** One stretch of text with a single style. {@code color} is 0-15 (the vanilla color order), or -1 for none. */
	public record Run(String text, int color, boolean bold, boolean italic, boolean underlined, boolean strikethrough,
			boolean obfuscated) {
	}

	/** Either {@code problem} is null and {@code raw}/{@code runs} hold the parsed name, or it says what's wrong. */
	public record Result(String raw, List<Run> runs, String plain, Problem problem, String detail) {
		public boolean valid() {
			return problem == null;
		}
	}

	private CosmeticName() {
	}

	public static Result parse(String input) {
		if (input == null) {
			return fail(Problem.EMPTY, "");
		}
		String raw = input.strip();
		if (raw.isEmpty()) {
			return fail(Problem.EMPTY, "");
		}
		if (raw.length() > MAX_RAW) {
			return fail(Problem.TOO_LONG, Integer.toString(MAX_RAW));
		}

		List<Run> runs = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		StringBuilder plain = new StringBuilder();
		int color = -1;
		boolean bold = false, italic = false, underlined = false, strike = false, obfuscated = false;
		int visible = 0;
		boolean anyClear = false;
		boolean anyNonSpace = false;

		for (int i = 0; i < raw.length(); ) {
			int cp = raw.codePointAt(i);
			int len = Character.charCount(cp);
			if (cp == '&') {
				if (i + 1 >= raw.length()) {
					return fail(Problem.BAD_CODE, "&");
				}
				char code = Character.toLowerCase(raw.charAt(i + 1));
				i += 2;
				if (code == '&') {
					cp = '&';
					len = 0; // already advanced; fall through to append a literal '&'
				} else {
					int newColor = Character.digit(code, 16);
					boolean isFormat = code >= 'k' && code <= 'o';
					if (newColor < 0 && !isFormat && code != 'r') {
						return fail(Problem.BAD_CODE, "&" + raw.charAt(i - 1));
					}
					if (text.length() > 0) {
						runs.add(new Run(text.toString(), color, bold, italic, underlined, strike, obfuscated));
						text.setLength(0);
					}
					if (newColor >= 0 || code == 'r') {
						// Like vanilla: a color or reset clears all formats.
						color = newColor;
						bold = italic = underlined = strike = obfuscated = false;
					} else {
						switch (code) {
							case 'k' -> obfuscated = true;
							case 'l' -> bold = true;
							case 'm' -> strike = true;
							case 'n' -> underlined = true;
							default -> italic = true; // 'o'
						}
					}
					continue;
				}
			}
			if (!allowed(cp)) {
				return fail(Problem.BAD_CHARACTER, String.format(Locale.ROOT, "U+%04X", cp));
			}
			text.appendCodePoint(cp);
			plain.appendCodePoint(cp);
			visible++;
			if (!Character.isWhitespace(cp)) {
				anyNonSpace = true;
				if (!obfuscated) anyClear = true;
			}
			i += len;
		}
		if (text.length() > 0) {
			runs.add(new Run(text.toString(), color, bold, italic, underlined, strike, obfuscated));
		}
		if (visible > MAX_VISIBLE) {
			return fail(Problem.TOO_MANY_VISIBLE, Integer.toString(MAX_VISIBLE));
		}
		if (!anyNonSpace || !anyClear) {
			return fail(Problem.NO_VISIBLE_TEXT, "");
		}
		if (containsContactInfo(plain.toString())) {
			return fail(Problem.CONTACT_INFO, "");
		}
		return new Result(raw, List.copyOf(runs), plain.toString(), null, "");
	}

	static boolean allowed(int cp) {
		if (cp == '§') {
			return false;
		}
		return switch (Character.getType(cp)) {
			case Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
					Character.PRIVATE_USE, Character.SURROGATE, Character.UNASSIGNED -> false;
			default -> true;
		};
	}

	private static Result fail(Problem problem, String detail) {
		return new Result(null, List.of(), "", problem, detail);
	}
}
