package com.golemaddons.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * A flat GolemAddons button: dark fill, an accent bar that grows on hover, a short flash when pressed, and a fade-in
 * when its screen opens. Works with the mouse, Tab and Enter like any button.
 */
final class GolemButton extends AbstractButton {
	private final Runnable action;
	private final int accent;
	private final long appearAt;
	private final long[] lastFrame = new long[1];
	private float hover;
	private long pressedAt;
	private boolean danger;

	GolemButton(int x, int y, int w, int h, Component label, int accent, long appearAt, Runnable action) {
		super(x, y, w, h, label);
		this.action = action;
		this.accent = accent;
		this.appearAt = appearAt;
	}

	/** Same shape as vanilla's {@code Button.builder}, so screens can switch over line for line. */
	static Builder builder(Component label, java.util.function.Consumer<GolemButton> onPress) {
		return new Builder(label, onPress);
	}

	static final class Builder {
		private final Component label;
		private final java.util.function.Consumer<GolemButton> onPress;
		private int x;
		private int y;
		private int w = 150;
		private int h = 20;
		private net.minecraft.client.gui.components.Tooltip tooltip;
		private int accent = UiText.ACCENT;

		private Builder(Component label, java.util.function.Consumer<GolemButton> onPress) {
			this.label = label;
			this.onPress = onPress;
		}

		Builder pos(int x, int y) {
			this.x = x;
			this.y = y;
			return this;
		}

		Builder width(int w) {
			this.w = w;
			return this;
		}

		Builder size(int w, int h) {
			this.w = w;
			this.h = h;
			return this;
		}

		Builder bounds(int x, int y, int w, int h) {
			return pos(x, y).size(w, h);
		}

		Builder tooltip(net.minecraft.client.gui.components.Tooltip tooltip) {
			this.tooltip = tooltip;
			return this;
		}

		Builder accent(int accent) {
			this.accent = accent;
			return this;
		}

		GolemButton build() {
			GolemButton[] self = new GolemButton[1];
			self[0] = new GolemButton(x, y, w, h, label, accent, 0L, () -> onPress.accept(self[0]));
			if (tooltip != null) self[0].setTooltip(tooltip);
			return self[0];
		}
	}

	/** Red styling, for a destructive action awaiting confirmation. */
	GolemButton danger(boolean danger) {
		this.danger = danger;
		return this;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		pressedAt = Util.getMillis();
		action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float appear = appearAt == 0 ? 1f : UiAnim.appear(appearAt);
		if (appear <= 0.01f) return;
		float dt = UiAnim.tick(lastFrame);
		hover = UiAnim.approach(hover, active && isHoveredOrFocused() ? 1f : 0f, dt, 14f);
		int color = danger ? UiText.RED : accent;
		float a = appear * (active ? 1f : 0.45f);

		int x = getX();
		int y = getY() + Math.round((1f - appear) * 4f);
		int w = getWidth();
		int h = getHeight();
		graphics.fill(x, y, x + w, y + h, UiText.withAlpha(UiText.lerpColor(0xD0141220, 0xF0241D3A, hover), a));
		graphics.outline(x, y, w, h, UiText.withAlpha(UiText.lerpColor(0x30FFFFFF, color, hover), a));
		int bar = Math.round(w * (0.25f + 0.75f * hover));
		graphics.fill(x + (w - bar) / 2, y + h - 2, x + (w + bar) / 2, y + h, UiText.withAlpha(color, a));
		float flash = 1f - Math.min(1f, (Util.getMillis() - pressedAt) / 220f);
		if (flash > 0f) graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, UiText.withAlpha(color, flash * 0.35f));

		Font font = Minecraft.getInstance().font;
		int text = active ? UiText.lerpColor(0xFFE6E2F2, UiText.WHITE, hover) : UiText.GRAY;
		graphics.centeredText(font, getMessage(), x + w / 2, y + (h - 8) / 2, UiText.withAlpha(text, a));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
