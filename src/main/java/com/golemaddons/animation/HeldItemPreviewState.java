package com.golemaddons.animation;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

/** One frame of the animation editor's first-person preview, drawn by {@link HeldItemPreviewRenderer}. */
public record HeldItemPreviewState(ItemStackRenderState item, HeldItemAnimation animation, float attack, boolean rightArm,
		int pixelWidth, int pixelHeight, int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea,
		@Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
	public HeldItemPreviewState(ItemStackRenderState item, HeldItemAnimation animation, float attack, boolean rightArm,
			int guiScale, int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
		this(item, animation, attack, rightArm, (x1 - x0) * guiScale, (y1 - y0) * guiScale, x0, y0, x1, y1, scissorArea,
				PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
	}

	@Override
	public float scale() {
		return 1f;
	}
}
