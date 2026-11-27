package com.golemaddons.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets an embedded settings pane move its tooltip along with the pane (tooltips ignore the pose). */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {
	@Accessor("deferredTooltip")
	Runnable golemaddons$deferredTooltip();

	@Accessor("deferredTooltip")
	void golemaddons$setDeferredTooltip(Runnable tooltip);
}
