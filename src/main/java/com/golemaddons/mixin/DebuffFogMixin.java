package com.golemaddons.mixin;

import com.golemaddons.render.Debuffs;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Disable debuffs: no blindness or darkness fog. Render only. */
@Mixin(MobEffectFogEnvironment.class)
abstract class DebuffFogMixin {
	@Shadow
	public abstract Holder<MobEffect> getMobEffect();

	@Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
	private void golemaddons$noEffectFog(FogType fogType, Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Debuffs.hides(getMobEffect())) cir.setReturnValue(false);
	}
}
