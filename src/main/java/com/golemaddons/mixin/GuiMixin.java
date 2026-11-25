package com.golemaddons.mixin;

import com.golemaddons.cosmetics.CosmeticsFeature;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Scoreboard sidebar: each line's team-formatted text (where servers put "[RANK] username") gets custom names.
 * The line is rebuilt every frame, so the rewriter's memo keeps this cheap.
 */
@Mixin(Gui.class)
abstract class GuiMixin {
	@ModifyExpressionValue(method = "lambda$displayScoreboardSidebar$1", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;"
					+ "Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"))
	private MutableComponent golemaddons$customNames(MutableComponent line) {
		CosmeticsFeature feature = CosmeticsFeature.instance();
		if (feature == null) {
			return line;
		}
		Component rewritten = feature.rewriteNames(line);
		return rewritten instanceof MutableComponent mutable ? mutable : line;
	}
}
