package com.golemaddons.mixin;

import com.golemaddons.terminals.TerminalFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the compact terminal GUI is showing, the chest itself (slots, labels, tooltips) is not drawn. */
@Mixin(AbstractContainerScreen.class)
abstract class TerminalScreenMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void golemaddons$hideTerminalChest(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (TerminalFeature.customGui((Screen) (Object) this)) ci.cancel();
	}
}
