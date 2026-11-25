package com.golemaddons.config;

import com.golemaddons.autosell.SellRule;
import com.golemaddons.dragon.DragonCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything stored in {@code config/golemaddons.json}. One section per feature, plus shared selling settings. */
public final class GolemConfig {
	/**
	 * v1 had a single "autoSell" section; v2 splits it into "selling" + "miningAutoSell" and adds "dragonSeller".
	 * Later sections ("camera", "animations", "updates") are purely additive: a file without them loads with their
	 * defaults, so they need no migration and no version bump. Not bumping also keeps the file readable by older
	 * versions of the mod, which set aside any config whose version is newer than they know.
	 */
	public static final int CURRENT_VERSION = 2;

	public int configVersion = CURRENT_VERSION;
	public Selling selling = new Selling();
	public MiningAutoSell miningAutoSell = new MiningAutoSell();
	public DragonSeller dragonSeller = new DragonSeller();
	public Render render = new Render();
	public Cosmetics cosmetics = new Cosmetics();
	public AutoClicker autoClicker = new AutoClicker();
	public PartyFinder partyFinder = new PartyFinder();
	public Camera camera = new Camera();
	public Animations animations = new Animations();
	public Updates updates = new Updates();
	public Terminals terminals = new Terminals();

	/** Floor 7 terminal solvers: the compact GUI, its look, and hover click. */
	public static final class Terminals {
		public static final float MIN_SCALE = 0.6f;
		public static final float MAX_SCALE = 3f;
		public static final int MAX_GAP = 8;
		public static final int MAX_DELAY_MS = 1000;
		public static final int MAX_PING_MS = 500;

		public boolean enabled = true;
		/** Replace the chest with the compact GUI; off highlights the answer on the normal chest instead. */
		public boolean customGui = true;
		/** Refuse clicks that are not part of the answer while a terminal is open. */
		public boolean blockWrongClicks = true;
		/** Click a correct slot as soon as the cursor rests on it. */
		public boolean hoverClick = false;
		/** Solve terminals without any input, at the hover click's first-click delay and click delay. */
		public boolean autoTerms = false;
		/** Darken the items that are not the answer in Starts With and Select All. */
		public boolean fadeOthers = true;
		/** How dark the faded items get, 0-95 %. */
		public int fadePercent = 70;
		/** Colour left in the faded items, 0 (grey) to 100 % (unchanged). */
		public int othersSaturation = 100;
		/** How long the cursor must rest on a slot before hover click fires. */
		public int hoverDelayMs = 40;
		/** Least time between two clicks made by hover click. */
		public int clickDelayMs = 150;
		/** No hover clicks this soon after a terminal opens. */
		public int firstClickDelayMs = 350;
		public float scale = 1.5f;
		public int gap = 2;
		/** Delay the practice terminals add before answering a click, like a server would. */
		public int practicePingMs = 120;
		public Colors colors = new Colors();

		/** ARGB colours of the compact GUI and the highlights. */
		public static final class Colors {
			public int background = 0xE6100E1A;
			public int tile = 0xFF262236;
			public int solution = 0xFF5CE1C6;
			public int second = 0xFFE0B04A;
			public int third = 0xFF9D7BFF;
			public int blocked = 0xFFFF5555;
			public int text = 0xFFFFFFFF;

			Colors copy() {
				Colors c = new Colors();
				c.background = background;
				c.tile = tile;
				c.solution = solution;
				c.second = second;
				c.third = third;
				c.blocked = blocked;
				c.text = text;
				return c;
			}
		}

		public Terminals copy() {
			Terminals c = new Terminals();
			c.enabled = enabled;
			c.customGui = customGui;
			c.blockWrongClicks = blockWrongClicks;
			c.hoverClick = hoverClick;
			c.autoTerms = autoTerms;
			c.fadeOthers = fadeOthers;
			c.fadePercent = fadePercent;
			c.othersSaturation = othersSaturation;
			c.hoverDelayMs = hoverDelayMs;
			c.clickDelayMs = clickDelayMs;
			c.firstClickDelayMs = firstClickDelayMs;
			c.scale = scale;
			c.gap = gap;
			c.practicePingMs = practicePingMs;
			c.colors = colors.copy();
			return c;
		}

		void sanitize() {
			if (colors == null) colors = new Colors();
			scale = Float.isFinite(scale) ? Math.clamp(scale, MIN_SCALE, MAX_SCALE) : 1.5f;
			gap = Math.clamp(gap, 0, MAX_GAP);
			fadePercent = Math.clamp(fadePercent, 0, 95);
			othersSaturation = Math.clamp(othersSaturation, 0, 100);
			hoverDelayMs = Math.clamp(hoverDelayMs, 0, MAX_DELAY_MS);
			clickDelayMs = Math.clamp(clickDelayMs, 0, MAX_DELAY_MS);
			firstClickDelayMs = Math.clamp(firstClickDelayMs, 0, MAX_DELAY_MS);
			practicePingMs = Math.clamp(practicePingMs, 0, MAX_PING_MS);
		}
	}

