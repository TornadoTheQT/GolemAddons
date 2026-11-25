package com.golemaddons.mixin;

import com.golemaddons.animation.AnimationFeature;
import com.golemaddons.animation.HeldItemAnimation;
import com.golemaddons.animation.HeldItemTransform;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swing speed (as in OdinLegacy): the local player's swing animation takes a different number of ticks. This is the
 * client's own swing animation only; attacks, attack cooldown and swing packets don't depend on it.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntitySwingSpeedMixin {
	@ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
	private int golemaddons$swingSpeed(int vanilla) {
		LivingEntity self = (LivingEntity) (Object) this;
		HeldItemAnimation a = AnimationFeature.active();
		if (a == null || self != Minecraft.getInstance().player) return vanilla;
		int base = vanilla;
		if (a.ignoreHaste) {
			InteractionHand hand = self.swingingArm != null ? self.swingingArm : InteractionHand.MAIN_HAND;
			base = self.getItemInHand(hand).getSwingAnimation().duration();
		}
		return HeldItemTransform.swingDuration(base, a);
	}
}
