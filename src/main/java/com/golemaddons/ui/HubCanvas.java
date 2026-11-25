package com.golemaddons.ui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

/**
 * A fixed 640x360 "design" canvas mapped onto the real window, independent of the GUI scale setting.
 *
 * <p>One design unit is a whole number of physical pixels whenever the window is at least 640x360, so the
 * pixel font stays crisp. The canvas is centered and fills as much of the window as that allows.
 */
record HubCanvas(float scale, float originX, float originY) {
	static final int WIDTH = 640;
	static final int HEIGHT = 360;

	static HubCanvas of(Minecraft mc, int guiWidth, int guiHeight) {
		Window window = mc.getWindow();
		float fit = Math.min(window.getWidth() / (float) WIDTH, window.getHeight() / (float) HEIGHT);
		float physicalPerUnit = fit >= 1f ? (float) Math.floor(fit) : fit;
		float scale = physicalPerUnit / Math.max(1, window.getGuiScale());
		return new HubCanvas(scale, (guiWidth - WIDTH * scale) / 2f, (guiHeight - HEIGHT * scale) / 2f);
	}

	/** Design x to GUI x. */
	float x(float units) {
		return originX + units * scale;
	}

	/** Design y to GUI y. */
	float y(float units) {
		return originY + units * scale;
	}

	int size(float units) {
		return Math.round(units * scale);
	}
}
