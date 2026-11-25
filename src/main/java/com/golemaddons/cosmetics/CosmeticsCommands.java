package com.golemaddons.cosmetics;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * {@code /ga custom name|cape ...}. Client-side commands: they never reach the server, and win over a server
 * command with the same name. Each one validates locally, prints "Updating…" and returns at once; the result
 * arrives in chat when the server answers. URLs are only ever shown as plain text, never as clickable links.
 */
final class CosmeticsCommands {
	private CosmeticsCommands() {
	}

	static void register(CosmeticsFeature feature) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
				literal("ga").then(literal("custom")
						.then(literal("name")
								.then(literal("reset").executes(ctx -> change(ctx, feature, "name", null)))
								.then(argument("text", StringArgumentType.greedyString())
										.executes(ctx -> setName(ctx, feature, StringArgumentType.getString(ctx, "text")))))
						.then(literal("cape")
								.then(literal("reset").executes(ctx -> change(ctx, feature, "capeUrl", null)))
								.then(argument("url", StringArgumentType.greedyString())
										.executes(ctx -> setCape(ctx, feature, StringArgumentType.getString(ctx, "url")))))
						.then(literal("status").executes(ctx -> status(ctx, feature))))));
	}

	private static int setName(CommandContext<FabricClientCommandSource> ctx, CosmeticsFeature feature, String text) {
		CosmeticName.Result name = CosmeticName.parse(text);
		if (!name.valid()) {
			ctx.getSource().sendError(Component.translatable(
					"golemaddons.cosmetics.name." + name.problem().name().toLowerCase(Locale.ROOT), name.detail()));
			return 0;
		}
		ctx.getSource().sendFeedback(Component.translatable("golemaddons.cosmetics.name.preview")
				.append(CosmeticText.build(name.runs())));
		return change(ctx, feature, "name", name.raw());
	}

	private static int setCape(CommandContext<FabricClientCommandSource> ctx, CosmeticsFeature feature, String url) {
		CapeUrlRules.Result result = CapeUrlRules.check(url, feature.apiHost());
		if (!result.valid()) {
			ctx.getSource().sendError(Component.translatable(
					"golemaddons.cosmetics.cape." + result.problem().name().toLowerCase(Locale.ROOT)));
			return 0;
		}
		return change(ctx, feature, "capeUrl", result.url());
	}

	private static int change(CommandContext<FabricClientCommandSource> ctx, CosmeticsFeature feature, String key, String value) {
		Map<String, String> changes = new HashMap<>();
		changes.put(key, value);
		if (!feature.update(changes)) {
			return 0;
		}
		ctx.getSource().sendFeedback(Component.translatable("golemaddons.cosmetics.updating").withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int status(CommandContext<FabricClientCommandSource> ctx, CosmeticsFeature feature) {
		FabricClientCommandSource source = ctx.getSource();
		if (!feature.active()) {
			source.sendFeedback(Component.translatable(feature.hasBuiltInServer()
					? "golemaddons.cosmetics.error.disabled" : "golemaddons.cosmetics.error.no_server").withStyle(ChatFormatting.YELLOW));
			return 1;
		}
		CosmeticsFeature.Cosmetic own = feature.own();
		source.sendFeedback(Component.translatable("golemaddons.cosmetics.status.server", feature.serverDescription()));
		source.sendFeedback(Component.translatable("golemaddons.cosmetics.status.name").append(
				own == null || own.name() == null ? Component.translatable("golemaddons.cosmetics.status.none") : own.name()));
		source.sendFeedback(Component.translatable("golemaddons.cosmetics.status.cape",
				own == null || own.capeHash() == null ? Component.translatable("golemaddons.cosmetics.status.none")
						: Component.literal(own.capeHash().substring(0, 12))));
		if (feature.paused()) {
			source.sendFeedback(Component.translatable("golemaddons.cosmetics.status.paused").withStyle(ChatFormatting.YELLOW));
		}
		if (!feature.lastError().isEmpty()) {
			source.sendFeedback(Component.translatable("golemaddons.cosmetics.status.last_error", feature.lastError())
					.withStyle(ChatFormatting.GRAY));
		}
		feature.checkHealth(ok -> source.sendFeedback(Component.translatable(ok
				? "golemaddons.cosmetics.status.reachable" : "golemaddons.cosmetics.status.unreachable")
				.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)));
		return 1;
	}
}
