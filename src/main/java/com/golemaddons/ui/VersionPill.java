package com.golemaddons.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** The hub's version number: a small pill on the {@link HubCanvas} that opens the update settings and changelog. */
final class VersionPill extends AbstractButton {
	private final float scale;
	private final int unitW;
	private final int unitH;
	private final Runnable action;
	private final long[] lastFrame = new long[1];
	private float hover;

	VersionPill(HubCanvas canvas, float ux, float uy, int unitW, int unitH, Component text, Runnable action) {
		super(Math.round(canvas.x(ux)), Math.round(canvas.y(uy)), canvas.size(unitW), canvas.size(unitH), text);
		this.scale = canvas.scale();
		this.unitW = unitW;
		this.unitH = unitH;
		this.action = action;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		hover = UiAnim.approach(hover, isHoveredOrFocused() ? 1f : 0f, UiAnim.tick(lastFrame), 14f);
		g.pose().pushMatrix();
		g.pose().translate(getX(), getY());
		g.pose().scale(scale, scale);
		g.fill(0, 0, unitW, unitH, UiText.lerpColor(0x90141220, 0xC0241E3A, hover));
		g.outline(0, 0, unitW, unitH, UiText.withAlpha(UiText.lerpColor(UiText.ACCENT, UiText.ACCENT_2, hover), 0.6f + 0.4f * hover));
		g.text(net.minecraft.client.Minecraft.getInstance().font, getMessage(), 4, 3, UiText.lerpColor(UiText.GRAY, UiText.WHITE, hover));
		g.pose().popMatrix();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
