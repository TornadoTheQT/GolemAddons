package com.golemaddons.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

/**
 * Turns an animation into a short shareable string and back: {@code GA-ANIM1:} followed by Base64 (URL-safe) of a
 * small JSON object. Decoding treats the text as untrusted: size-capped, strict schema, every number checked.
 */
public final class AnimationCodec {
	public static final String PREFIX = "GA-ANIM1:";
	/** Far above any real export (~200 chars); stops a huge clipboard from being decoded at all. */
	public static final int MAX_LENGTH = 4096;
	public static final int MAX_NAME_LENGTH = 32;

	private static final Set<String> KEYS = Set.of("name", "x", "y", "z", "rx", "ry", "rz", "scale", "swingSpeed", "swingTranslation",
			"scaleSwing", "ignoreHaste", "noEquipReset");

	/** A decoded string: the animation and the name it was shared under ("" if none). */
	public record Decoded(HeldItemAnimation animation, String name) {
	}

	/** Why a string could not be decoded; each maps to a message in the UI. */
	public enum Error {
		EMPTY, TOO_LONG, NOT_A_PRESET, NEWER_VERSION, MALFORMED, OUT_OF_RANGE
	}

	/** Thrown by {@link #decode}; carries which problem it was. */
	public static final class DecodeException extends Exception {
		private final Error error;

		DecodeException(Error error) {
			super(error.name(), null, false, false);
			this.error = error;
		}

		public Error error() {
			return error;
		}
	}

	private AnimationCodec() {
	}

	public static String encode(HeldItemAnimation a, String name) {
		JsonObject o = new JsonObject();
		String clean = cleanName(name);
		if (!clean.isEmpty()) o.addProperty("name", clean);
		o.addProperty("x", a.x);
		o.addProperty("y", a.y);
		o.addProperty("z", a.z);
		o.addProperty("rx", a.rotX);
		o.addProperty("ry", a.rotY);
		o.addProperty("rz", a.rotZ);
		o.addProperty("scale", a.scale);
		o.addProperty("swingSpeed", a.swingSpeed);
		o.addProperty("swingTranslation", a.swingTranslation);
		o.addProperty("scaleSwing", a.scaleSwing);
		o.addProperty("ignoreHaste", a.ignoreHaste);
		o.addProperty("noEquipReset", a.noEquipReset);
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(o.toString().getBytes(StandardCharsets.UTF_8));
	}

	public static Decoded decode(String text) throws DecodeException {
		if (text == null || text.isBlank()) throw new DecodeException(Error.EMPTY);
		if (text.length() > MAX_LENGTH) throw new DecodeException(Error.TOO_LONG);
		text = text.strip();
		if (!text.startsWith(PREFIX)) {
			// "GA-ANIM2:" and later: a preset from a newer version of the mod.
			throw new DecodeException(text.matches("GA-ANIM\\d+:.*") ? Error.NEWER_VERSION : Error.NOT_A_PRESET);
		}
		JsonObject o;
		try {
			byte[] bytes = Base64.getUrlDecoder().decode(text.substring(PREFIX.length()));
			JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) throw new DecodeException(Error.MALFORMED);
			o = root.getAsJsonObject();
		} catch (IllegalArgumentException | JsonParseException e) {
			throw new DecodeException(Error.MALFORMED);
		}
		for (String key : o.keySet()) {
			if (!KEYS.contains(key)) throw new DecodeException(Error.MALFORMED);
		}
		HeldItemAnimation a = new HeldItemAnimation();
		a.x = number(o, "x", 0f);
		a.y = number(o, "y", 0f);
		a.z = number(o, "z", 0f);
		a.rotX = number(o, "rx", 0f);
		a.rotY = number(o, "ry", 0f);
		a.rotZ = number(o, "rz", 0f);
		a.scale = number(o, "scale", 1f);
		a.swingSpeed = number(o, "swingSpeed", 1f);
		a.swingTranslation = bool(o, "swingTranslation", true);
		a.scaleSwing = bool(o, "scaleSwing", true);
		a.ignoreHaste = bool(o, "ignoreHaste", false);
		a.noEquipReset = bool(o, "noEquipReset", false);
		if (!a.isValid()) throw new DecodeException(Error.OUT_OF_RANGE);
		String name = "";
		if (o.has("name")) {
			JsonElement n = o.get("name");
			if (!n.isJsonPrimitive() || !n.getAsJsonPrimitive().isString()) throw new DecodeException(Error.MALFORMED);
			name = cleanName(n.getAsString());
		}
		return new Decoded(a, name);
	}

	/** Printable characters only, trimmed, at most {@link #MAX_NAME_LENGTH} long. Null becomes "". */
	public static String cleanName(String name) {
		if (name == null) return "";
		StringBuilder out = new StringBuilder();
		int[] cps = name.codePoints().toArray();
		int kept = 0;
		for (int i = 0; i < cps.length && kept < MAX_NAME_LENGTH; i++) {
			int cp = cps[i];
			if (cp == 0xA7) {
				i++; // a formatting code: drop the section sign and the code letter after it
			} else if (!Character.isISOControl(cp) && Character.isDefined(cp)) {
				out.appendCodePoint(cp);
				kept++;
			}
		}
		return out.toString().strip();
	}

	private static float number(JsonObject o, String key, float fallback) throws DecodeException {
		if (!o.has(key)) return fallback;
		JsonElement e = o.get(key);
		if (!e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber()) throw new DecodeException(Error.MALFORMED);
		double d = e.getAsDouble();
		if (!Double.isFinite(d)) throw new DecodeException(Error.OUT_OF_RANGE);
		return (float) d;
	}

	private static boolean bool(JsonObject o, String key, boolean fallback) throws DecodeException {
		if (!o.has(key)) return fallback;
		JsonElement e = o.get(key);
		if (!e.isJsonPrimitive() || !((JsonPrimitive) e).isBoolean()) throw new DecodeException(Error.MALFORMED);
		return e.getAsBoolean();
	}
}
