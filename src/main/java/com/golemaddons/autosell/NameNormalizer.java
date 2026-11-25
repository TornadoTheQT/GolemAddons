package com.golemaddons.autosell;

import java.util.regex.Pattern;

/** Turns an item or screen name into the plain text that rules are matched against. */
public final class NameNormalizer {
	/** Names longer than this are never matched. */
	public static final int MAX_NAME_LENGTH = 256;

	private static final Pattern LEGACY_FORMATTING = Pattern.compile("§.?");

	private NameNormalizer() {
	}

	/**
	 * Strips legacy {@code §} formatting codes and surrounding whitespace.
	 *
	 * @return the plain name, or {@code null} if the name is too long or contains control characters
	 *         (in which case the caller must treat it as "no match")
	 */
	public static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		String plain = LEGACY_FORMATTING.matcher(raw).replaceAll("").strip();
		if (plain.length() > MAX_NAME_LENGTH) {
			return null;
		}
		for (int i = 0; i < plain.length(); i++) {
			if (Character.isISOControl(plain.charAt(i))) {
				return null;
			}
		}
		return plain;
	}
}
