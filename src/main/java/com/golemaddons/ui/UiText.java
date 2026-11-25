package com.golemaddons.ui;

import com.golemaddons.autosell.RuleValidator;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Shared colors and user-facing messages. */
final class UiText {
	static final int WHITE = 0xFFFFFFFF;
	static final int GRAY = 0xFFA0A0A0;
	static final int DARK_GRAY = 0xFF6A6A72;
	static final int GREEN = 0xFF55FF55;
	static final int RED = 0xFFFF5555;
	static final int YELLOW = 0xFFFFFF55;
	/** GolemAddons accent (violet) and secondary (teal). */
	static final int ACCENT = 0xFF9D7BFF;
	static final int ACCENT_2 = 0xFF5CE1C6;
	static final int GOLD = 0xFFE0B04A;

	private UiText() {
	}

	static Component problem(RuleValidator.Problem problem, String detail) {
		return Component.translatable("golemaddons.problem." + problem.name().toLowerCase(Locale.ROOT), detail);
	}

	static Component problem(RuleValidator.Result result) {
		return problem(result.problem(), result.detail());
	}

	/** Replaces the alpha channel of an ARGB color, alpha in 0..1. */
	static int withAlpha(int argb, float alpha) {
		int a = Math.round(Math.clamp(alpha, 0f, 1f) * ((argb >>> 24) & 0xFF));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	/** Linear blend of two ARGB colors, t in 0..1. */
	static int lerpColor(int from, int to, float t) {
		t = Math.clamp(t, 0f, 1f);
		int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
