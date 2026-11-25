package com.golemaddons.mixin;

import com.golemaddons.cosmetics.CosmeticsFeature;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nametags of every entity, not just players: servers usually show "[RANK] username" on a named armor stand or
 * similar entity riding the player (that is the nametag you see on yourself in third person, and the one others see
 * on you). Only the text vanilla already decided to show is changed; with no custom names around this is a no-op.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"))
	private void golemaddons$customNames(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		if (state.nameTag == null) {
			return;
		}
		CosmeticsFeature feature = CosmeticsFeature.instance();
		if (feature != null) {
			state.nameTag = feature.rewriteNames(state.nameTag);
		}
	}
}
