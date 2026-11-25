package com.golemaddons.mixin;

import com.golemaddons.cosmetics.CosmeticsFeature;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Text display entities (how modern servers draw nametags and holograms): the text is rewritten when vanilla lays it
 * out into lines, which only happens when the text changes (or when custom names change; see TextDisplayAccessor).
 */
@Mixin(DisplayRenderer.TextDisplayRenderer.class)
abstract class TextDisplayRendererMixin {
	@ModifyVariable(method = "splitLines", at = @At("HEAD"), argsOnly = true)
	private Component golemaddons$customNames(Component text) {
		CosmeticsFeature feature = CosmeticsFeature.instance();
		return feature == null ? text : feature.rewriteNames(text);
	}
}
