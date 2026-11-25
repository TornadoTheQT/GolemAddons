package com.golemaddons.mixin;

import com.golemaddons.cosmetics.CosmeticsFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tab list: custom names replace usernames (and a [RANK] right before them), including in names the server sets.
 * A player whose own name was replaced gets their real username after it in dark gray, so nobody can pass as
 * someone else.
 */
@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void golemaddons$customName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		CosmeticsFeature feature = CosmeticsFeature.instance();
		if (feature == null) {
			return;
		}
		Component original = cir.getReturnValue();
		Component replaced = feature.rewriteNames(original);
		if (replaced == original) {
			return;
		}
		CosmeticsFeature.Cosmetic own = feature.lookup(info.getProfile().id());
		cir.setReturnValue(own == null || own.name() == null ? replaced : Component.empty().append(replaced)
				.append(Component.literal(" (" + info.getProfile().name() + ")").withStyle(ChatFormatting.DARK_GRAY)));
	}
}
