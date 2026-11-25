package com.golemaddons.mixin;

import com.golemaddons.animation.AnimationFeature;
import com.golemaddons.animation.HeldItemAnimation;
import com.golemaddons.animation.HeldItemTransform;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Held item animations (first person only), following OdinLegacy's Animations module: vanilla's resting position and
 * swing are replaced by {@link HeldItemTransform}. Rendering only: no packets, no attack timing. With the feature off
 * every hook passes straight through.
 */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
	@Shadow
	private ItemStack mainHandItem;
	@Shadow
	private ItemStack offHandItem;

	/** Swing progress of the item about to be drawn, set where vanilla would have swung it; 0 when it doesn't swing. */
	@Unique
	private float golemaddons$attack;

	/** Resting position (every place vanilla uses it: plain items, eating, blocking, crossbows, riptide). */
	@WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"))
	private void golemaddons$base(ItemInHandRenderer self, PoseStack pose, HumanoidArm arm, float inverseArmHeight, Operation<Void> original) {
		HeldItemAnimation a = AnimationFeature.active();
		if (a != null) {
			HeldItemTransform.applyBase(pose, a, arm == HumanoidArm.RIGHT, inverseArmHeight);
		} else {
			original.call(self, pose, arm, inverseArmHeight);
		}
	}

	/** The swing itself is drawn with the item (below), after its rotation, like Odin. */
	@WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"))
	private void golemaddons$swing(ItemInHandRenderer self, float attack, PoseStack pose, int invert, HumanoidArm arm, Operation<Void> original) {
		if (AnimationFeature.active() != null) {
			golemaddons$attack = attack;
		} else {
			original.call(self, attack, pose, invert, arm);
		}
	}

	@WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;"
					+ "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;"
					+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
	private void golemaddons$item(ItemInHandRenderer self, LivingEntity entity, ItemStack stack, ItemDisplayContext context,
			PoseStack pose, SubmitNodeCollector collector, int light, Operation<Void> original) {
		HeldItemAnimation a = AnimationFeature.active();
		float attack = golemaddons$attack;
		golemaddons$attack = 0f;
		if (a != null) {
			HeldItemTransform.applyItem(pose, a, context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND, attack);
		}
		original.call(self, entity, stack, context, pose, collector, light);
	}

	/** No equip reset for the bare hand too. */
	@WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;"
					+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V"))
	private void golemaddons$bareHand(ItemInHandRenderer self, PoseStack pose, SubmitNodeCollector collector, int light,
			float inverseArmHeight, float attack, HumanoidArm arm, Operation<Void> original) {
		HeldItemAnimation a = AnimationFeature.active();
		original.call(self, pose, collector, light, a != null && a.noEquipReset ? 0f : inverseArmHeight, attack, arm);
	}

	/** No equip reset: also show a newly selected item straight away instead of after the (now hidden) dip. */
	@Inject(method = "tick", at = @At("TAIL"))
	private void golemaddons$noEquipReset(CallbackInfo ci) {
		HeldItemAnimation a = AnimationFeature.active();
		LocalPlayer player = Minecraft.getInstance().player;
		if (a != null && a.noEquipReset && player != null) {
			mainHandItem = player.getMainHandItem();
			offHandItem = player.getOffhandItem();
		}
	}
}
