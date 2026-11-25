package com.golemaddons.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnimationCodecTest {
	private static HeldItemAnimation sample() {
		HeldItemAnimation a = new HeldItemAnimation();
		a.x = 0.25f;
		a.y = -0.1f;
		a.z = 0.5f;
		a.rotX = -30f;
		a.rotY = 45f;
		a.rotZ = 180f;
		a.scale = 0.6f;
		a.swingSpeed = 1.5f;
		a.swingTranslation = false;
		a.scaleSwing = false;
		a.ignoreHaste = true;
		a.noEquipReset = true;
		return a;
	}

	private static String wrap(String json) {
		return AnimationCodec.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	private static AnimationCodec.Error errorOf(String text) {
		return assertThrows(AnimationCodec.DecodeException.class, () -> AnimationCodec.decode(text)).error();
	}

	@Test
	void roundTripsEveryValueAndTheName() throws Exception {
		String text = AnimationCodec.encode(sample(), "  Low sword ");
		assertTrue(text.startsWith(AnimationCodec.PREFIX));
		AnimationCodec.Decoded decoded = AnimationCodec.decode("\n" + text + "  ");
		assertEquals(sample(), decoded.animation());
		assertEquals("Low sword", decoded.name());
	}

	@Test
	void missingFieldsUseTheVanillaValues() throws Exception {
		AnimationCodec.Decoded decoded = AnimationCodec.decode(wrap("{\"x\":0.1}"));
		assertEquals(0.1f, decoded.animation().x);
		assertEquals(1f, decoded.animation().scale);
		assertTrue(decoded.animation().swingTranslation);
		assertEquals("", decoded.name());
	}

	@Test
	void rejectsEmptyHugeAndForeignText() {
		assertEquals(AnimationCodec.Error.EMPTY, errorOf(""));
		assertEquals(AnimationCodec.Error.EMPTY, errorOf(null));
		assertEquals(AnimationCodec.Error.TOO_LONG, errorOf(AnimationCodec.PREFIX + "A".repeat(AnimationCodec.MAX_LENGTH)));
		assertEquals(AnimationCodec.Error.NOT_A_PRESET, errorOf("hello"));
		assertEquals(AnimationCodec.Error.NEWER_VERSION, errorOf("GA-ANIM2:abc"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"[1,2]", "{\"x\":\"far\"}", "{\"swingTranslation\":1}", "{\"evil\":true}", "{\"name\":5}", "not json{"})
	void rejectsMalformedPayloads(String json) {
		assertEquals(AnimationCodec.Error.MALFORMED, errorOf(wrap(json)));
	}

	@Test
	void rejectsNonFiniteNumbers() {
		// Gson either refuses to parse NaN (malformed) or parses it (then out of range): rejected both ways.
		AnimationCodec.Error error = errorOf(wrap("{\"x\":NaN}"));
		assertTrue(error == AnimationCodec.Error.MALFORMED || error == AnimationCodec.Error.OUT_OF_RANGE, error.name());
	}

	@Test
	void rejectsBadBase64() {
		assertEquals(AnimationCodec.Error.MALFORMED, errorOf(AnimationCodec.PREFIX + "%%%"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"x\":5}", "{\"scale\":0}", "{\"scale\":1e9}", "{\"rx\":181}", "{\"swingSpeed\":0.1}"})
	void rejectsValuesOutOfRange(String json) {
		assertEquals(AnimationCodec.Error.OUT_OF_RANGE, errorOf(wrap(json)));
	}

	@Test
	void namesAreTrimmedCappedAndStrippedOfControlAndFormattingCodes() {
		assertEquals("ab", AnimationCodec.cleanName("a\u0000\nb"));
		assertEquals("red", AnimationCodec.cleanName("§cred"));
		assertEquals(AnimationCodec.MAX_NAME_LENGTH, AnimationCodec.cleanName("x".repeat(100)).length());
		assertEquals("", AnimationCodec.cleanName(null));
	}

	@Test
	void sanitizeClampsAndReplacesNonFiniteValues() {
		HeldItemAnimation a = new HeldItemAnimation();
		a.x = 9f; // above MAX_X
		a.rotY = Float.NaN;
		a.scale = Float.POSITIVE_INFINITY;
		a.swingSpeed = -2f;
		assertFalse(a.isValid());
		a.sanitize();
		assertTrue(a.isValid());
		assertEquals(HeldItemAnimation.MAX_X, a.x);
		assertEquals(0f, a.rotY);
		assertEquals(1f, a.scale);
		assertEquals(HeldItemAnimation.MIN_SWING_SPEED, a.swingSpeed);
	}
}
