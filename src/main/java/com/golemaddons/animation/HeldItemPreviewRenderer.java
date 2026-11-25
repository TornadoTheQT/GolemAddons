package com.golemaddons.animation;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Draws the held item as it looks in first person: a perspective camera at the eye and the same
 * {@link HeldItemTransform} code the game uses.
 */
public final class HeldItemPreviewRenderer extends PictureInPictureRenderer<HeldItemPreviewState> {
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Vanilla's default field of view; the hand itself is drawn with a fixed FOV in game too. */
	private static final float FOV = 70f;

	private final Projection projection = new Projection();
	private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("GolemAddons held item preview");

	public HeldItemPreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
	}

	@Override
	public Class<HeldItemPreviewState> getRenderStateClass() {
		return HeldItemPreviewState.class;
	}

	@Override
	protected void renderToTexture(HeldItemPreviewState state, PoseStack ignoredGuiPose) {
		// The base class set up a flat GUI projection; the preview needs the game's perspective one instead.
		projection.setupPerspective(0.05f, 100f, FOV, Math.max(1, state.pixelWidth()), Math.max(1, state.pixelHeight()));
		RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
		Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);

		PoseStack pose = new PoseStack();
		HeldItemAnimation a = state.animation();
		HeldItemTransform.applyBase(pose, a, state.rightArm(), 0f);
		HeldItemTransform.applyItem(pose, a, state.rightArm(), state.attack());

		FeatureRenderDispatcher features = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
		state.item().submit(pose, features.getSubmitNodeStorage(), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		features.renderAllFeatures();
	}

	@Override
	protected String getTextureLabel() {
		return "golemaddons_held_item";
	}

	@Override
	public void close() {
		super.close();
		projectionBuffer.close();
	}
}
