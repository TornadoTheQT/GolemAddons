package com.golemaddons.ui;

import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * The scene behind the held-item preview: a dusk sky, a glowing horizon, a perspective grid floor that drifts
 * towards the viewer, slow motes of light and a soft vignette. Drawn with plain fills, in the hub's colors.
 */
final class PreviewBackdrop {
	private static final int MOTES = 26;
	private static final int GRID_COLUMNS = 14;
	private static final float GRID_SPEED = 0.35f;

	private final float[][] motes = new float[MOTES][];

	PreviewBackdrop() {
		Random random = new Random(0x5EED_A11L);
		for (int i = 0; i < MOTES; i++) {
			// x, y phase, speed, twinkle phase
			motes[i] = new float[] {random.nextFloat(), random.nextFloat(), 0.02f + random.nextFloat() * 0.05f, random.nextFloat() * 6.28f};
		}
	}

	void extract(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		int w = x1 - x0;
		int h = y1 - y0;
		if (w <= 4 || h <= 4) return;
		float t = (Util.getMillis() % 3_600_000L) / 1000f;
		int horizon = y0 + Math.round(h * 0.58f);

		// Sky: deep violet at the top to a warm violet-teal haze at the horizon.
		g.fillGradient(x0, y0, x1, horizon, 0xFF0C0A1C, 0xFF2B2150);
		// Floor: dark, fading into the distance.
		g.fillGradient(x0, horizon, x1, y1, 0xFF120F22, 0xFF07060E);

		// Horizon glow, gently breathing.
		float pulse = 0.75f + 0.25f * (float) Math.sin(t * 1.3f);
		g.fillGradient(x0, horizon - Math.max(6, h / 7), x1, horizon, 0x00000000, UiText.withAlpha(UiText.ACCENT, 0.35f * pulse));
		g.fillGradient(x0, horizon, x1, horizon + Math.max(4, h / 12), UiText.withAlpha(UiText.ACCENT_2, 0.22f * pulse), 0x00000000);
		g.fill(x0, horizon, x1, horizon + 1, UiText.withAlpha(0xFFE9DFFF, 0.55f * pulse));

		// Grid floor: rows get further apart towards the viewer and scroll forward; columns converge on the horizon.
		int cx = (x0 + x1) / 2;
		int floorH = y1 - horizon;
		float scroll = (t * GRID_SPEED) % 1f;
		for (int i = 0; i < 9; i++) {
			float depth = (i + scroll) / 9f; // 0 at the horizon, 1 at the viewer
			int y = horizon + Math.round(depth * depth * floorH);
			if (y <= horizon || y >= y1) continue;
			g.fill(x0, y, x1, y + 1, UiText.withAlpha(UiText.ACCENT, 0.10f + 0.35f * depth));
		}
		for (int c = -GRID_COLUMNS / 2; c <= GRID_COLUMNS / 2; c++) {
			float spread = c / (float) (GRID_COLUMNS / 2); // -1..1 at the viewer
			for (int y = horizon + 1; y < y1; y += 2) {
				float depth = (y - horizon) / (float) floorH;
				int x = cx + Math.round(spread * depth * w * 0.95f);
				if (x < x0 || x >= x1) continue;
				g.fill(x, y, x + 1, y + 2, UiText.withAlpha(UiText.ACCENT_2, 0.06f + 0.22f * depth));
			}
		}

		// Motes of light drifting up through the sky.
		for (float[] m : motes) {
			float travel = (m[1] + t * m[2]) % 1f;
			int mx = x0 + Math.round(m[0] * (w - 2));
			int my = horizon - Math.round(travel * (horizon - y0));
			float twinkle = 0.35f + 0.35f * (float) Math.sin(t * 2.1f + m[3]);
			float fade = Math.min(1f, Math.min(travel * 5f, (1f - travel) * 3f));
			g.fill(mx, my, mx + 1, my + 1, UiText.withAlpha(m[3] > 3.14f ? UiText.ACCENT_2 : 0xFFF0E6FF, twinkle * fade));
		}

		// Vignette and a thin accent frame.
		int edge = Math.max(4, Math.min(w, h) / 6);
		g.fillGradient(x0, y0, x1, y0 + edge, 0x70000000, 0x00000000);
		g.fillGradient(x0, y1 - edge, x1, y1, 0x00000000, 0x80000000);
		for (int i = 0; i < edge; i += 2) {
			int alpha = Math.round(0x60 * (1f - i / (float) edge));
			g.fill(x0 + i, y0, x0 + i + 2, y1, alpha << 24);
			g.fill(x1 - i - 2, y0, x1 - i, y1, alpha << 24);
		}
		g.outline(x0 - 1, y0 - 1, w + 2, h + 2, UiText.withAlpha(UiText.ACCENT, 0.45f));
	}
}
