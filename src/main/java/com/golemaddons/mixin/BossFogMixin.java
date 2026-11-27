package com.golemaddons.mixin;

import com.golemaddons.render.RiftHaze;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rift haze: the boss bar's thick world fog and screen darkening are skipped. Render-only. */
@Mixin(BossHealthOverlay.class)
abstract class BossFogMixin {
	@Inject(method = "shouldCreateWorldFog", at = @At("HEAD"), cancellable = true)
	private void golemaddons$noWorldFog(CallbackInfoReturnable<Boolean> cir) {
		if (RiftHaze.active()) cir.setReturnValue(false);
	}

	@Inject(method = "shouldDarkenScreen", at = @At("HEAD"), cancellable = true)
	private void golemaddons$noDarken(CallbackInfoReturnable<Boolean> cir) {
		if (RiftHaze.active()) cir.setReturnValue(false);
	}
}
