package com.golemaddons.ui;

import net.minecraft.util.Util;

/** Small timing helpers for animated widgets: entrance fades and frame-rate independent easing. */
final class UiAnim {
	static final long APPEAR_MS = 280;

	private UiAnim() {
	}

	static float easeOutCubic(float t) {
		float u = 1f - Math.clamp(t, 0f, 1f);
		return 1f - u * u * u;
	}

	/** 0..1 entrance progress of something that starts appearing at {@code at} (ms, {@link Util#getMillis()}). */
	static float appear(long at) {
		return easeOutCubic((Util.getMillis() - at) / (float) APPEAR_MS);
	}

	/** Moves {@code current} towards {@code target}; {@code rate} is roughly "per second", independent of frame rate. */
	static float approach(float current, float target, float dtSeconds, float rate) {
		return current + (target - current) * (1f - (float) Math.exp(-rate * dtSeconds));
	}

	/** Seconds since {@code last[0]} (and updates it), capped so a hitch doesn't make things jump. */
	static float tick(long[] last) {
		long now = Util.getMillis();
		float dt = last[0] == 0 ? 0f : Math.min(0.1f, (now - last[0]) / 1000f);
		last[0] = now;
		return dt;
	}
}
