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

	static HubCard.Status pets() {
		GolemConfig.PetSeller p = config().petSeller;
		int on = 0;
		for (com.golemaddons.autosell.PetRarity r : com.golemaddons.autosell.PetRarity.values()) if (p.sells(r)) on++;
		if (!p.enabled || on == 0) return off();
		return new HubCard.Status(Component.translatable("golemaddons.hub.pets.status", on), UiText.GREEN);
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

	static HubCard.Status afk() {
		GolemConfig.Afk a = config().afk;
		if (!a.enabled) return off();
		com.golemaddons.afk.AfkFeature f = com.golemaddons.afk.AfkFeature.instance();
		if (f.running()) return new HubCard.Status(Component.translatable("golemaddons.hub.afk.status.running", f.tracker().total()), UiText.GREEN);
		return new HubCard.Status(Component.translatable("golemaddons.hub.afk.status.ready"), UiText.GRAY);
	}

	static HubCard.Status riftborn() {
		GolemConfig.Riftborn r = config().riftborn;
		int n = (r.anchors ? 1 : 0) + (r.autoAttunement ? 1 : 0);
		return n == 0 ? off() : new HubCard.Status(Component.translatable("golemaddons.hub.riftborn.status", n), UiText.GREEN);
	}

	static HubCard.Status boneSwap() {
		GolemConfig.BoneSwap b = config().boneSwap;
		if (!b.enabled) return off();
		var key = com.golemaddons.combat.BoneSwap.instance().key();
		return key.isUnbound() ? new HubCard.Status(Component.translatable("golemaddons.hub.boneswap.unbound"), UiText.YELLOW)
				: new HubCard.Status(Component.translatable("golemaddons.hub.boneswap.status", key.getTranslatedKeyMessage()), UiText.GREEN);
	}

	static HubCard.Status combat() {
		GolemConfig c = config();
		int on = (c.riftborn.anchors || c.riftborn.autoAttunement ? 1 : 0)
				+ (c.boneSwap.enabled && !com.golemaddons.combat.BoneSwap.instance().key().isUnbound() ? 1 : 0)
				+ (c.chatCleaner.enabled ? 1 : 0);
		return on > 0 ? countOn(on, 3) : off();
	}

	static HubCard.Status chatCleaner() {
		GolemConfig.ChatCleaner c = config().chatCleaner;
		if (!c.enabled) return off();
		int groups = (c.hitshield ? 1 : 0) + (c.mechanics ? 1 : 0) + (c.split ? 1 : 0) + (c.anchors ? 1 : 0) + (c.soulcry ? 1 : 0);
		return new HubCard.Status(Component.translatable("golemaddons.hub.chatcleaner.status", groups), groups > 0 ? UiText.GREEN : UiText.GRAY);
	}

	static HubCard.Status scoreboard() {
		GolemConfig.Scoreboard s = config().scoreboard;
		if (!s.enabled) return off();
		return new HubCard.Status(Component.translatable("golemaddons.hub.scoreboard.status",
				Component.translatable(com.golemaddons.scoreboard.ScoreboardTheme.of(s.theme).key())), UiText.GREEN);
	}

	static HubCard.Status world() {
		GolemConfig.Render r = config().render;
		int on = (r.dragonHitboxes ? 1 : 0) + (r.clearRiftHaze ? 1 : 0) + (r.disableDebuffs ? 1 : 0);
		return on > 0 ? countOn(on, 3) : off();
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
		int on = (clicker ? 1 : 0) + (cameraOn() ? 1 : 0) + (config().terminals.enabled ? 1 : 0) + (config().afk.enabled ? 1 : 0);
		return on > 0 ? countOn(on, 4) : off();
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

	static HubCard.Status visuals() {
		GolemConfig c = config();
		int on = (cosmeticsOn() ? 1 : 0) + (animationsOn() ? 1 : 0) + (c.scoreboard.enabled ? 1 : 0)
				+ (c.render.dragonHitboxes || c.render.clearRiftHaze || c.render.disableDebuffs ? 1 : 0);
		return on > 0 ? countOn(on, 4) : off();
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
