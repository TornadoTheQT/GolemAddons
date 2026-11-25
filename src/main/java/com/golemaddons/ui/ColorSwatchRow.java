package com.golemaddons.ui;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A row of colour swatches; click one (or use the arrow keys) to pick which colour the sliders below edit. */
final class ColorSwatchRow extends net.minecraft.client.gui.components.AbstractWidget {
	private final List<Component> names;
	private final IntUnaryOperator color;
	private final IntConsumer onSelect;
	private int selected;

	ColorSwatchRow(int x, int y, int w, int h, List<Component> names, IntUnaryOperator color, int selected, IntConsumer onSelect) {
		super(x, y, w, h, Component.translatable("golemaddons.terminals.colors"));
		this.names = names;
		this.color = color;
		this.selected = selected;
		this.onSelect = onSelect;
	}

	private int cellW() {
		return (getWidth() + 3) / names.size();
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int cw = cellW();
		for (int i = 0; i < names.size(); i++) {
			int x = getX() + i * cw;
			int w = cw - 3;
			int y = getY();
			int h = getHeight();
			boolean hover = isHovered() && mouseX >= x && mouseX < x + w;
			// Checkerboard behind, so a see-through colour shows as one.
			g.fill(x, y, x + w, y + h, 0xFF2A2A2A);
			for (int cx = 0; cx < w; cx += 4) {
				for (int cy = (cx / 4) % 2 * 4; cy < h; cy += 8) g.fill(x + cx, y + cy, Math.min(x + w, x + cx + 4), Math.min(y + h, y + cy + 4), 0xFF3C3C3C);
			}
			g.fill(x, y, x + w, y + h, color.applyAsInt(i));
			g.outline(x, y, w, h, i == selected ? UiText.WHITE : hover ? 0xA0FFFFFF : 0x50FFFFFF);
			if (i == selected) g.fill(x + 1, y + h + 1, x + w - 1, y + h + 2, UiText.ACCENT_2);
		}
		if (isHovered()) {
			int i = Math.clamp((mouseX - getX()) / cw, 0, names.size() - 1);
			g.setTooltipForNextFrame(names.get(i), mouseX, mouseY);
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		select(Math.clamp((int) ((event.x() - getX()) / cellW()), 0, names.size() - 1));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isLeft()) {
			select(Math.floorMod(selected - 1, names.size()));
			return true;
		}
		if (event.isRight()) {
			select((selected + 1) % names.size());
			return true;
		}
		return super.keyPressed(event);
	}

	private void select(int index) {
		if (index == selected) return;
		selected = index;
		onSelect.accept(index);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.translatable("golemaddons.terminals.colors.narration", names.get(selected)));
	}
}
