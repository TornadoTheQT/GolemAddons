package com.golemaddons.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the container GUI's position, so the sell button can sit beside it without covering slots. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int golemaddons$getLeftPos();

	@Accessor("topPos")
	int golemaddons$getTopPos();

	@Accessor("imageWidth")
	int golemaddons$getImageWidth();

	@Accessor("imageHeight")
	int golemaddons$getImageHeight();
}
