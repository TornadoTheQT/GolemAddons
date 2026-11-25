package com.golemaddons.animation;

/**
 * How the first-person held item is placed and swung, modelled on OdinLegacy's Animations module (see
 * {@link HeldItemTransform}). All-default values give exactly the vanilla look.
 *
 * <p>Stored in the config and in presets; {@link #sanitize()} clamps every value so a hand-edited file or pasted
 * preset can never produce a NaN, an infinite scale or an item flung off screen.
 */
public final class HeldItemAnimation {
	public static final float MIN_X = -2.5f;
	public static final float MAX_X = 1.5f;
	public static final float MIN_Y = -1.5f;
	public static final float MAX_Y = 1.5f;
	public static final float MIN_Z = -1.5f;
	public static final float MAX_Z = 3f;
	public static final float MIN_ROTATION = -180f;
	public static final float MAX_ROTATION = 180f;
	/** Size multiplier; Odin's "Size" setting is its natural log (-1.5..1.5). */
	public static final float MIN_SCALE = 0.1f;
	public static final float MAX_SCALE = 5f;
	public static final float MIN_SWING_SPEED = 0.15f;
	public static final float MAX_SWING_SPEED = 3f;

	/** Position, as a fraction of vanilla's resting position: 0 is vanilla, 1 is twice as far right / down / forward. */
	public float x = 0f;
	public float y = 0f;
	public float z = 0f;
	/** Pitch, yaw and roll in degrees, applied before the swing so the swing follows the rotated item. */
	public float rotX = 0f;
	public float rotY = 0f;
	public float rotZ = 0f;
	public float scale = 1f;
	/** Swing speed multiplier: 2 plays the swing in half the time. Changes only the local swing animation. */
	public float swingSpeed = 1f;
	/** True: the full swing. False ("no swing"): the item only turns, without moving forward. */
	public boolean swingTranslation = true;
	/** The swing's movement grows and shrinks with the item's size. */
	public boolean scaleSwing = true;
	/** The swing speed ignores Haste and Mining Fatigue. */
	public boolean ignoreHaste = false;
	/** No dip when switching items: the new item is simply there. */
	public boolean noEquipReset = false;

	public HeldItemAnimation copy() {
		HeldItemAnimation c = new HeldItemAnimation();
		c.set(this);
		return c;
	}

	public void set(HeldItemAnimation o) {
		x = o.x;
		y = o.y;
		z = o.z;
		rotX = o.rotX;
		rotY = o.rotY;
		rotZ = o.rotZ;
		scale = o.scale;
		swingSpeed = o.swingSpeed;
		swingTranslation = o.swingTranslation;
		scaleSwing = o.scaleSwing;
		ignoreHaste = o.ignoreHaste;
		noEquipReset = o.noEquipReset;
	}

	/** True when this changes nothing, so the renderer can skip its work entirely. */
	public boolean isVanilla() {
		return x == 0f && y == 0f && z == 0f && rotX == 0f && rotY == 0f && rotZ == 0f && scale == 1f
				&& swingSpeed == 1f && swingTranslation && !ignoreHaste && !noEquipReset;
	}

	/** True when every value is a finite number inside its range. */
	public boolean isValid() {
		return in(x, MIN_X, MAX_X) && in(y, MIN_Y, MAX_Y) && in(z, MIN_Z, MAX_Z)
				&& in(rotX, MIN_ROTATION, MAX_ROTATION) && in(rotY, MIN_ROTATION, MAX_ROTATION) && in(rotZ, MIN_ROTATION, MAX_ROTATION)
				&& in(scale, MIN_SCALE, MAX_SCALE) && in(swingSpeed, MIN_SWING_SPEED, MAX_SWING_SPEED);
	}

	public void sanitize() {
		x = clamp(x, MIN_X, MAX_X, 0f);
		y = clamp(y, MIN_Y, MAX_Y, 0f);
		z = clamp(z, MIN_Z, MAX_Z, 0f);
		rotX = clamp(rotX, MIN_ROTATION, MAX_ROTATION, 0f);
		rotY = clamp(rotY, MIN_ROTATION, MAX_ROTATION, 0f);
		rotZ = clamp(rotZ, MIN_ROTATION, MAX_ROTATION, 0f);
		scale = clamp(scale, MIN_SCALE, MAX_SCALE, 1f);
		swingSpeed = clamp(swingSpeed, MIN_SWING_SPEED, MAX_SWING_SPEED, 1f);
	}

	private static boolean in(float v, float lo, float hi) {
		return Float.isFinite(v) && v >= lo && v <= hi;
	}

	private static float clamp(float v, float lo, float hi, float fallback) {
		return Float.isFinite(v) ? Math.clamp(v, lo, hi) : fallback;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof HeldItemAnimation a && x == a.x && y == a.y && z == a.z && rotX == a.rotX && rotY == a.rotY
				&& rotZ == a.rotZ && scale == a.scale && swingSpeed == a.swingSpeed && swingTranslation == a.swingTranslation
				&& scaleSwing == a.scaleSwing && ignoreHaste == a.ignoreHaste && noEquipReset == a.noEquipReset;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(x, y, z, rotX, rotY, rotZ, scale, swingSpeed, swingTranslation, scaleSwing, ignoreHaste, noEquipReset);
	}
}
