package com.golemaddons.mixin;

import com.golemaddons.render.Debuffs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Disable debuffs: no carved-pumpkin blur or freezing frame over the screen. */
@Mixin(Gui.class)
abstract class DebuffOverlayMixin {
	@WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Gui;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V"))
	private void golemaddons$overlay(Gui gui, GuiGraphicsExtractor graphics, Identifier texture, float alpha, Operation<Void> original) {
		if (!Debuffs.hidesOverlay(texture)) original.call(gui, graphics, texture, alpha);
	}
}
