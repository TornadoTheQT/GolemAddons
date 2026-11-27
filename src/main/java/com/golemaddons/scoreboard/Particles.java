package com.golemaddons.scoreboard;

import java.util.Random;

/** Fixed random numbers for the board's particles: positions come from time, so nothing is updated per tick. */
final class Particles {
	static final int MAX = 120;
	/** Per particle: x fraction, phase, speed, size, wobble phase, hue mix. */
	static final float[][] SEEDS = new float[MAX][6];

	static {
		Random r = new Random(0x5C0_2EB0A2DL);
		for (float[] s : SEEDS) {
			for (int i = 0; i < s.length; i++) s[i] = r.nextFloat();
		}
	}

	private Particles() {
	}

	/** How many to draw for a board of this size, theme base density and user density (percent). */
	static int count(int w, int h, float perArea, int densityPercent) {
		return Math.min(MAX, Math.round(w * h * perArea * densityPercent / 100f));
	}

	/** Positive modulo. */
	static float wrap(float v, float m) {
		float r = v % m;
		return r < 0 ? r + m : r;
	}
}
