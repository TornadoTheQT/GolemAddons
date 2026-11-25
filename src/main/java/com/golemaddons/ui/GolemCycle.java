package com.golemaddons.ui;

import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * A GolemAddons-styled replacement for vanilla's {@code CycleButton}, with the same builder shape. On/off settings
 * ({@link #onOffBuilder}) draw as a label with an animated switch; other values draw as "Label: Value" with arrows,
 * and the value slides in when it changes. Click for the next value, right-click (or Shift-click) for the previous.
 */
final class GolemCycle<T> extends AbstractButton {
	@FunctionalInterface
	interface OnValueChange<T> {
		void onValueChange(GolemCycle<T> button, T value);
	}

	private final Component label;
	private final List<T> values;
	private final Function<T, Component> stringifier;
	private final Function<T, Tooltip> tooltip;
	private final boolean onlyValue;
	private final boolean asSwitch;
	private final OnValueChange<T> onChange;
	private final long[] lastFrame = new long[1];
	private int index;
	private float hover;
	private float knob;
	private long changedAt;
	private int direction = 1;

	private GolemCycle(int x, int y, int w, int h, Component label, List<T> values, int index, Function<T, Component> stringifier,
			Function<T, Tooltip> tooltip, boolean onlyValue, boolean asSwitch, OnValueChange<T> onChange) {
		super(x, y, w, h, label);
		this.label = label;
		this.values = values;
		this.index = index;
		this.stringifier = stringifier;
		this.tooltip = tooltip;
		this.onlyValue = onlyValue;
		this.asSwitch = asSwitch;
		this.onChange = onChange;
		this.knob = Boolean.TRUE.equals(values.get(index)) ? 1f : 0f;
		updateTooltip();
	}

	static Builder<Boolean> onOffBuilder(boolean initial) {
		return new Builder<Boolean>(v -> v ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF, initial)
				.withValues(List.of(true, false)).asSwitch();
	}

	static Builder<Boolean> booleanBuilder(Component trueText, Component falseText, boolean initial) {
		return new Builder<Boolean>(v -> v ? trueText : falseText, initial).withValues(List.of(true, false));
	}

	static <T> Builder<T> builder(Function<T, Component> stringifier, T initial) {
		return new Builder<>(stringifier, initial);
	}

	static final class Builder<T> {
		private final Function<T, Component> stringifier;
		private final T initial;
		private List<T> values;
		private Function<T, Tooltip> tooltip = v -> null;
		private boolean onlyValue;
		private boolean asSwitch;

		private Builder(Function<T, Component> stringifier, T initial) {
			this.stringifier = stringifier;
			this.initial = initial;
		}

		@SafeVarargs
		final Builder<T> withValues(T... values) {
			return withValues(List.of(values));
		}

		Builder<T> withValues(List<T> values) {
			this.values = values;
			return this;
		}

		Builder<T> displayOnlyValue() {
			this.onlyValue = true;
			return this;
		}

		Builder<T> withTooltip(Function<T, Tooltip> tooltip) {
			this.tooltip = tooltip;
			return this;
		}

		private Builder<T> asSwitch() {
			this.asSwitch = true;
			return this;
		}

		GolemCycle<T> create(int x, int y, int w, int h, Component label, OnValueChange<T> onChange) {
			int index = Math.max(0, values.indexOf(initial));
			return new GolemCycle<>(x, y, w, h, label, values, index, stringifier, tooltip, onlyValue, asSwitch, onChange);
		}
	}

	T getValue() {
		return values.get(index);
	}

	void setValue(T value) {
		int i = values.indexOf(value);
		if (i >= 0) index = i;
		updateTooltip();
	}

	private void updateTooltip() {
		setTooltip(tooltip.apply(getValue()));
	}

	private void step(int delta) {
		index = Math.floorMod(index + delta, values.size());
		direction = delta;
		changedAt = Util.getMillis();
		updateTooltip();
		onChange.onValueChange(this, getValue());
	}

	@Override
	public void onPress(InputWithModifiers input) {
		step(input.hasShiftDown() ? -1 : 1);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (active && visible && event.button() == 1 && isMouseOver(event.x(), event.y())) {
			playDownSound(Minecraft.getInstance().getSoundManager());
			step(-1);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (!active || scrollY == 0) return false;
		step(scrollY < 0 ? 1 : -1);
		return true;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float dt = UiAnim.tick(lastFrame);
		hover = UiAnim.approach(hover, active && isHoveredOrFocused() ? 1f : 0f, dt, 14f);
		float a = active ? 1f : 0.45f;
		Font font = Minecraft.getInstance().font;
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		int accent = UiText.ACCENT;

		graphics.fill(x, y, x + w, y + h, UiText.withAlpha(UiText.lerpColor(0xD0141220, 0xF0201A32, hover), a));
		graphics.outline(x, y, w, h, UiText.withAlpha(UiText.lerpColor(0x30FFFFFF, accent, hover * 0.8f), a));
		int textY = y + (h - 8) / 2;

		if (asSwitch) {
			boolean on = Boolean.TRUE.equals(getValue());
			knob = UiAnim.approach(knob, on ? 1f : 0f, dt, 18f);
			int trackW = 20;
			int trackH = Math.min(10, h - 6);
			// With "value only" (a narrow on/off column) the switch sits alone in the middle.
			int tx = onlyValue ? x + (w - trackW) / 2 : x + w - trackW - 6;
			int ty = y + (h - trackH) / 2;
			if (!onlyValue) {
				int textW = tx - x - 12;
				graphics.text(font, fit(font, label, textW), x + 7, textY, UiText.withAlpha(UiText.lerpColor(0xFFD6D2E2, UiText.WHITE, hover), a), false);
			}
			graphics.fill(tx, ty, tx + trackW, ty + trackH, UiText.withAlpha(UiText.lerpColor(0xFF3A3648, UiText.ACCENT_2, knob), a));
			int kx = tx + 1 + Math.round(knob * (trackW - trackH));
			graphics.fill(kx, ty + 1, kx + trackH - 2, ty + trackH - 1, UiText.withAlpha(UiText.WHITE, a));
			return;
		}

		// "Label: Value" (or just the value), with small arrows; the new value slides in from the side it came from.
		Component value = stringifier.apply(getValue());
		Component text = onlyValue ? value : CommonComponents.optionNameValue(label, value);
		float slide = UiAnim.easeOutCubic((Util.getMillis() - changedAt) / 180f);
		// Arrows only when there's room for them; a narrow picker uses the space for its value.
		boolean arrows = font.width(text) + 22 <= w;
		if (arrows) {
			int arrowColor = UiText.withAlpha(UiText.lerpColor(UiText.DARK_GRAY, accent, hover), a);
			graphics.text(font, "‹", x + 4, textY, arrowColor, false);
			graphics.text(font, "›", x + w - 4 - font.width("›"), textY, arrowColor, false);
		}
		Component shown = fit(font, text, arrows ? w - 20 : w - 6);
		int cx = x + w / 2 + Math.round((1f - slide) * 6f * direction);
		graphics.centeredText(font, shown, cx, textY, UiText.withAlpha(UiText.WHITE, a * (0.4f + 0.6f * slide)));
	}

	private static Component fit(Font font, Component text, int width) {
		if (font.width(text) <= width) return text;
		return Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(0, width - font.width("…"))) + "…")
				.withStyle(text.getStyle());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, CommonComponents.optionNameValue(label, stringifier.apply(getValue())));
	}
}
