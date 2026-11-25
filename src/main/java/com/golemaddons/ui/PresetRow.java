package com.golemaddons.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** One line of the animation preset list. Clicking it (or Enter while focused) loads the preset. */
final class PresetRow extends AbstractButton {
	private final boolean selected;
	private final Runnable action;

	PresetRow(int x, int y, int w, int h, Component name, boolean selected, Runnable action) {
		super(x, y, w, h, name);
		this.selected = selected;
		this.action = action;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = getX();
		int y = getY();
		if (selected) {
			graphics.fill(x, y, getRight(), getBottom(), 0x509D7BFF);
			graphics.fill(x, y, x + 2, getBottom(), UiText.ACCENT);
		} else if (isHoveredOrFocused()) {
			graphics.fill(x, y, getRight(), getBottom(), 0x30FFFFFF);
		}
		graphics.text(Minecraft.getInstance().font, getMessage(), x + 6, y + (getHeight() - 8) / 2,
				selected ? UiText.WHITE : UiText.lerpColor(UiText.GRAY, UiText.WHITE, isHoveredOrFocused() ? 1f : 0f), false);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
