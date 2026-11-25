package com.golemaddons.config;

import com.golemaddons.autosell.SellRule;
import com.golemaddons.dragon.DragonCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything stored in {@code config/golemaddons.json}. One section per feature, plus shared selling settings. */
public final class GolemConfig {
	/** v1 had a single "autoSell" section; v2 splits it into "selling" + "miningAutoSell" and adds "dragonSeller". */
	public static final int CURRENT_VERSION = 2;

	public int configVersion = CURRENT_VERSION;
	public Selling selling = new Selling();
	public MiningAutoSell miningAutoSell = new MiningAutoSell();
	public DragonSeller dragonSeller = new DragonSeller();
	public Render render = new Render();
	public Cosmetics cosmetics = new Cosmetics();
	public AutoClicker autoClicker = new AutoClicker();
	public PartyFinder partyFinder = new PartyFinder();

	/** Dragon Party Finder: browse preferences, the party you last set up, and notifications. */
	public static final class PartyFinder {
		public boolean enabled = true;
		/** Chat command the leader's game runs to invite someone; {name} is their username. */
		public String inviteCommand = "p {name}";
		public boolean toasts = true;
		public boolean chatMessages = true;
		public boolean sound = true;
		/** "all", "gdrag" or "ndrag". */
		public String typeFilter = "all";
		public String sort = "newest";
		public boolean openOnly = false;
		public List<String> roleFilter = new ArrayList<>();
		/** Role you join as: "" for any, or a role id. */
		public String joinAs = "";
		public Draft draft = new Draft();

		/** The settings of the party you last created or edited, so the next one starts the same. */
		public static final class Draft {
			public String type = "gdrag";
			public String name = "";
			public String desc = "";
			public int max = 5;
			public Map<String, Integer> roles = new LinkedHashMap<>();
			public boolean autoAccept = false;

			Draft copy() {
				Draft d = new Draft();
				d.type = type;
				d.name = name;
				d.desc = desc;
				d.max = max;
				d.roles = new LinkedHashMap<>(roles);
				d.autoAccept = autoAccept;
				return d;
			}
		}

		public PartyFinder copy() {
			PartyFinder c = new PartyFinder();
			c.enabled = enabled;
			c.inviteCommand = inviteCommand;
			c.toasts = toasts;
			c.chatMessages = chatMessages;
			c.sound = sound;
			c.typeFilter = typeFilter;
			c.sort = sort;
			c.openOnly = openOnly;
			c.roleFilter = new ArrayList<>(roleFilter);
			c.joinAs = joinAs;
			c.draft = draft.copy();
			return c;
		}

		void sanitize() {
			if (!com.golemaddons.partyfinder.PartyText.validTemplate(inviteCommand)) inviteCommand = "p {name}";
			if (!List.of("all", "gdrag", "ndrag").contains(typeFilter)) typeFilter = "all";
			sort = com.golemaddons.partyfinder.PartyQuery.Sort.byId(sort).id;
			if (roleFilter == null) roleFilter = new ArrayList<>();
			roleFilter.removeIf(r -> com.golemaddons.partyfinder.PartyModels.Role.byId(r) == null);
			if (joinAs == null || (!joinAs.isEmpty() && com.golemaddons.partyfinder.PartyModels.Role.byId(joinAs) == null)) joinAs = "";
			if (draft == null) draft = new Draft();
			if (com.golemaddons.partyfinder.PartyModels.DragonType.byId(draft.type) == null) draft.type = "gdrag";
			if (draft.name == null) draft.name = "";
			if (draft.desc == null) draft.desc = "";
			if (draft.name.length() > 64) draft.name = draft.name.substring(0, 64);
			if (draft.desc.length() > 240) draft.desc = draft.desc.substring(0, 240);
			draft.max = Math.clamp(draft.max, 1, 6);
			if (draft.roles == null) draft.roles = new LinkedHashMap<>();
			draft.roles.entrySet().removeIf(e -> com.golemaddons.partyfinder.PartyModels.Role.byId(e.getKey()) == null || e.getValue() == null);
			draft.roles.replaceAll((k, v) -> Math.clamp(v, 0, 5));
		}
	}

	/** Left-clicks at a random rate between two values while its key is held. */
	public static final class AutoClicker {
		public boolean enabled = true;
		public int minCps = 8;
		public int maxCps = 12;

