package com.golemaddons.ui;

import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The hub's dimmed background with slowly rising, twinkling particles. Shared by the main hub and category hubs. */
final class HubBackdrop {
	private static final int PARTICLES = 64;

	private final float[][] particles = new float[PARTICLES][];

	HubBackdrop() {
		Random random = new Random(0x601E_A5L);
		for (int i = 0; i < PARTICLES; i++) {
			// x fraction, start phase, speed (canvas units/s), size (units), twinkle phase
			particles[i] = new float[] {random.nextFloat(), random.nextFloat(), 6f + random.nextFloat() * 16f,
					1f + random.nextInt(2), random.nextFloat() * 6.28f};
		}
	}

	/** {@code t} is seconds since the screen opened (for the fade-in). */
	void extract(GuiGraphicsExtractor graphics, int width, int height, float scale, float t) {
		graphics.fill(0, 0, width, height, 0xA008070E);
		graphics.fillGradient(0, 0, width, height / 2, 0x38281A4A, 0x00000000);
		graphics.fillGradient(0, height / 2, width, height, 0x00000000, 0x3010302C);

		float fadeIn = Math.min(1f, t / 0.8f);
		for (int i = 0; i < PARTICLES; i++) {
			float[] p = particles[i];
			float travel = height + 12f;
			float y = height + 6f - ((p[1] * travel + t * p[2] * scale) % travel);
			float x = p[0] * width;
			float twinkle = 0.35f + 0.35f * (float) Math.sin(t * 1.7f + p[4]);
			int color = UiText.withAlpha(i % 3 == 0 ? UiText.ACCENT_2 : UiText.ACCENT, twinkle * fadeIn * 0.7f);
			// Plain fill at scaled size: no per-particle matrix push/pop.
			int size = Math.max(1, Math.round(p[3] * scale));
			int ix = Math.round(x);
			int iy = Math.round(y);
			graphics.fill(ix, iy, ix + size, iy + size, color);
		}
	}
}
