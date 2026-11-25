package com.golemaddons.ui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A labelled slider in the GolemAddons style: name and value on top, a thin track with an accent fill below.
 * Drag or click to set; arrow keys step (Shift for ten steps); double-click or right-click resets to the default.
 * Values snap to the step and stay inside the range.
 */
final class GolemSlider extends AbstractWidget {
	static final int HEIGHT = 22;
	private static final int TRACK_H = 3;
	private static final int HANDLE_W = 5;
	private static final int HANDLE_H = 9;

	private final double min;
	private final double max;
	private final double step;
	private final double defaultValue;
	private final DoubleSupplier getter;
	private final DoubleConsumer setter;
	private final DoubleFunction<String> format;
	private final int accent;
	private boolean dragging;
	private float hover;
	private long appearAt;
	private final long[] lastFrame = new long[1];

	/** Fade and slide in from this time ({@link net.minecraft.util.Util#getMillis()}), like the other Golem widgets. */
	GolemSlider appearAt(long at) {
		this.appearAt = at;
		return this;
	}

	GolemSlider(int x, int y, int width, Component label, double min, double max, double step, double defaultValue,
			DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<String> format, int accent) {
		super(x, y, width, HEIGHT, label);
		this.min = min;
		this.max = max;
		this.step = step;
		this.defaultValue = defaultValue;
		this.getter = getter;
		this.setter = setter;
		this.format = format;
		this.accent = accent;
	}

	private double value() {
		return getter.getAsDouble();
	}

	private void set(double v) {
		double snapped = Math.round((v - min) / step) * step + min;
		// Avoid float noise like 0.30000000004 in the stored value and the label.
		snapped = Math.round(snapped * 1e6) / 1e6;
		setter.accept(Math.clamp(snapped, min, max));
	}

	private int trackX0() {
		return getX() + HANDLE_W / 2;
	}

	private int trackX1() {
		return getRight() - HANDLE_W / 2 - 1;
	}

	private void setFromMouse(double mouseX) {
		double t = (mouseX - trackX0()) / Math.max(1, trackX1() - trackX0());
		set(min + Math.clamp(t, 0, 1) * (max - min));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (active && visible && event.button() == 1 && isMouseOver(event.x(), event.y())) {
			set(defaultValue);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (doubleClick) {
			set(defaultValue);
			dragging = false;
			return;
		}
		dragging = true;
		setFromMouse(event.x());
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		if (dragging) setFromMouse(event.x());
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		dragging = false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!active) return false;
		if (event.isLeft() || event.isRight()) {
			double steps = (event.isLeft() ? -1 : 1) * (event.hasShiftDown() ? 10 : 1);
			set(value() + steps * step);
			return true;
		}
		return false;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		Font font = Minecraft.getInstance().font;
		float appear = appearAt == 0 ? 1f : UiAnim.appear(appearAt);
		if (appear <= 0.01f) return;
		hover = UiAnim.approach(hover, isHoveredOrFocused() || dragging ? 1f : 0f, UiAnim.tick(lastFrame), 14f);
		int x = getX();
		int y = getY() + Math.round((1f - appear) * 4f);
		int alpha = Math.round((active ? 0xFF : 0x80) * appear);

		int labelColor = UiText.withAlpha(UiText.lerpColor(UiText.GRAY, UiText.WHITE, hover), alpha / 255f);
		graphics.text(font, getMessage(), x, y + 1, labelColor, false);
		String text = format.apply(value());
		boolean isDefault = Math.abs(value() - defaultValue) < step / 2;
		graphics.text(font, text, getRight() - font.width(text), y + 1,
				UiText.withAlpha(isDefault ? UiText.DARK_GRAY : accent, alpha / 255f), false);

		int tx0 = trackX0();
		int tx1 = trackX1();
		int ty = y + 14;
		graphics.fill(tx0, ty, tx1, ty + TRACK_H, UiText.withAlpha(0x50FFFFFF, alpha / 255f));
		double t = (value() - min) / (max - min);
		int hx = tx0 + (int) Math.round(t * (tx1 - tx0));
		// Fill from the default value (e.g. 0 in the middle of a -1..1 range), so offsets read as offsets.
		int zero = tx0 + (int) Math.round(Math.clamp((defaultValue - min) / (max - min), 0, 1) * (tx1 - tx0));
		graphics.fill(Math.min(zero, hx), ty, Math.max(zero, hx), ty + TRACK_H, UiText.withAlpha(accent, alpha / 255f * 0.9f));
		if (zero > tx0 && zero < tx1) {
			graphics.fill(zero, ty - 1, zero + 1, ty + TRACK_H + 1, UiText.withAlpha(0x90FFFFFF, alpha / 255f));
		}
		int handleColor = UiText.withAlpha(UiText.lerpColor(0xFFD8D4E8, UiText.WHITE, hover), alpha / 255f);
		int hy = ty + TRACK_H / 2 - HANDLE_H / 2;
		graphics.fill(hx - HANDLE_W / 2, hy, hx - HANDLE_W / 2 + HANDLE_W, hy + HANDLE_H, handleColor);
		if (hover > 0.05f) {
			graphics.outline(hx - HANDLE_W / 2 - 1, hy - 1, HANDLE_W + 2, HANDLE_H + 2, UiText.withAlpha(accent, hover * alpha / 255f));
		}
		if (isHovered()) {
			graphics.requestCursor(active ? (dragging ? CursorTypes.RESIZE_EW : CursorTypes.POINTING_HAND) : CursorTypes.NOT_ALLOWED);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.slider",
				Component.empty().append(getMessage()).append(": ").append(format.apply(value()))));
		if (active) {
			output.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.focused"));
		}
	}
}
