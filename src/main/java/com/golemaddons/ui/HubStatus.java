package com.golemaddons.ui;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.autosell.RuleValidator;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.cosmetics.CosmeticsFeature;
import com.golemaddons.dragon.DragonCatalog;
import com.golemaddons.partyfinder.PartyFinderFeature;
import java.util.Arrays;
import net.minecraft.network.chat.Component;

/** The one-line status shown on each hub card, for features and for the categories that group them. */
final class HubStatus {
	private HubStatus() {
	}

	private static GolemConfig config() {
		return GolemAddonsClient.config().get();
	}

	private static HubCard.Status off() {
		return new HubCard.Status(Component.translatable("golemaddons.hub.off"), UiText.GRAY);
	}

	private static boolean sellTitleSet() {
		return RuleValidator.validateScreenTitlePattern(config().selling.sellScreenTitlePattern).valid();
	}

	static HubCard.Status mining() {
		GolemConfig config = config();
		int total = config.miningAutoSell.rules.size();
		long on = config.miningAutoSell.rules.stream().filter(r -> r.enabled).count();
		return on > 0
				? new HubCard.Status(Component.translatable("golemaddons.hub.mining.status", on, total), UiText.GREEN)
				: new HubCard.Status(Component.translatable("golemaddons.hub.mining.status.none"), UiText.GRAY);
	}

	static HubCard.Status dragon() {
		GolemConfig.DragonSeller s = config().dragonSeller;
		if (!s.enabled) return off();
		long dragons = Arrays.stream(DragonCatalog.DragonType.values()).filter(s::sellsDragon).count();
		return new HubCard.Status(Component.translatable("golemaddons.hub.dragon.status", dragons,
				DragonCatalog.DragonType.values().length), UiText.ACCENT_2);
	}

	static HubCard.Status sellSettings() {
		return sellTitleSet()
				? new HubCard.Status(Component.translatable("golemaddons.hub.settings.status.ok"), UiText.GREEN)
				: new HubCard.Status(Component.translatable("golemaddons.hub.settings.status.missing"), UiText.YELLOW);
	}

	/** Selling category: the missing sell title first (nothing sells without it), then how many sellers are on. */
	static HubCard.Status selling() {
		if (!sellTitleSet()) return sellSettings();
		GolemConfig config = config();
		int on = (config.miningAutoSell.rules.stream().anyMatch(r -> r.enabled) ? 1 : 0) + (config.dragonSeller.enabled ? 1 : 0);
		return on > 0 ? countOn(on, 2) : off();
	}

	static HubCard.Status autoClicker() {
		GolemConfig.AutoClicker s = config().autoClicker;
		if (!s.enabled) return off();
		if (GolemAddonsClient.autoClickerKey().isUnbound()) {
			return new HubCard.Status(Component.translatable("golemaddons.hub.autoclicker.no_key"), UiText.YELLOW);
		}
		return new HubCard.Status(Component.translatable("golemaddons.hub.autoclicker.status", s.minCps, s.maxCps), UiText.GREEN);
	}

	private static boolean cameraOn() {
		GolemConfig.Camera c = config().camera;
		return c.enabled && (c.disableFrontView || c.customDistance || c.clipThroughBlocks);
	}

	static HubCard.Status camera() {
		if (!cameraOn()) return off();
		GolemConfig.Camera c = config().camera;
		int n = (c.disableFrontView ? 1 : 0) + (c.customDistance ? 1 : 0) + (c.clipThroughBlocks ? 1 : 0);
		return new HubCard.Status(Component.translatable("golemaddons.hub.camera.status", n), UiText.GREEN);
	}

	static HubCard.Status terminals() {
		GolemConfig.Terminals t = config().terminals;
		if (!t.enabled) return off();
		if (t.autoTerms) return new HubCard.Status(Component.translatable("golemaddons.hub.terminals.status.auto"), UiText.RED);
		return new HubCard.Status(Component.translatable(t.hoverClick ? "golemaddons.hub.terminals.status.hover"
				: t.customGui ? "golemaddons.hub.terminals.status.gui" : "golemaddons.hub.terminals.status.highlight"), UiText.GREEN);
	}

	static HubCard.Status qol() {
		boolean clicker = config().autoClicker.enabled && !GolemAddonsClient.autoClickerKey().isUnbound();
		int on = (clicker ? 1 : 0) + (cameraOn() ? 1 : 0) + (config().terminals.enabled ? 1 : 0);
		return on > 0 ? countOn(on, 3) : off();
	}

	private static boolean cosmeticsOn() {
		CosmeticsFeature feature = CosmeticsFeature.instance();
		return feature != null && feature.active();
	}

	static HubCard.Status cosmetics() {
		if (!cosmeticsOn()) return off();
		return CosmeticsFeature.instance().paused()
				? new HubCard.Status(Component.translatable("golemaddons.hub.cosmetics.status.paused"), UiText.YELLOW)
				: new HubCard.Status(Component.translatable("golemaddons.hub.cosmetics.status.on"), UiText.GREEN);
	}

	private static boolean animationsOn() {
		GolemConfig.Animations a = config().animations;
		return a.enabled && !a.current.isVanilla();
	}

	static HubCard.Status animations() {
		return animationsOn() ? new HubCard.Status(Component.translatable("golemaddons.hub.animations.status.on"), UiText.GREEN) : off();
	}

	static HubCard.Status hitboxes() {
		return config().render.dragonHitboxes
				? new HubCard.Status(Component.translatable("golemaddons.hub.hitboxes.status.on"), UiText.GREEN) : off();
	}

	static HubCard.Status visuals() {
		int on = (cosmeticsOn() ? 1 : 0) + (animationsOn() ? 1 : 0) + (config().render.dragonHitboxes ? 1 : 0);
		return on > 0 ? countOn(on, 3) : off();
	}

	static HubCard.Status partyFinder() {
		PartyFinderFeature f = PartyFinderFeature.instance();
		if (f == null || !f.enabled()) return off();
		var own = f.leading();
		if (own != null) {
			return new HubCard.Status(Component.translatable("golemaddons.hub.partyfinder.leading", own.size(), own.max()), UiText.GREEN);
		}
		if (f.memberOf() != null) return new HubCard.Status(Component.translatable("golemaddons.hub.partyfinder.member"), UiText.GREEN);
		if (f.outgoing() != null) return new HubCard.Status(Component.translatable("golemaddons.hub.partyfinder.requested"), UiText.YELLOW);
		return new HubCard.Status(Component.translatable("golemaddons.hub.partyfinder.ready"), UiText.ACCENT_2);
	}

	private static HubCard.Status countOn(int on, int total) {
		return new HubCard.Status(Component.translatable("golemaddons.hub.category.status", on, total), UiText.GREEN);
	}
}
