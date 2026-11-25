package com.golemaddons.ui;

import java.util.List;
import java.util.function.IntConsumer;
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
 * A row of mutually exclusive options with a highlight that slides to the selected one (tabs, Full / Pivot).
 * Click an option, or use the arrow keys while focused. An optional label is drawn on the left.
 */
final class GolemSegmented extends AbstractWidget {
	private final List<Component> options;
	private final IntConsumer onSelect;
	private final int accent;
	private final long appearAt;
	private final long[] lastFrame = new long[1];
	private int labelWidth;
	private int selected;
	private float slide;
	private float hover;

	GolemSegmented(int x, int y, int w, int h, Component label, List<Component> options, int selected, int accent, long appearAt,
			IntConsumer onSelect) {
		super(x, y, w, h, label);
		this.options = options;
		this.selected = selected;
		this.slide = selected;
		this.accent = accent;
		this.appearAt = appearAt;
		this.onSelect = onSelect;
		this.labelWidth = label.getString().isEmpty() ? 0 : Minecraft.getInstance().font.width(label) + 14;
	}

	/** Keep the label for narration (and finding the widget) but don't draw it. */
	GolemSegmented hideLabel() {
		labelWidth = 0;
		hiddenLabel = true;
		return this;
	}

	private boolean hiddenLabel;

	/** Start the highlight at {@code index} (e.g. the previously selected tab) and let it slide to the selection. */
	GolemSegmented slideFrom(float index) {
		slide = index;
		return this;
	}

	private int segX0() {
		return getX() + labelWidth;
	}

	private float segW() {
		return (getWidth() - labelWidth) / (float) options.size();
	}

	private void select(int index) {
		if (index == selected || index < 0 || index >= options.size()) return;
		selected = index;
		onSelect.accept(index);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (event.x() >= segX0()) select((int) ((event.x() - segX0()) / segW()));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!active) return false;
		if (event.isLeft()) {
			select(selected - 1);
			return true;
		}
		if (event.isRight()) {
			select(selected + 1);
			return true;
		}
		return false;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		float appear = UiAnim.appear(appearAt);
		if (appear <= 0.01f) return;
		float dt = UiAnim.tick(lastFrame);
		slide = UiAnim.approach(slide, selected, dt, 16f);
		hover = UiAnim.approach(hover, isHoveredOrFocused() ? 1f : 0f, dt, 14f);
		float a = appear * (active ? 1f : 0.45f);
		Font font = Minecraft.getInstance().font;
		int x = getX();
		int y = getY() + Math.round((1f - appear) * 4f);
		int h = getHeight();

		if (labelWidth > 0 && !hiddenLabel) {
			graphics.text(font, getMessage(), x + 7, y + (h - 8) / 2, UiText.withAlpha(0xFFD6D2E2, a), false);
		}
		int sx = segX0();
		int sw = getRight() - sx;
		graphics.fill(sx, y, sx + sw, y + h, UiText.withAlpha(0xD0141220, a));
		graphics.outline(sx, y, sw, h, UiText.withAlpha(UiText.lerpColor(0x30FFFFFF, accent, hover * 0.8f), a));
		float segW = segW();
		int hx0 = Math.round(sx + slide * segW) + 1;
		int hx1 = Math.round(sx + (slide + 1) * segW) - 1;
		graphics.fill(hx0, y + 1, hx1, y + h - 1, UiText.withAlpha(UiText.lerpColor(accent, 0xFF000000, 0.55f), a));
		graphics.fill(hx0, y + h - 2, hx1, y + h - 1, UiText.withAlpha(accent, a));
		for (int i = 0; i < options.size(); i++) {
			float closeness = 1f - Math.min(1f, Math.abs(slide - i));
			int color = UiText.lerpColor(UiText.GRAY, UiText.WHITE, closeness);
			graphics.centeredText(font, options.get(i), Math.round(sx + (i + 0.5f) * segW), y + (h - 8) / 2, UiText.withAlpha(color, a));
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.empty().append(getMessage()).append(": ").append(options.get(selected)));
	}
}
