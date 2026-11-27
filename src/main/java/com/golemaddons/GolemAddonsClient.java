package com.golemaddons;

import com.golemaddons.animation.AnimationFeature;
import com.golemaddons.animation.HeldItemPreviewRenderer;
import com.golemaddons.autoclick.AutoClickerFeature;
import com.golemaddons.autosell.AutoSellFeature;
import com.golemaddons.camera.CameraFeature;
import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.cosmetics.CosmeticsFeature;
import com.golemaddons.partyfinder.PartyFinderFeature;
import com.golemaddons.render.DragonHitboxRenderer;
import com.golemaddons.terminals.TerminalFeature;
import com.golemaddons.afk.AfkFeature;
import com.golemaddons.ui.GolemHubScreen;
import com.golemaddons.update.UpdateChecker;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class GolemAddonsClient implements ClientModInitializer {
	public static final String MOD_ID = "golemaddons";

	private static ConfigManager config;
	private static KeyMapping openMenu;
	private static KeyMapping toggleHitboxes;
	private static AutoSellFeature autoSell;
	private static AutoClickerFeature autoClicker;

	public static ConfigManager config() {
		return config;
	}

	/** The key currently bound to open the GolemAddons menu, for on-screen hints. */
	public static Component openMenuKeyName() {
		return openMenu.getTranslatedKeyMessage();
	}

	/** The key held to autoclick, rebindable from the Auto Clicker screen. */
	public static KeyMapping autoClickerKey() {
		return autoClicker.holdKey();
	}

	/** The quick-sell key (run the shop command, sell, close), rebindable from Sell Settings. */
	public static KeyMapping quickSellKey() {
		return autoSell.quickSellKey();
	}

	@Override
	public void onInitializeClient() {
		config = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.golemaddons.config", InputConstants.Type.KEYSYM, InputConstants.KEY_K, category));

		// Features: each owns its own registrations. Add new ones here.
		autoSell = new AutoSellFeature(category);
		autoSell.register(config);
		toggleHitboxes = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.golemaddons.dragon_hitboxes", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		config.addListener(c -> DragonHitboxRenderer.setEnabled(c.render.dragonHitboxes));
		new CosmeticsFeature().register(config);
		new PartyFinderFeature().register(config);
		autoClicker = new AutoClickerFeature(category);
		autoClicker.register(config);
		CameraFeature.register(config);
		AnimationFeature.register(config);
		TerminalFeature.register(config);
		AfkFeature.instance().register(config);
		com.golemaddons.boss.Bosses.register(config);
		com.golemaddons.combat.BoneSwap.instance().register(config, category);
		PictureInPictureRendererRegistry.register(ctx -> new HeldItemPreviewRenderer(ctx.bufferSource()));
		UpdateChecker.instance().register(config);

		config.load();

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openMenu.consumeClick()) {
				if (mc.screen == null) {
					mc.setScreen(new GolemHubScreen(null));
				}
			}
			while (toggleHitboxes.consumeClick()) {
				GolemConfig next = config.get().copy();
				next.render.dragonHitboxes = !next.render.dragonHitboxes;
				if (config.save(next)) {
					mc.gui.setOverlayMessage(Component.translatable(next.render.dragonHitboxes
							? "golemaddons.hitboxes.on" : "golemaddons.hitboxes.off"), false);
				}
			}
		});
	}
}
