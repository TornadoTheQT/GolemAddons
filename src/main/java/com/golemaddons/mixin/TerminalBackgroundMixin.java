package com.golemaddons.mixin;

import com.golemaddons.terminals.TerminalFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Behind the compact terminal GUI: a plain dimmed world instead of the chest texture. */
@Mixin(ContainerScreen.class)
abstract class TerminalBackgroundMixin {
	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void golemaddons$terminalBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (TerminalFeature.customGui(self)) {
			self.extractTransparentBackground(graphics);
			ci.cancel();
		}
	}
}
