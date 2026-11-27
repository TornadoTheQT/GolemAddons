package com.golemaddons.mixin;

import com.golemaddons.render.RiftHaze;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rift haze: open-air fog goes back to plain render-distance fog, and (optionally) takes an overworld colour instead
 * of the End's purple. Only the local fog render state changes.
 */
@Mixin(AtmosphericFogEnvironment.class)
abstract class AtmosphericFogMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void golemaddons$clearFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker,
			CallbackInfo ci) {
		if (!RiftHaze.active()) return;
		fog.environmentalStart = Float.MAX_VALUE;
		fog.environmentalEnd = Float.MAX_VALUE;
		fog.skyEnd = renderDistance;
		fog.cloudEnd = Minecraft.getInstance().options.cloudRange().get() * 16;
	}

	@Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
	private void golemaddons$fogColor(ClientLevel level, Camera camera, int renderDistance, float partialTicks, CallbackInfoReturnable<Integer> cir) {
		if (RiftHaze.normalSky()) cir.setReturnValue(ARGB.srgbLerp(0.35f, RiftHaze.FOG, RiftHaze.SKY));
	}
}
