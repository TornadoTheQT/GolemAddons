package com.golemaddons.boss;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;

/**
 * Runs boss helpers: reads the sidebar a few times a second to know which fight is on, and passes chat lines, ticks
 * and frames to that boss's helpers. To add a helper, implement {@link BossFeature} and add it to {@link #FEATURES}.
 */
public final class Bosses {
	private static final List<BossFeature> FEATURES = List.of(new RiftbornAnchors(), new AutoAttunement());
	private static final int SIDEBAR_EVERY_TICKS = 5;

	private static volatile GolemConfig config = GolemConfig.defaults();
	private static final Set<BossType> active = EnumSet.noneOf(BossType.class);
	private static int ticks;

	private Bosses() {
	}

	public static void register(ConfigManager manager) {
		manager.addListener(c -> config = c.copy());
		ClientTickEvents.END_CLIENT_TICK.register(Bosses::tick);
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
				net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("gaseraph")
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("item").executes(c -> debugItem()))
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("target").executes(c -> debugTarget()))
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("status").executes(c -> debugStatus()))));
		ClientReceiveMessageEvents.GAME.register(Bosses::onMessage);
		// The chat cleaner may hide boss messages; the helpers still need them.
		ClientReceiveMessageEvents.GAME_CANCELED.register(Bosses::onMessage);
	}

	private static void onMessage(net.minecraft.network.chat.Component message, boolean overlay) {
		if (active.isEmpty()) return;
		String line = Sidebar.clean(message.getString());
		for (BossFeature f : FEATURES) {
			if (!active.contains(f.boss())) continue;
			if (overlay) f.onActionBar(line, config);
			else f.onChat(line, config);
		}
	}

	/** Anchors outlined right now (for the in-game test). */
	public static int anchorCount() {
		for (BossFeature f : FEATURES) if (f instanceof RiftbornAnchors a) return a.size();
		return 0;
	}

	public static boolean active(BossType boss) {
		return active.contains(boss);
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			if (!active.isEmpty()) end(EnumSet.copyOf(active));
			return;
		}
		if (++ticks % SIDEBAR_EVERY_TICKS == 0) {
			List<String> lines = Sidebar.lines(mc);
			Set<BossType> ended = EnumSet.noneOf(BossType.class);
			for (BossType boss : BossType.values()) {
				if (boss.matches(lines)) active.add(boss);
				else if (active.contains(boss)) ended.add(boss);
			}
			end(ended);
		}
		for (BossFeature f : FEATURES) {
			if (active.contains(f.boss())) f.onTick(mc, config);
		}
	}

	private static void end(Set<BossType> ended) {
		if (ended.isEmpty()) return;
		active.removeAll(ended);
		for (BossFeature f : FEATURES) {
			if (ended.contains(f.boss())) f.fightEnded();
		}
	}

	// ---- /gaseraph: shows what the helpers read, to check them against the real fight -------------------------

	private static int debugItem() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return 0;
		var stack = mc.player.getMainHandItem();
		say("Held: " + stack.getHoverName().getString());
		for (String line : AutoAttunement.lore(stack)) say("  " + line);
		for (Attunement.Dagger d : Attunement.Dagger.values()) {
			if (d.isIn(stack.getHoverName().getString())) {
				Attunement a = Attunement.current(d, stack.getHoverName().getString(), AutoAttunement.lore(stack));
				say("Read as: " + d + " in " + (a == null ? "unknown attunement" : a));
			}
		}
		return 1;
	}

	private static int debugTarget() {
		Minecraft mc = Minecraft.getInstance();
		var target = mc.crosshairPickEntity;
		if (target == null) {
			say("Not aiming at anything.");
			return 1;
		}
		say("Aiming at: " + target.getType().getDescription().getString());
		for (String name : AutoAttunement.namesAround(mc, target)) say("  tag: " + name);
		Attunement mark = AutoAttunement.targetMark(mc);
		say("Mark: " + (mark == null ? "none found" : mark + " -> " + mark.dagger()));
		return 1;
	}

	private static int debugStatus() {
		say("Fights on: " + (active.isEmpty() ? "none" : active));
		for (String line : Sidebar.lines(Minecraft.getInstance())) say("  " + line);
		return 1;
	}

	private static void say(String text) {
		Minecraft.getInstance().gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(text)
				.withStyle(net.minecraft.ChatFormatting.GRAY));
	}

	/** From the debug renderer, once per frame. */
	public static void emitGizmos(Frustum frustum, float partialTick) {
		if (active.isEmpty()) return;
		for (BossFeature f : FEATURES) {
			if (active.contains(f.boss())) f.emitGizmos(frustum, partialTick, config);
		}
	}
}
