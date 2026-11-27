package com.golemaddons.scoreboard;

import com.golemaddons.config.GolemConfig;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Draws the custom scoreboard: a themed, optionally animated background in a rounded box, an optional border, and
 * the header, lines and footer on top. Everything is plain fills and text, sized to the content, so it is cheap
 * (a few dozen quads plus the particles) and needs no textures.
 */
public final class ScoreboardRenderer {
	/** What to draw. {@code widths} are the lines' widths, measured once when the model is built. */
	public record Model(Component header, List<Component> lines, int[] widths, Component footer) {
		public static Model of(Font font, Component header, List<Component> lines, Component footer) {
			int[] w = new int[lines.size()];
			for (int i = 0; i < w.length; i++) w[i] = font.width(lines.get(i));
			return new Model(header, lines, w, footer);
		}
	}

	private static final int MIN_WIDTH = 64;
	private static final int MAX_WIDTH = 260;
	private static final String GLYPHS = "0123456789ABCDEFGHJKLMNPRSTUVXZ$#%&*+=<>";

	private ScoreboardRenderer() {
	}

	private static int lineHeight(GolemConfig.Scoreboard c) {
		return 9 + c.lineSpacing;
	}

	/** Unscaled width and height. */
	public static int[] size(Font font, Model m, GolemConfig.Scoreboard c) {
		int content = 0;
		for (int w : m.widths()) content = Math.max(content, w);
		if (m.header() != null) content = Math.max(content, font.width(m.header()));
		if (m.footer() != null) content = Math.max(content, font.width(m.footer()));
		int w = Math.clamp(content + 2 * c.padding + 2, MIN_WIDTH, MAX_WIDTH);
		int lh = lineHeight(c);
		int h = 2 * c.padding + m.lines().size() * lh;
		if (m.header() != null) h += lh + 3;
		if (m.footer() != null) h += lh + 3;
		return new int[] {w, Math.max(h, 2 * c.padding + 9)};
	}

	/** Draws at (x, y) in GUI pixels. {@code seconds} drives the animation. */
	public static void draw(GuiGraphicsExtractor g, Font font, int x, int y, float scale, Model m, GolemConfig.Scoreboard c, float seconds) {
		int[] size = size(font, m, c);
		int w = size[0];
		int h = size[1];
		float t = seconds * c.animationSpeed / 100f;
		ScoreboardTheme theme = ScoreboardTheme.of(c.theme);
		GolemConfig.Scoreboard.ThemeColors colors = c.colors();
		RoundedShape shape = RoundedShape.of(w, h, c.cornerRadius, theme == ScoreboardTheme.SOLID ? 1 << 12 : 4);

		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		float opacity = c.opacity / 100f;
		float dim = c.textBacking / 100f;
		if (opacity > 0f) {
			background(g, shape, theme, colors, t, opacity, dim);
			if (c.particles && c.particleDensity > 0) particles(g, font, shape, theme, colors, t, opacity * (1f - dim * 0.6f), c.particleDensity);
		}
		border(g, shape, c, colors, t);
		text(g, font, m, c, w, colors);
		g.pose().popMatrix();
	}

	// ---- Background ------------------------------------------------------------------------------------------