		AutoClicker copy() {
			AutoClicker c = new AutoClicker();
			c.enabled = enabled;
			c.minCps = minCps;
			c.maxCps = maxCps;
			return c;
		}

		void sanitize() {
			int lo = Math.clamp(Math.min(minCps, maxCps), com.golemaddons.autoclick.ClickScheduler.MIN_CPS, com.golemaddons.autoclick.ClickScheduler.MAX_CPS);
			int hi = Math.clamp(Math.max(minCps, maxCps), com.golemaddons.autoclick.ClickScheduler.MIN_CPS, com.golemaddons.autoclick.ClickScheduler.MAX_CPS);
			minCps = lo;
			maxCps = hi;
		}
	}

	/** Synced custom names and capes. */
	public static final class Cosmetics {
		public boolean enabled = true;
		/** Override for the cosmetics server. Blank uses the URL built into the mod. */
		public String apiBaseUrl = "";
		public boolean showOthers = true;
		public boolean showOwn = true;

		Cosmetics copy() {
			Cosmetics c = new Cosmetics();
			c.enabled = enabled;
			c.apiBaseUrl = apiBaseUrl;
			c.showOthers = showOthers;
			c.showOwn = showOwn;
			return c;
		}

		void sanitize() {
			if (apiBaseUrl == null) apiBaseUrl = "";
			apiBaseUrl = apiBaseUrl.strip();
			if (!apiBaseUrl.isEmpty() && com.golemaddons.cosmetics.ApiUrl.parse(apiBaseUrl) == null) {
				apiBaseUrl = ""; // invalid override: fall back to the built-in server
			}
		}
	}

	/** Client-side visuals. Render-only: nothing here sends packets or changes the world. */
	public static final class Render {
		/** Outline ender dragons' hit parts. Off by default. */
		public boolean dragonHitboxes = false;

		Render copy() {
			Render c = new Render();
			c.dragonHitboxes = dragonHitboxes;
			return c;
		}
	}

	/** Settings shared by every sell feature: which screen is the sell GUI, click pacing, slot protection. */
	public static final class Selling {
		public static final int MIN_DELAY_TICKS = 1;
		public static final int MAX_DELAY_TICKS = 20;

		/** Regex fully matched against the NPC sell GUI's title. Blank disables all selling. */
		public String sellScreenTitlePattern = "";
		public int clickDelayTicks = 4;
		public boolean protectHotbar = true;
		/** Command the quick-sell key runs before selling, without the slash. Blank turns quick sell off. */
		public String quickSellCommand = "shop";

		Selling copy() {
			Selling c = new Selling();
			c.sellScreenTitlePattern = sellScreenTitlePattern;
			c.clickDelayTicks = clickDelayTicks;
			c.protectHotbar = protectHotbar;
			c.quickSellCommand = quickSellCommand;
			return c;
		}

		void sanitize() {
			if (sellScreenTitlePattern == null) sellScreenTitlePattern = "";
			if (quickSellCommand == null) quickSellCommand = "shop";
			clickDelayTicks = Math.clamp(clickDelayTicks, MIN_DELAY_TICKS, MAX_DELAY_TICKS);
		}
	}

	/** User-written regex rules (the original Auto-Sell). */
	public static final class MiningAutoSell {
		public List<SellRule> rules = new ArrayList<>();

		MiningAutoSell copy() {
			MiningAutoSell c = new MiningAutoSell();
			for (SellRule rule : rules) c.rules.add(rule.copy());
			return c;
		}

		void sanitize() {
			if (rules == null) rules = new ArrayList<>();
			rules.removeIf(r -> r == null);
			rules.forEach(SellRule::sanitize);
		}
	}

	/** Preset dragon drops. Maps are keyed by enum name so the JSON stays readable. */
	public static final class DragonSeller {
		public boolean enabled = true;
		public Map<String, Boolean> dragons = new LinkedHashMap<>();
		public Map<String, Boolean> pieces = new LinkedHashMap<>();
		public Map<String, Boolean> extras = new LinkedHashMap<>();

		public DragonSeller() {
			resetToDefaults();
		}

