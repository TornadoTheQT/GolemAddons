package com.golemaddons.autoclick;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Left-clicks while its key is held, at a random rate between the configured minimum and maximum CPS.
 *
 * <p>Each click is added to the attack key's pending clicks, exactly what a physical mouse click does, so vanilla
 * then handles it the normal way during the same tick (attack cooldown, miss delay, block breaking, swing). Only
 * runs in game with no screen open and the window focused.
 */
public final class AutoClickerFeature {
	private final KeyMapping holdKey;
	private final ClickScheduler scheduler = new ClickScheduler(() -> ThreadLocalRandom.current().nextDouble());
	private volatile boolean enabled;
	private volatile int minCps = 8;
	private volatile int maxCps = 12;

	public AutoClickerFeature(KeyMapping.Category category) {
		this.holdKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.golemaddons.autoclicker", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
	}

	public KeyMapping holdKey() {
		return holdKey;
	}

	public void register(ConfigManager config) {
		config.addListener(c -> {
			enabled = c.autoClicker.enabled;
			minCps = c.autoClicker.minCps;
			maxCps = c.autoClicker.maxCps;
		});
		// Start of tick: clicks added now are handled by vanilla's key handling later in this same tick.
		ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
	}

	private void onTick(Minecraft mc) {
		if (!enabled || holdKey.isUnbound() || !holdKey.isDown() || mc.player == null || mc.screen != null
				|| mc.gameMode == null || !mc.isWindowActive()) {
			scheduler.reset();
			return;
		}
		int clicks = scheduler.due(System.nanoTime(), minCps, maxCps);
		if (clicks > 0) {
			KeyMappingAccessor attack = (KeyMappingAccessor) mc.options.keyAttack;
			attack.golemaddons$setClickCount(attack.golemaddons$getClickCount() + clicks);
		}
	}
}
