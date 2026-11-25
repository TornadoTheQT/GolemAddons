package com.golemaddons.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The pending-click counter a physical key or mouse press increments; vanilla consumes it once per click. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int golemaddons$getClickCount();

	@Accessor("clickCount")
	void golemaddons$setClickCount(int count);
}
