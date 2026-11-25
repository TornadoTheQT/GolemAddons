package com.golemaddons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lets GolemAddons' own text boxes draw a styled frame instead of the vanilla texture. Other text boxes are untouched. */
@Mixin(EditBox.class)
abstract class EditBoxMixin {
	@WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void golemaddons$frame(GuiGraphicsExtractor graphics, com.mojang.blaze3d.pipeline.RenderPipeline pipeline,
			net.minecraft.resources.Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
		if (!com.golemaddons.ui.GolemFrames.drawEditBoxFrame((EditBox) (Object) this, graphics)) {
			original.call(graphics, pipeline, sprite, x, y, w, h);
		}
	}
}
