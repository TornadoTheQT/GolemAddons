package com.golemaddons.mixin;

import com.golemaddons.camera.CameraFeature;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** F5 skips the front-facing view when that option is on. Local option only. */
@Mixin(Minecraft.class)
abstract class MinecraftPerspectiveMixin {
	@ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;cycle()Lnet/minecraft/client/CameraType;"))
	private CameraType golemaddons$skipFront(CameraType next) {
		return CameraFeature.nextPerspective(next);
	}
}
