package com.golemaddons.mixin;

import com.golemaddons.render.Debuffs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Disable debuffs: blindness and darkness no longer hide the sky. Camera render state only. */
@Mixin(Camera.class)
abstract class DebuffCameraMixin {
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
	private boolean golemaddons$skyNotBlocked(LivingEntity entity, Holder<MobEffect> effect, Operation<Boolean> original) {
		return !Debuffs.hides(effect) && original.call(entity, effect);
	}
}
