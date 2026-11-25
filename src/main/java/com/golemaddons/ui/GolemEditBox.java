package com.golemaddons.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Vanilla's text box with a GolemAddons frame instead of the vanilla texture (the swap is done by
 * {@code EditBoxMixin}, only for this class). Typing, selection and the cursor are vanilla's.
 */
final class GolemEditBox extends EditBox {
	private final long[] lastFrame = new long[1];
	private float focus;

	GolemEditBox(Font font, int x, int y, int w, int h, Component message) {
		super(font, x, y, w, h, message);
	}

	/** Called in place of the vanilla background. */
	void extractFrame(GuiGraphicsExtractor graphics) {
		focus = UiAnim.approach(focus, isFocused() ? 1f : isHovered() ? 0.4f : 0f, UiAnim.tick(lastFrame), 14f);
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		float a = isActive() ? 1f : 0.5f;
		graphics.fill(x, y, x + w, y + h, UiText.withAlpha(0xE00A0912, a));
		graphics.outline(x, y, w, h, UiText.withAlpha(UiText.lerpColor(0x40FFFFFF, UiText.ACCENT, focus), a));
		if (focus > 0.02f) {
			int bar = Math.round(w * focus);
			graphics.fill(x + (w - bar) / 2, y + h - 1, x + (w + bar) / 2, y + h, UiText.withAlpha(UiText.ACCENT_2, a * focus));
		}
	}
}
