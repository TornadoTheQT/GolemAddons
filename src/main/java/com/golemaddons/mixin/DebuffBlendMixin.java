package com.golemaddons.mixin;

import com.golemaddons.render.Debuffs;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disable debuffs: the effect "blend factor" only drives screen effects (nausea wobble and overlay, darkness
 * dimming), so on the client it reads as 0 for hidden effects. The effect itself is untouched.
 */
@Mixin(LivingEntity.class)
abstract class DebuffBlendMixin {
	@Inject(method = "getEffectBlendFactor", at = @At("HEAD"), cancellable = true)
	private void golemaddons$noBlend(Holder<MobEffect> effect, float partialTicks, CallbackInfoReturnable<Float> cir) {
		if (((LivingEntity) (Object) this).level().isClientSide() && Debuffs.hides(effect)) cir.setReturnValue(0f);
	}
}
