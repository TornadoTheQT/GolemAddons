package com.golemaddons.cosmetics;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Hashing and ID helpers shared by the client and mirrored in the Worker. Plain Java, unit tested. */
public final class Hashes {
	private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

	private Hashes() {
	}

	public static String sha256Hex(byte[] data) {
		return HexFormat.of().formatHex(digest("SHA-256", data));
	}

	public static boolean isSha256Hex(String value) {
		return value != null && SHA256_HEX.matcher(value).matches();
	}

	/** UUID as 32 lowercase hex digits, the form the API uses. */
	public static String compactUuid(UUID uuid) {
		return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}

	/** Parses 32-hex or dashed UUIDs; null if malformed. */
	public static UUID parseUuid(String value) {
		if (value == null) {
			return null;
		}
		if (value.length() == 36) {
			if (value.charAt(8) != '-' || value.charAt(13) != '-' || value.charAt(18) != '-' || value.charAt(23) != '-') {
				return null;
			}
		} else if (value.length() != 32) {
			return null;
		}
		String hex = value.replace("-", "");
		if (hex.length() != 32) {
			return null;
		}
		for (int i = 0; i < 32; i++) {
			char c = hex.charAt(i);
			boolean hexDigit = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
			if (!hexDigit) return null;
		}
		return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
	}

	private static byte[] digest(String algorithm, byte[] data) {
		try {
			return MessageDigest.getInstance(algorithm).digest(data);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(algorithm + " missing from the JDK", e);
		}
	}
}
