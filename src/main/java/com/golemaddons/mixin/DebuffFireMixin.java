package com.golemaddons.mixin;

import com.golemaddons.render.Debuffs;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Disable debuffs: no flames across the screen while burning (you still burn). */
@Mixin(ScreenEffectRenderer.class)
abstract class DebuffFireMixin {
	@ModifyExpressionValue(method = "renderScreenEffect", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;isOnFire()Z"))
	private boolean golemaddons$noFireOverlay(boolean onFire) {
		return onFire && !Debuffs.hidesFire();
	}
}
