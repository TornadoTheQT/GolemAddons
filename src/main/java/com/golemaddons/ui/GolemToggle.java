package com.golemaddons.ui;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** A label with an animated on/off switch on the right. The whole row is the click target. */
final class GolemToggle extends AbstractButton {
	private static final int TRACK_W = 20;
	private static final int TRACK_H = 10;

	private final Consumer<Boolean> onChange;
	private final int accent;
	private final long appearAt;
	private final long[] lastFrame = new long[1];
	private boolean value;
	private float knob;
	private float hover;

	GolemToggle(int x, int y, int w, int h, Component label, boolean value, int accent, long appearAt, Consumer<Boolean> onChange) {
		super(x, y, w, h, label);
		this.value = value;
		this.knob = value ? 1f : 0f;
		this.accent = accent;
		this.appearAt = appearAt;
		this.onChange = onChange;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		value = !value;
		onChange.accept(value);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float appear = UiAnim.appear(appearAt);
		if (appear <= 0.01f) return;
		float dt = UiAnim.tick(lastFrame);
		knob = UiAnim.approach(knob, value ? 1f : 0f, dt, 18f);
		hover = UiAnim.approach(hover, active && isHoveredOrFocused() ? 1f : 0f, dt, 14f);
		float a = appear * (active ? 1f : 0.45f);
		int x = getX();
		int y = getY() + Math.round((1f - appear) * 4f);
		int w = getWidth();
		int h = getHeight();

		graphics.fill(x, y, x + w, y + h, UiText.withAlpha(UiText.lerpColor(0xD0141220, 0xF0201A32, hover), a));
		graphics.outline(x, y, w, h, UiText.withAlpha(UiText.lerpColor(0x30FFFFFF, accent, hover * 0.8f), a));
		graphics.text(Minecraft.getInstance().font, getMessage(), x + 7, y + (h - 8) / 2,
				UiText.withAlpha(UiText.lerpColor(0xFFD6D2E2, UiText.WHITE, hover), a), false);

		int tx = x + w - TRACK_W - 6;
		int ty = y + (h - TRACK_H) / 2;
		graphics.fill(tx, ty, tx + TRACK_W, ty + TRACK_H, UiText.withAlpha(UiText.lerpColor(0xFF3A3648, accent, knob), a));
		int kx = tx + 1 + Math.round(knob * (TRACK_W - TRACK_H));
		graphics.fill(kx, ty + 1, kx + TRACK_H - 2, ty + TRACK_H - 1, UiText.withAlpha(UiText.WHITE, a));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
				CommonComponents.optionNameValue(getMessage(), CommonComponents.optionStatus(value)));
	}
}
