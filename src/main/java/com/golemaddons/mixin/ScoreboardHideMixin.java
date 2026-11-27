package com.golemaddons.mixin;

import com.golemaddons.scoreboard.ScoreboardFeature;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Hide server's" sidebar: while the custom scoreboard is showing, the client's scoreboard reports no sidebar
 * objective to anything but GolemAddons, so the vanilla sidebar and other mods' (e.g. a client's own scoreboard
 * module) draw nothing. Client scoreboard only; the data itself is untouched.
 */
@Mixin(Scoreboard.class)
abstract class ScoreboardHideMixin {
	@Inject(method = "getDisplayObjective", at = @At("HEAD"), cancellable = true)
	private void golemaddons$hideSidebar(DisplaySlot slot, CallbackInfoReturnable<Objective> cir) {
		if (slot == DisplaySlot.LIST || slot == DisplaySlot.BELOW_NAME) return;
		if ((Object) this instanceof ServerScoreboard) return;
		if (ScoreboardFeature.hidesServerSidebar()) cir.setReturnValue(null);
	}

	// Removing an objective must still see the real display slots, or a removed sidebar would linger.
	@Inject(method = "removeObjective", at = @At("HEAD"))
	private void golemaddons$removeStart(Objective objective, CallbackInfo ci) {
		ScoreboardFeature.beginRead();
	}

	@Inject(method = "removeObjective", at = @At("RETURN"))
	private void golemaddons$removeEnd(Objective objective, CallbackInfo ci) {
		ScoreboardFeature.endRead();
	}
}
