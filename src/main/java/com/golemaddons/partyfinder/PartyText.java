package com.golemaddons.partyfinder;

import java.util.regex.Pattern;

/**
 * Checks party names and descriptions before sending, with the same rules the Worker enforces
 * (worker/src/party.ts cleanText), so typing mistakes are caught instantly. The server checks again; this is
 * only for feedback. Plain Java, unit tested.
 */
public final class PartyText {
	public static final int NAME_MAX = 32;
	public static final int DESC_MAX = 120;
	/** Minecraft usernames; also what the leader's game puts in the invite command. */
	public static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

	public enum Problem { EMPTY, TOO_LONG, INVALID, CONTACT_INFO }

	public record Result(String text, Problem problem) {
		public boolean valid() {
			return problem == null;
		}
	}

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final Pattern[] CONTACT_INFO = {
			Pattern.compile("\\b(?:https?|ftp)://", Pattern.CASE_INSENSITIVE),
			Pattern.compile("\\bwww\\.", Pattern.CASE_INSENSITIVE),
			Pattern.compile("\\b[a-z0-9-]{2,}(?:\\.|\\s*\\(dot\\)\\s*|\\s*\\[dot\\]\\s*)(?:com|net|org|gg|io|me|co|xyz|tk|ly|de|uk|ru|cc|tv|us|info|biz|link|site|online|club|app|dev)\\b",
					Pattern.CASE_INSENSITIVE),
			Pattern.compile("\\bdiscord(?:\\.gg|app\\.com|\\.com/invite)\\b", Pattern.CASE_INSENSITIVE),
			Pattern.compile("\\b\\d{1,3}(?:\\s*[.,]\\s*\\d{1,3}){3}\\b"),
			Pattern.compile("\\b[0-9a-f]{1,4}(?::[0-9a-f]{0,4}){4,7}\\b", Pattern.CASE_INSENSITIVE),
			Pattern.compile("[a-z0-9._%+-]+\\s*(?:@|\\(at\\)|\\[at\\])\\s*[a-z0-9-]+\\s*(?:\\.|\\(dot\\)|\\[dot\\])\\s*[a-z]{2,}",
					Pattern.CASE_INSENSITIVE),
			Pattern.compile("(?:\\d[\\s().-]*){7,}"),
	};

	private PartyText() {
	}

	public static Result check(String raw, int max, boolean allowEmpty) {
		String text = raw == null ? "" : WHITESPACE.matcher(raw).replaceAll(" ").strip();
		if (text.isEmpty()) return new Result(text, allowEmpty ? null : Problem.EMPTY);
		if (text.codePointCount(0, text.length()) > max) return new Result(text, Problem.TOO_LONG);
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			int type = Character.getType(cp);
			if (cp == '§' || cp == 0xFFFD || type == Character.CONTROL || type == Character.FORMAT
					|| type == Character.PRIVATE_USE || type == Character.UNASSIGNED || type == Character.SURROGATE) {
				return new Result(text, Problem.INVALID);
			}
			i += Character.charCount(cp);
		}
		for (Pattern p : CONTACT_INFO) {
			if (p.matcher(text).find()) return new Result(text, Problem.CONTACT_INFO);
		}
		return new Result(text, null);
	}

	public static boolean validPlayerName(String name) {
		return name != null && PLAYER_NAME.matcher(name).matches();
	}

	/**
	 * The chat command that invites {@code name}, from a template like {@code "p {name}"}; null if the template or
	 * name is unsafe. Only letters, digits, spaces and {@code _:-} are allowed around the placeholder, so a
	 * template can never become a different command with extra arguments.
	 */
	public static String inviteCommand(String template, String name) {
		if (!validTemplate(template) || !validPlayerName(name)) return null;
		String t = template.strip();
		if (t.startsWith("/")) t = t.substring(1);
		return t.replace("{name}", name);
	}

	public static boolean validTemplate(String template) {
		if (template == null) return false;
		String t = template.strip();
		if (t.startsWith("/")) t = t.substring(1);
		return t.length() <= 48 && t.matches("[A-Za-z][A-Za-z0-9_:-]*(?: [A-Za-z0-9_:-]+)*(?: \\{name\\})(?: [A-Za-z0-9_:-]+)*")
				&& t.indexOf("{name}") == t.lastIndexOf("{name}");
	}
}
