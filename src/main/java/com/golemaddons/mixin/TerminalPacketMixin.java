package com.golemaddons.mixin;

import com.golemaddons.terminals.TerminalFeature;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the terminal solver when the server has answered a click. Only reads the packets after the game has
 * handled them (TAIL, on the main thread); nothing is changed or sent.
 */
@Mixin(ClientPacketListener.class)
abstract class TerminalPacketMixin {
	@Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
	private void golemaddons$terminalSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
		TerminalFeature.serverSlot(packet.getContainerId(), packet.getSlot());
	}

	@Inject(method = "handleContainerContent", at = @At("TAIL"))
	private void golemaddons$terminalContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
		TerminalFeature.serverContent(packet.containerId());
	}
}
