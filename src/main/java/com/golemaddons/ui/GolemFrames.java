package com.golemaddons.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

/** Entry point for mixins into package-private UI classes. */
public final class GolemFrames {
	private GolemFrames() {
	}

	/** Draws a GolemAddons frame for our own text boxes; returns false for anyone else's. */
	public static boolean drawEditBoxFrame(EditBox box, GuiGraphicsExtractor graphics) {
		if (box instanceof GolemEditBox golem) {
			golem.extractFrame(graphics);
			return true;
		}
		return false;
	}
}