	private static void background(GuiGraphicsExtractor g, RoundedShape s, ScoreboardTheme theme, GolemConfig.Scoreboard.ThemeColors k,
			float t, float opacity, float dim) {
		int w = s.w;
		int h = s.h;
		for (int i = 0; i < s.columns(); i++) {
			int a = s.x0[i];
			int b = s.x1[i];
			int top = s.top[i];
			int bottom = s.bottom[i];
			if (bottom <= top) continue;
			float fx = (a + b) * 0.5f / w;
			int mid = (top + bottom) / 2;
			int c0;
			int c1;
			int c2;
			switch (theme) {
				case SOLID -> c0 = c1 = c2 = k.primary;
				case GRADIENT -> {
					float shift = (float) Math.sin(t * 0.6f) * 0.25f;
					c0 = lerp(k.primary, k.secondary, fx * 0.5f + shift + 0.1f);
					c1 = lerp(k.primary, k.secondary, fx * 0.5f + 0.25f + shift);
					c2 = lerp(k.primary, k.secondary, fx * 0.5f + 0.5f + shift);
				}
				case AURORA -> {
					c0 = darken(k.primary, 0.25f);
					c1 = k.primary;
					c2 = lerp(k.primary, k.secondary, 0.12f);
				}
				case MATRIX -> {
					c0 = k.primary;
					c1 = lerp(k.primary, k.secondary, 0.5f);
					c2 = k.secondary;
				}
				case FIRE -> {
					c0 = k.primary;
					c1 = lerp(k.primary, k.secondary, 0.45f);
					c2 = k.secondary;
				}
				default -> { // OCEAN
					c0 = k.primary;
					c1 = lerp(k.primary, k.secondary, 0.55f);
					c2 = k.secondary;
				}
			}
			c0 = shade(c0, opacity, dim);
			c1 = shade(c1, opacity, dim);
			c2 = shade(c2, opacity, dim);
			g.fillGradient(a, top, b, mid, c0, c1);
			g.fillGradient(a, mid, b, bottom, c1, c2);

			// Animated light on top of the base.
			float glow = 1f - dim * 0.5f;
			switch (theme) {
				case AURORA -> {
					float x = (a + b) * 0.5f;
					float wave = 0.5f + 0.5f * (float) (Math.sin(x * 0.085f + t * 0.9f) * Math.sin(x * 0.031f - t * 0.55f + 1.3f));
					float hue = 0.5f + 0.5f * (float) Math.sin(x * 0.022f + t * 0.35f);
					int curtain = lerp(k.secondary, k.accent, hue);
					float alpha = wave * wave * 0.65f * opacity * glow;
					int peak = top + Math.round(h * (0.42f + 0.12f * (float) Math.sin(x * 0.05f + t * 0.7f)));
					int start = Math.max(top, top + h / 12);
					int end = Math.min(bottom, peak + h / 4);
					peak = Math.clamp(peak, start, end);
					g.fillGradient(a, start, b, peak, withAlpha(curtain, 0f), withAlpha(curtain, alpha));
					g.fillGradient(a, peak, b, end, withAlpha(curtain, alpha), withAlpha(curtain, 0f));
				}
				case FIRE -> {
					float x = (a + b) * 0.5f;
					float n = 0.5f + 0.25f * (float) Math.sin(x * 0.31f + t * 5.1f) + 0.25f * (float) Math.sin(x * 0.12f - t * 3.3f);
					int flame = Math.round(h * (0.25f + 0.3f * n));
					int from = Math.max(top, bottom - flame);
					g.fillGradient(a, from, b, bottom, withAlpha(k.accent, 0f), withAlpha(k.accent, (0.35f + 0.35f * n) * opacity * glow));
				}
				case OCEAN -> {
					float x = (a + b) * 0.5f;
					double ray = Math.max(0, Math.sin(x * 0.065f + t * 0.5f + Math.sin(x * 0.021f + t * 0.2f) * 2));
					float alpha = (float) (ray * ray * ray) * 0.32f * opacity * glow;
					if (alpha > 0.01f) g.fillGradient(a, top, b, Math.min(bottom, top + h * 4 / 5), withAlpha(k.accent, alpha), withAlpha(k.accent, 0f));
					int ripple = top + 1 + Math.round(1.5f + 1.5f * (float) Math.sin(x * 0.2f + t * 2.2f));
					if (ripple < bottom) g.fill(a, top, b, ripple, withAlpha(k.accent, 0.22f * opacity * glow));
				}
				case MATRIX -> {
					// faint scanline sweep
					float sweep = Particles.wrap(t * 18f, h + 20) - 10;
					int sy = top + Math.round(sweep);
					if (sy > top && sy + 6 < bottom) g.fillGradient(a, sy, b, sy + 6, withAlpha(k.accent, 0f), withAlpha(k.accent, 0.08f * opacity * glow));
				}
				default -> {
				}
			}
		}
	}

	// ---- Particles -------------------------------------------------------------------------------------------

