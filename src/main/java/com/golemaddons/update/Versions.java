package com.golemaddons.update;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;

/** Semantic version comparison (0.1.10 is newer than 0.1.9; 0.2.0-beta.1 is older than 0.2.0). */
public final class Versions {
	private Versions() {
	}

	/** Negative, zero or positive like {@link Comparable}; a version that can't be parsed sorts lowest. */
	public static int compare(String a, String b) {
		SemanticVersion va = parse(a);
		SemanticVersion vb = parse(b);
		if (va == null || vb == null) return va == vb ? 0 : (va == null ? -1 : 1);
		return va.compareTo((net.fabricmc.loader.api.Version) vb);
	}

	/** True only when {@code candidate} parses and is strictly newer than a {@code current} that also parses. */
	public static boolean isNewer(String candidate, String current) {
		return parse(candidate) != null && parse(current) != null && compare(candidate, current) > 0;
	}

	static SemanticVersion parse(String v) {
		if (v == null || v.isBlank()) return null;
		try {
			return SemanticVersion.parse(v.startsWith("v") ? v.substring(1) : v);
		} catch (VersionParsingException e) {
			return null;
		}
	}
}
