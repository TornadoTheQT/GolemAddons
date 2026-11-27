package com.golemaddons.mixin;

import com.golemaddons.combat.BoneSwap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once a frame, so Bone swap keeps delays shorter than a game tick. */
@Mixin(Minecraft.class)
abstract class MinecraftFrameMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void golemaddons$frame(boolean advanceGameTime, CallbackInfo ci) {
		BoneSwap.instance().pump((Minecraft) (Object) this);
	}
}
