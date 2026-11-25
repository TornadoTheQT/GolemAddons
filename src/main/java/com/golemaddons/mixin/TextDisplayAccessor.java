package com.golemaddons.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets cosmetics drop a text display's laid-out lines so they are rebuilt with newly loaded custom names. */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
	@Accessor("clientDisplayCache")
	void golemaddons$setClientDisplayCache(Display.TextDisplay.CachedInfo cache);
}