	/** Third-person camera tweaks. Render-only: the camera is never sent to the server. */
	public static final class Camera {
		public static final float MIN_DISTANCE = 1f;
		public static final float MAX_DISTANCE = 12f;
		public static final float VANILLA_DISTANCE = 4f;

		public boolean enabled = true;
		/** F5 skips the front-facing view. */
		public boolean disableFrontView = false;
		/** Use {@link #distance} instead of the game's own third-person distance. */
		public boolean customDistance = false;
		public float distance = VANILLA_DISTANCE;
		/** In third person, the camera passes through blocks instead of being pulled in towards the player. */
		public boolean clipThroughBlocks = false;

		/** A detached copy the camera mixins can read from the render thread. */
		public Camera copyForRender() {
			return copy();
		}

		Camera copy() {
			Camera c = new Camera();
			c.enabled = enabled;
			c.disableFrontView = disableFrontView;
			c.customDistance = customDistance;
			c.distance = distance;
			c.clipThroughBlocks = clipThroughBlocks;
			return c;
		}

		void sanitize() {
			distance = Float.isFinite(distance) ? Math.clamp(distance, MIN_DISTANCE, MAX_DISTANCE) : VANILLA_DISTANCE;
		}
	}

	/** First-person held item position, rotation, scale and swing, plus saved presets. */
	public static final class Animations {
		public static final int MAX_PRESETS = 50;

		public boolean enabled = false;
		public com.golemaddons.animation.HeldItemAnimation current = new com.golemaddons.animation.HeldItemAnimation();
		public List<Preset> presets = new ArrayList<>();

		public static final class Preset {
			public String name = "";
			public com.golemaddons.animation.HeldItemAnimation animation = new com.golemaddons.animation.HeldItemAnimation();

			public Preset() {
			}

			public Preset(String name, com.golemaddons.animation.HeldItemAnimation animation) {
				this.name = name;
				this.animation = animation.copy();
			}
		}

		Animations copy() {
			Animations c = new Animations();
			c.enabled = enabled;
			c.current = current.copy();
			for (Preset p : presets) c.presets.add(new Preset(p.name, p.animation));
			return c;
		}

		void sanitize() {
			if (current == null) current = new com.golemaddons.animation.HeldItemAnimation();
			current.sanitize();
			if (presets == null) presets = new ArrayList<>();
			presets.removeIf(p -> p == null || p.animation == null);
			java.util.Set<String> seen = new java.util.HashSet<>();
			List<Preset> clean = new ArrayList<>();
			for (Preset p : presets) {
				String name = com.golemaddons.animation.AnimationCodec.cleanName(p.name);
				if (name.isEmpty() || !seen.add(name.toLowerCase(java.util.Locale.ROOT)) || clean.size() >= MAX_PRESETS) continue;
				p.name = name;
				p.animation.sanitize();
				clean.add(p);
			}
			presets = clean;
		}
	}

	/** Checking GitHub for new releases of the mod. */
	public static final class Updates {
		public boolean checkOnStartup = true;
		/** Download, verify and stage new releases without asking. Off by default. */
		public boolean autoUpdate = false;
		public boolean includePrereleases = false;
		/** A version the player chose to skip: no reminder and no auto-update for it. Blank for none. */
		public String skippedVersion = "";

		Updates copy() {
			Updates c = new Updates();
			c.checkOnStartup = checkOnStartup;
			c.autoUpdate = autoUpdate;
			c.includePrereleases = includePrereleases;
			c.skippedVersion = skippedVersion;
			return c;
		}

		void sanitize() {
			if (skippedVersion == null || skippedVersion.length() > 64) skippedVersion = "";
		}
	}

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
			// Roles were removed from the party finder: drop any saved from older versions so none are ever sent.
			roleFilter.clear();
			joinAs = "";
			draft.roles.clear();
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
			if (sellScreenTitlePattern == null) sellScreenTitlePattern = "Upgrades Shop";
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
		c.camera = camera.copy();
		c.animations = animations.copy();
		c.updates = updates.copy();
		c.terminals = terminals.copy();
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
		if (camera == null) camera = new Camera();
		if (animations == null) animations = new Animations();
		if (updates == null) updates = new Updates();
		if (terminals == null) terminals = new Terminals();
		terminals.sanitize();
		camera.sanitize();
		animations.sanitize();
		updates.sanitize();
		partyFinder.sanitize();
		autoClicker.sanitize();
		cosmetics.sanitize();
		selling.sanitize();
		miningAutoSell.sanitize();
		dragonSeller.sanitize();
	}
}
