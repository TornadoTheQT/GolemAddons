package com.golemaddons.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reads GitHub's release JSON. Everything in it is treated as untrusted and checked before use. */
public final class ReleaseParser {
	/** v1.2.3, optionally with a pre-release or build suffix. Anything else is not a release tag of ours. */
	static final Pattern TAG = Pattern.compile("^v?\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}([-.+][0-9A-Za-z.-]{1,32})?$");
	private static final Pattern SHA256 = Pattern.compile("^sha256:([0-9a-fA-F]{64})$");
	private static final int MAX_NOTES = 8000;

	public enum Problem {
		/** The response was not the shape GitHub sends. */
		MALFORMED,
		/** No published, non-draft release with a valid tag. */
		NO_RELEASE,
		/** The release has no {@code golemaddons-<version>.jar} file. */
		NO_ASSET
	}

	/** A parse failure; {@link #problem()} says which. */
	public static final class ParseException extends Exception {
		private final Problem problem;

		ParseException(Problem problem) {
			super(problem.name(), null, false, false);
			this.problem = problem;
		}

		public Problem problem() {
			return problem;
		}
	}

	private ReleaseParser() {
	}

	/** One release object, e.g. from {@code /releases/latest}. */
	public static Release parse(JsonElement json) throws ParseException {
		if (json == null || !json.isJsonObject()) throw new ParseException(Problem.MALFORMED);
		JsonObject o = json.getAsJsonObject();
		if (bool(o, "draft")) throw new ParseException(Problem.NO_RELEASE);
		String tag = string(o, "tag_name");
		if (tag == null || !TAG.matcher(tag).matches()) throw new ParseException(Problem.NO_RELEASE);
		String version = tag.startsWith("v") ? tag.substring(1) : tag;
		String notes = string(o, "body");
		notes = notes == null ? "" : notes.replace("\r", "");
		if (notes.length() > MAX_NOTES) notes = notes.substring(0, MAX_NOTES) + "…";

		String expectedName = "golemaddons-" + version + ".jar";
		JsonElement assets = o.get("assets");
		if (assets == null || !assets.isJsonArray()) throw new ParseException(Problem.MALFORMED);
		for (JsonElement e : assets.getAsJsonArray()) {
			if (!e.isJsonObject()) continue;
			JsonObject asset = e.getAsJsonObject();
			if (!expectedName.equals(string(asset, "name"))) continue;
			String url = string(asset, "browser_download_url");
			if (url == null || !url.equals(UpdateSource.DOWNLOAD_PREFIX + tag + "/" + expectedName)) {
				throw new ParseException(Problem.NO_ASSET);
			}
			long size = asset.has("size") && asset.get("size").isJsonPrimitive() ? asset.get("size").getAsLong() : -1;
			if (size <= 0 || size > UpdateSource.MAX_JAR_BYTES) throw new ParseException(Problem.NO_ASSET);
			String digest = string(asset, "digest");
			String sha256 = null;
			if (digest != null) {
				var m = SHA256.matcher(digest);
				if (m.matches()) sha256 = m.group(1).toLowerCase(Locale.ROOT);
			}
			return new Release(version, tag, notes, bool(o, "prerelease"), expectedName, url, size, sha256);
		}
		throw new ParseException(Problem.NO_ASSET);
	}

	/**
	 * The newest usable release from {@code /releases} (a JSON array), skipping drafts, pre-releases unless
	 * {@code includePrereleases}, and releases with no jar.
	 */
	public static Release newest(JsonElement json, boolean includePrereleases) throws ParseException {
		if (json == null || !json.isJsonArray()) throw new ParseException(Problem.MALFORMED);
		JsonArray array = json.getAsJsonArray();
		Release best = null;
		for (JsonElement e : array) {
			Release r;
			try {
				r = parse(e);
			} catch (ParseException skip) {
				continue;
			}
			if (r.prerelease() && !includePrereleases) continue;
			if (best == null || Versions.compare(r.version(), best.version()) > 0) best = r;
		}
		if (best == null) throw new ParseException(Problem.NO_RELEASE);
		return best;
	}

	private static String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
	}

	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
	}
}
