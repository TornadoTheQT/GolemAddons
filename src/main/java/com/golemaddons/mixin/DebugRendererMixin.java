package com.golemaddons.mixin;

import com.golemaddons.render.DragonHitboxRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds dragon hitboxes to the per-frame gizmos, right after vanilla's debug renderers emit theirs. */
@Mixin(DebugRenderer.class)
abstract class DebugRendererMixin {
	@Inject(method = "emitGizmos", at = @At("TAIL"))
	private void golemaddons$emitDragonHitboxes(Frustum frustum, double camX, double camY, double camZ, float partialTick,
			CallbackInfo ci) {
		DragonHitboxRenderer.emit(frustum, partialTick);
		com.golemaddons.boss.Bosses.emitGizmos(frustum, partialTick);
	}
}
