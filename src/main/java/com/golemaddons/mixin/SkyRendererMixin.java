package com.golemaddons.mixin;

import com.golemaddons.render.RiftHaze;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rift haze with "normal sky": the End sky box is drawn as a blue overworld sky. Render state only. */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/dimension/DimensionType;skybox()Lnet/minecraft/world/level/dimension/DimensionType$Skybox;"))
	private DimensionType.Skybox golemaddons$skybox(DimensionType.Skybox skybox) {
		return RiftHaze.overworldSkybox(skybox) ? DimensionType.Skybox.OVERWORLD : skybox;
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void golemaddons$skyColor(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
		if (RiftHaze.normalSky() && state.skybox == DimensionType.Skybox.OVERWORLD && level.dimensionType().skybox() == DimensionType.Skybox.END) {
			state.skyColor = RiftHaze.SKY;
		}
	}
}
