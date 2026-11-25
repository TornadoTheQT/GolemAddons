package com.golemaddons.mixin;

import com.golemaddons.cosmetics.CosmeticsFeature;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Chat: every line passes through here once as it arrives (after signature checks), so custom names replace
 * "[RANK] username" in it once, not every frame. Lines received before a custom name was known stay as they were.
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
	@ModifyVariable(
			method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
					+ "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true)
	private Component golemaddons$customNames(Component message) {
		CosmeticsFeature feature = CosmeticsFeature.instance();
		return feature == null ? message : feature.rewriteNames(message);
	}
}
