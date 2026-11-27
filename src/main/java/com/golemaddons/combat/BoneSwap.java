package com.golemaddons.combat;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;

/**
 * Bone swap: one key press throws every Bonemerang in the hotbar, left to right - select its slot, right-click,
 * wait the delay (give or take the variation), next one - and then selects the end slot (the slot you started on,
 * or a chosen one). Timed every frame, so short delays are kept. Pressing the key again while it runs does nothing.
 */
public final class BoneSwap {
	private static final BoneSwap INSTANCE = new BoneSwap();

	private final Random random = new Random();
	private volatile GolemConfig.BoneSwap settings = new GolemConfig.BoneSwap();
	private KeyMapping key;
	private List<Integer> queue = List.of();
	private int index;
	private int returnSlot;
	private long nextAt;
	private boolean running;

	private BoneSwap() {
	}

	public static BoneSwap instance() {
		return INSTANCE;
	}

	public KeyMapping key() {
		return key;
	}

	public void register(ConfigManager config, KeyMapping.Category category) {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.golemaddons.boneswap", InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(), category));
		config.addListener(c -> settings = c.boneSwap.copy());
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.consumeClick()) {
				if (!running && settings.enabled) start(mc);
			}
			pump(mc);
		});
	}

	/** The hotbar slots (0-8) holding an item whose name contains {@code fragment}, left to right. */
	static List<Integer> matchingSlots(List<String> hotbarNames, String fragment) {
		List<Integer> out = new ArrayList<>();
		String f = fragment.toLowerCase(Locale.ROOT);
		for (int i = 0; i < hotbarNames.size() && i < 9; i++) {
			String n = hotbarNames.get(i);
			if (n != null && n.toLowerCase(Locale.ROOT).contains(f)) out.add(i);
		}
		return out;
	}

	/** A delay with the variation applied, never below zero. */
	static long delay(int delayMs, int variationMs, Random random) {
		int v = variationMs <= 0 ? 0 : random.nextInt(2 * variationMs + 1) - variationMs;
		return Math.max(0, delayMs + v);
	}

	private void start(Minecraft mc) {
		if (mc.player == null || mc.screen != null) return;
		var inventory = mc.player.getInventory();
		List<String> names = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			var stack = inventory.getItem(i);
			names.add(stack.isEmpty() ? null : stack.getHoverName().getString());
		}
		queue = matchingSlots(names, settings.itemName);
		if (queue.isEmpty()) return;
		returnSlot = settings.endSlot == 0 ? inventory.getSelectedSlot() : settings.endSlot - 1;
		index = 0;
		nextAt = Util.getMillis();
		running = true;
		pump(mc);
	}

	/** Called every tick and every frame: throws the next bone when its time has come. */
	public void pump(Minecraft mc) {
		if (!running) return;
		if (mc.player == null || mc.gameMode == null) {
			running = false;
			return;
		}
		long now = Util.getMillis();
		while (running && now >= nextAt) {
			var inventory = mc.player.getInventory();
			if (index < queue.size()) {
				inventory.setSelectedSlot(queue.get(index++));
				if (mc.screen == null) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
				nextAt = now + delay(settings.delayMs, settings.variationMs, random);
				if (nextAt > now) break;
			} else {
				inventory.setSelectedSlot(returnSlot);
				running = false;
			}
		}
	}
}
