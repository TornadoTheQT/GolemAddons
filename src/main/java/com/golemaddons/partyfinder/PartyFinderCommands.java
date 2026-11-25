package com.golemaddons.partyfinder;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * {@code /gapf} opens the finder; {@code /gapf accept|deny <id>} answers a join request (the chat buttons fill
 * these in); {@code /gapf leave}, {@code disband}, {@code refresh}. Client-side only: never sent to the server.
 */
final class PartyFinderCommands {
	private PartyFinderCommands() {
	}

	static void register(PartyFinderFeature feature) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(literal("gapf")
				.executes(ctx -> {
					feature.openScreenNextTick(); // the chat screen closes after this command runs
					return 1;
				})
				.then(literal("accept").then(argument("id", StringArgumentType.word())
						.executes(ctx -> respond(ctx, feature, true))))
				.then(literal("deny").then(argument("id", StringArgumentType.word())
						.executes(ctx -> respond(ctx, feature, false))))
				.then(literal("leave").executes(ctx -> result(ctx, feature.memberOf() != null && feature.leave(), "golemaddons.pf.error.not_member")))
				.then(literal("disband").executes(ctx -> result(ctx, feature.leading() != null && feature.disband(), "golemaddons.pf.error.not_leader")))
				.then(literal("refresh").executes(ctx -> result(ctx, feature.refresh(), "golemaddons.pf.error.offline")))));
	}

	private static int respond(CommandContext<FabricClientCommandSource> ctx, PartyFinderFeature feature, boolean accept) {
		String id = StringArgumentType.getString(ctx, "id");
		if (!feature.hasIncoming(id)) {
			ctx.getSource().sendError(Component.translatable("golemaddons.pf.error.no_request"));
			return 0;
		}
		return result(ctx, feature.respond(id, accept), "golemaddons.pf.error.offline");
	}

	private static int result(CommandContext<FabricClientCommandSource> ctx, boolean ok, String errorKey) {
		if (!ok) {
			ctx.getSource().sendError(Component.translatable(errorKey));
			return 0;
		}
		return 1;
	}
}
