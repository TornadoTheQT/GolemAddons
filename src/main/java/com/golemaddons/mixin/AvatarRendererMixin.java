package com.golemaddons.mixin;

import com.golemaddons.cosmetics.CosmeticsFeature;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies a player's custom cape to their render state after vanilla fills it: it replaces the cape and elytra
 * texture once loaded. (Names are handled for all entities in EntityRendererMixin.) With no custom cape the state is
 * left exactly as vanilla made it.
 */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void golemaddons$applyCosmetics(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (!(entity instanceof Player player)) {
			return;
		}
		CosmeticsFeature feature = CosmeticsFeature.instance();
		CosmeticsFeature.Cosmetic cosmetic = feature == null ? null : feature.lookup(player.getUUID());
		if (cosmetic == null) {
			return;
		}
		if (cosmetic.capeHash() != null && state.skin != null) {
			state.skin = feature.skinWithCape(player.getUUID(), state.skin, cosmetic.capeHash());
		}
	}
}