		/** Sells everything except Chaotic, Berserk, Arcane, Superior, Wise and Golden. */
		public void resetToDefaults() {
			enabled = true;
			dragons = new LinkedHashMap<>();
			pieces = new LinkedHashMap<>();
			extras = new LinkedHashMap<>();
			for (DragonCatalog.DragonType d : DragonCatalog.DragonType.values()) dragons.put(d.name(), d.sellByDefault);
			for (DragonCatalog.Piece p : DragonCatalog.Piece.values()) pieces.put(p.name(), true);
			for (DragonCatalog.Extra e : DragonCatalog.Extra.values()) extras.put(e.name(), true);
		}

		public boolean sellsDragon(DragonCatalog.DragonType d) {
			return Boolean.TRUE.equals(dragons.get(d.name()));
		}

		public boolean sellsPiece(DragonCatalog.Piece p) {
			return Boolean.TRUE.equals(pieces.get(p.name()));
		}

		public boolean sellsExtra(DragonCatalog.Extra e) {
			return Boolean.TRUE.equals(extras.get(e.name()));
		}

		public void setDragon(DragonCatalog.DragonType d, boolean sell) {
			dragons.put(d.name(), sell);
		}

		public void setPiece(DragonCatalog.Piece p, boolean sell) {
			pieces.put(p.name(), sell);
		}

		public void setExtra(DragonCatalog.Extra e, boolean sell) {
			extras.put(e.name(), sell);
		}

		DragonSeller copy() {
			DragonSeller c = new DragonSeller();
			c.enabled = enabled;
			c.dragons = new LinkedHashMap<>(dragons);
			c.pieces = new LinkedHashMap<>(pieces);
			c.extras = new LinkedHashMap<>(extras);
			return c;
		}

		/**
		 * Keeps known keys in catalog order and drops unknown ones. A section that is missing entirely gets the
		 * defaults; a key missing from a stored section (an item added in a later version) is set to keep, so an
		 * update never starts selling something the user didn't choose.
		 */
		void sanitize() {
			DragonSeller defaults = new DragonSeller();
			dragons = merge(dragons, defaults.dragons);
			pieces = merge(pieces, defaults.pieces);
			extras = merge(extras, defaults.extras);
		}

		private static Map<String, Boolean> merge(Map<String, Boolean> stored, Map<String, Boolean> defaults) {
			if (stored == null) {
				return new LinkedHashMap<>(defaults);
			}
			Map<String, Boolean> out = new LinkedHashMap<>();
			for (String key : defaults.keySet()) {
				out.put(key, Boolean.TRUE.equals(stored.get(key)));
			}
			return out;
		}
	}

	/** Defaults for a fresh install: example mining rules are present but disabled. */
	public static GolemConfig defaults() {
		GolemConfig config = new GolemConfig();
		List<SellRule> rules = config.miningAutoSell.rules;
		rules.add(new SellRule(false, "Enchanted Obsidian", "Enchanted Obsidian", "", false));
		rules.add(new SellRule(false, "Enchanted End Stone", "Enchanted End ?[Ss]tone", "", false));
		rules.add(new SellRule(false, "Enchanted Crying Obsidian", "Enchanted Crying Obsidian", "", false));
		return config;
	}

	public GolemConfig copy() {
		GolemConfig c = new GolemConfig();
		c.configVersion = configVersion;
		c.selling = selling.copy();
		c.miningAutoSell = miningAutoSell.copy();
		c.dragonSeller = dragonSeller.copy();
		c.render = render.copy();
		c.cosmetics = cosmetics.copy();
		c.autoClicker = autoClicker.copy();
		c.partyFinder = partyFinder.copy();
		return c;
	}

	void sanitize() {
		if (selling == null) selling = new Selling();
		if (miningAutoSell == null) miningAutoSell = new MiningAutoSell();
		if (dragonSeller == null) dragonSeller = new DragonSeller();
		if (render == null) render = new Render();
		if (cosmetics == null) cosmetics = new Cosmetics();
		if (autoClicker == null) autoClicker = new AutoClicker();
		if (partyFinder == null) partyFinder = new PartyFinder();
		partyFinder.sanitize();
		autoClicker.sanitize();
		cosmetics.sanitize();
		selling.sanitize();
		miningAutoSell.sanitize();
		dragonSeller.sanitize();
	}
}
