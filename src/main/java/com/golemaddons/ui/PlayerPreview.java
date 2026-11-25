package com.golemaddons.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the local player walking on the spot and slowly turning, with their cape (custom if set, otherwise their
 * normal one) and a nametag above. Uses the player's real render state, so skin, armor, held items and the custom
 * cape look exactly as in game; only pose and animation are simulated.
 */
final class PlayerPreview {
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float TURN_DEGREES_PER_SECOND = 40f;

	private PlayerPreview() {
	}

	/** Draws into the box; {@code nameTag} is shown above the model. Does nothing useful outside a world. */
	static void extract(GuiGraphicsExtractor graphics, Minecraft mc, int x0, int y0, int x1, int y1, Component nameTag) {
		Font font = mc.font;
		LocalPlayer player = mc.player;
		int cx = (x0 + x1) / 2;
		if (player == null) {
			graphics.centeredText(font, Component.translatable("golemaddons.cosmetics.preview.no_world"), cx, (y0 + y1) / 2 - 4, UiText.GRAY);
			return;
		}

		int tagY = y0 + 2;
		int tagW = font.width(nameTag);
		graphics.fill(cx - tagW / 2 - 2, tagY - 1, cx + tagW / 2 + 2, tagY + 9, 0x40000000);
		graphics.text(font, nameTag, cx - tagW / 2, tagY, UiText.WHITE, false);

		EntityRenderer<? super LocalPlayer, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(player);
		EntityRenderState state = renderer.createRenderState(player, 1f);
		if (!(state instanceof AvatarRenderState avatar)) {
			return;
		}
		float t = (Util.getMillis() % 3_600_000L) / 1000f;
		simulate(avatar, t);

		int modelTop = tagY + 12;
		float size = Math.min((y1 - modelTop) / 2.45f, (x1 - x0) / 1.3f);
		Quaternionf pose = new Quaternionf().rotateZ(Mth.PI);
		Quaternionf camera = new Quaternionf().rotateX(-8f * Mth.DEG_TO_RAD);
		pose.mul(camera);
		Vector3f translation = new Vector3f(0f, avatar.boundingBoxHeight / 2f + 0.05f, 0f);
		graphics.entity(avatar, size, translation, pose, camera, x0, modelTop, x1, y1);
	}

	/** Standing, walking in place and turning, cape swaying; not affected by what the player is doing in game. */
	private static void simulate(AvatarRenderState s, float t) {
		s.shadowPieces.clear();
		s.outlineColor = EntityRenderState.NO_OUTLINE;
		s.nameTag = null; // drawn flat above the model instead
		s.lightCoords = FULL_BRIGHT;
		s.isInvisible = false;
		s.isInvisibleToPlayer = false;
		s.pose = Pose.STANDING;
		s.isCrouching = false;
		s.isFallFlying = false;
		s.isVisuallySwimming = false;
		s.swimAmount = 0f;
		s.isPassenger = false;
		s.isUpsideDown = false;
		s.deathTime = 0f;
		s.hasRedOverlay = false;
		s.boundingBoxWidth /= s.scale;
		s.boundingBoxHeight /= s.scale;
		s.scale = 1f;

		s.walkAnimationPos = t * 9f;
		s.walkAnimationSpeed = 0.75f;
		s.bodyRot = 180f + (t * TURN_DEGREES_PER_SECOND) % 360f;
		s.yRot = 0f;
		s.xRot = 0f;

		s.showCape = true;
		s.capeLean = 22f + 5f * Mth.sin(t * 2.3f);
		s.capeFlap = 3f * Mth.sin(s.walkAnimationPos * 0.6662f);
		s.capeLean2 = 0f;
	}
}