	private static void particles(GuiGraphicsExtractor g, Font font, RoundedShape s, ScoreboardTheme theme, GolemConfig.Scoreboard.ThemeColors k,
			float t, float alpha, int density) {
		int w = s.w;
		int h = s.h;
		switch (theme) {
			case MATRIX -> {
				int columns = Math.min(Particles.MAX, Math.round(w / 7f * density / 100f));
				g.pose().pushMatrix();
				g.pose().scale(0.75f, 0.75f);
				float inv = 1f / 0.75f;
				for (int i = 0; i < columns; i++) {
					float[] p = Particles.SEEDS[i];
					float cx = 2 + (i + p[0] * 0.6f) * (w - 6) / Math.max(1, columns);
					float speed = 18f + p[2] * 30f;
					int trail = 3 + (int) (p[3] * 4);
					float travel = h + trail * 7f;
					float head = Particles.wrap(p[1] * travel + t * speed, travel) - 4;
					for (int j = 0; j < trail; j++) {
						float gy = head - j * 7f;
						if (!s.contains(cx, gy, 5, 7)) continue;
						int cell = Math.floorMod((int) Math.floor((gy + 1000) / 7f) * 31 + i * 17 + (int) (t * 2 + p[4] * 9) * (j == 0 ? 1 : 0), GLYPHS.length());
						float fade = j == 0 ? 0.75f : 0.5f * (1f - j / (float) trail);
						int color = j == 0 ? lerp(k.particle, 0xFFFFFFFF, 0.5f) : k.particle;
						g.text(font, String.valueOf(GLYPHS.charAt(cell)), Math.round(cx * inv), Math.round(gy * inv), withAlpha(color, fade * alpha), false);
					}
				}
				g.pose().popMatrix();
			}
			case FIRE -> {
				int n = Particles.count(w, h, 1 / 140f, density);
				for (int i = 0; i < n; i++) {
					float[] p = Particles.SEEDS[i];
					float life = Particles.wrap(p[1] + t * (0.25f + p[2] * 0.35f), 1f);
					float py = h - 2 - life * h;
					float px = p[0] * w + (float) Math.sin(t * 2.5f + p[4] * 6.28f) * 3f * life;
					int size = p[3] > 0.7f ? 2 : 1;
					if (!s.contains(px, py, size, size)) continue;
					int color = lerp(k.particle, k.accent, life);
					fillDot(g, px, py, size, withAlpha(color, alpha * (1f - life) * 0.95f));
				}
			}
			case OCEAN -> {
				int n = Particles.count(w, h, 1 / 220f, density);
				for (int i = 0; i < n; i++) {
					float[] p = Particles.SEEDS[i];
					float life = Particles.wrap(p[1] + t * (0.12f + p[2] * 0.18f), 1f);
					float py = h - 3 - life * (h - 2);
					float px = p[0] * (w - 4) + 1 + (float) Math.sin(t * 1.6f + p[4] * 6.28f + life * 4f) * 2.5f;
					int size = p[3] > 0.6f ? 3 : 2;
					if (!s.contains(px, py, size, size)) continue;
					int color = withAlpha(k.particle, alpha * (0.35f + 0.4f * life));
					int ix = Math.round(px);
					int iy = Math.round(py);
					if (size == 3) {
						// A tiny ring: four sides, hollow middle, plus a highlight.
						g.fill(ix + 1, iy, ix + 2, iy + 1, color);
						g.fill(ix, iy + 1, ix + 1, iy + 2, color);
						g.fill(ix + 2, iy + 1, ix + 3, iy + 2, color);
						g.fill(ix + 1, iy + 2, ix + 2, iy + 3, color);
					} else {
						g.fill(ix, iy, ix + 2, iy + 2, color);
					}
				}
			}
			default -> { // stars / sparkles
				float perArea = theme == ScoreboardTheme.AURORA ? 1 / 170f : 1 / 320f;
				int n = Particles.count(w, h, perArea, density);
				for (int i = 0; i < n; i++) {
					float[] p = Particles.SEEDS[i];
					float py = Particles.wrap(p[1] * h - t * (1.5f + p[2] * 4f), h);
					float px = p[0] * w;
					int size = p[3] > 0.85f ? 2 : 1;
					if (!s.contains(px, py, size, size)) continue;
					float twinkle = 0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(t * (1.5f + p[5] * 2f) + p[4] * 6.28f));
					int color = theme == ScoreboardTheme.AURORA && p[5] > 0.75f ? k.secondary : k.particle;
					fillDot(g, px, py, size, withAlpha(color, alpha * twinkle * 0.8f));
				}
			}
		}
	}

	private static void fillDot(GuiGraphicsExtractor g, float x, float y, int size, int color) {
		int ix = Math.round(x);
		int iy = Math.round(y);
		g.fill(ix, iy, ix + size, iy + size, color);
	}

	// ---- Border ----------------------------------------------------------------------------------------------

	private static void border(GuiGraphicsExtractor g, RoundedShape s, GolemConfig.Scoreboard c, GolemConfig.Scoreboard.ThemeColors k, float t) {
		int bw = c.borderWidth;
		if (bw <= 0 || "NONE".equals(c.borderStyle)) return;
		boolean themed = "THEME".equals(c.borderStyle);
		int w = s.w;
		int h = s.h;
		int n = s.columns();
		for (int i = 0; i < n; i++) {
			int a = s.x0[i];
			int b = s.x1[i];
			int top = s.top[i];
			int bottom = s.bottom[i];
			// In the corners, reach down to the next column outwards so the curve has no gaps.
			int outer = i < s.r ? (i == 0 ? s.r : s.top[i - 1]) : i >= n - s.r ? (i == n - 1 ? s.r : s.top[i + 1]) : top + bw;
			int depth = Math.max(bw, outer - top);
			float fx = (a + b) * 0.5f / w;
			int topColor = themed ? themeBorder(k, fx * 0.5f, t) : c.borderColor;
			int bottomColor = themed ? themeBorder(k, 1f - fx * 0.5f, t) : c.borderColor;
			g.fill(a, top, b, Math.min(bottom, top + depth), topColor);
			g.fill(a, Math.max(top, bottom - depth), b, bottom, bottomColor);
		}
		int r = s.r;
		int y0 = r;
		int y1 = h - r;
		if (y1 > y0) {
			int lt = themed ? themeBorder(k, 0f, t) : c.borderColor;
			int lb = themed ? themeBorder(k, 1f, t) : c.borderColor;
			int rt = themed ? themeBorder(k, 0.5f, t) : c.borderColor;
			int rb = themed ? themeBorder(k, 0.5f, t) : c.borderColor;
			g.fillGradient(0, y0, bw, y1, lt, lb);
			g.fillGradient(w - bw, y0, w, y1, rt, rb);
		}
	}

	/** A colour flowing around the border: position p (0..1 around the box) and time. */
	private static int themeBorder(GolemConfig.Scoreboard.ThemeColors k, float p, float t) {
		float m = 0.5f + 0.5f * (float) Math.sin(p * Math.PI * 2 + t * 1.8f);
		return opaque(lerp(k.accent, k.secondary, m), 0.9f);
	}

	// ---- Text ------------------------------------------------------------------------------------------------

	private static void text(GuiGraphicsExtractor g, Font font, Model m, GolemConfig.Scoreboard c, int w, GolemConfig.Scoreboard.ThemeColors k) {
		int pad = c.padding;
		int lh = lineHeight(c);
		int y = pad + 1;
		int sep = withAlpha(k.accent, 0.45f);
		if (m.header() != null) {
			g.text(font, m.header(), (w - font.width(m.header())) / 2, y, 0xFFFFFFFF, c.textShadow);
			y += lh + 1;
			g.fill(pad, y, w - pad, y + 1, sep);
			y += 2;
		}
		int left = pad + 1;
		int right = w - pad - 1;
		for (int i = 0; i < m.lines().size(); i++) {
			int lw = m.widths()[i];
			int x = switch (c.align) {
				case "CENTER" -> (w - lw) / 2;
				case "RIGHT" -> right - lw;
				default -> left;
			};
			g.text(font, m.lines().get(i), x, y, 0xFFFFFFFF, c.textShadow);
			y += lh;
		}
		if (m.footer() != null) {
			y += 1;
			g.fill(pad, y - 1, w - pad, y, sep);
			y += 2;
			g.text(font, m.footer(), (w - font.width(m.footer())) / 2, y, 0xFFFFFFFF, c.textShadow);
		}
	}

	// ---- Colour helpers --------------------------------------------------------------------------------------

	static int lerp(int a, int b, float t) {
		t = Math.clamp(t, 0f, 1f);
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8)
				| Math.round(ab + (bb - ab) * t);
	}

	static int withAlpha(int argb, float alpha) {
		int a = Math.round(Math.clamp(alpha, 0f, 1f) * 255f);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static int opaque(int argb, float alpha) {
		return withAlpha(argb, alpha * (((argb >>> 24) & 0xFF) / 255f));
	}

	private static int darken(int argb, float amount) {
		return lerp(argb, argb & 0xFF000000, amount);
	}

	/** Applies the background opacity (times the colour's own alpha) and the legibility darkening. */
	private static int shade(int argb, float opacity, float dim) {
		int c = darken(argb, dim);
		return withAlpha(c, opacity * (((argb >>> 24) & 0xFF) / 255f));
	}
}
