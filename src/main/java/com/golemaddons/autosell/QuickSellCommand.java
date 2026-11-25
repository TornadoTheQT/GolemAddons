package com.golemaddons.autosell;

import java.util.regex.Pattern;

/**
 * Validates the command the quick-sell key runs (e.g. {@code shop}). Plain Java, unit tested.
 * Only a single command word plus optional printable arguments are allowed, so the setting can't smuggle in
 * newlines, a second command or chat text.
 */
public final class QuickSellCommand {
	public static final int MAX_LENGTH = 64;
	private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_.:-]+( [ -~]{1,60})?");

	private QuickSellCommand() {
	}

	/**
	 * @return the command without its leading slash, "" if blank (quick sell off), or null if not allowed
	 */
	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String command = raw.strip();
		if (command.startsWith("/")) {
			command = command.substring(1).strip();
		}
		if (command.isEmpty()) {
			return "";
		}
		if (command.length() > MAX_LENGTH || !FORMAT.matcher(command).matches()) {
			return null;
		}
		return command;
	}
}
