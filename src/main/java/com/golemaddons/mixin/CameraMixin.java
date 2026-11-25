package com.golemaddons.mixin;

import com.golemaddons.camera.CameraFeature;
import com.golemaddons.config.GolemConfig;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Third-person camera distance and block clipping. Only the local render camera moves: the player's position,
 * rotation and every packet stay exactly as in vanilla.
 */
@Mixin(Camera.class)
abstract class CameraMixin {
	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
	private float golemaddons$distance(float vanilla) {
		GolemConfig.Camera c = CameraFeature.settings();
		return c != null && c.customDistance ? c.distance : vanilla;
	}

	/** Skips the "pull the camera in front of walls" raycast, so the camera passes through blocks. */
	@Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
	private void golemaddons$clip(float distance, CallbackInfoReturnable<Float> cir) {
		GolemConfig.Camera c = CameraFeature.settings();
		if (c != null && c.clipThroughBlocks) {
			cir.setReturnValue(distance);
		}
	}
}
