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
	public Afk afk = new Afk();
	public Riftborn riftborn = new Riftborn();
	public BoneSwap boneSwap = new BoneSwap();
	public ChatCleaner chatCleaner = new ChatCleaner();
	public Scoreboard scoreboard = new Scoreboard();

	/** Where a movable HUD sits (fractions of the screen, so it survives resizes) and how big it is. */
	public static final class HudPosition {
		public static final float MIN_SCALE = 0.5f;
		public static final float MAX_SCALE = 3f;

		public float x;
		public float y;
		public float scale = 1f;

		public HudPosition() {
		}

		public HudPosition(float x, float y, float scale) {
			this.x = x;
			this.y = y;
			this.scale = scale;
		}

		public HudPosition copy() {
			return new HudPosition(x, y, scale);
		}

		void sanitize() {
			x = Float.isFinite(x) ? Math.clamp(x, 0f, 1f) : 0f;
			y = Float.isFinite(y) ? Math.clamp(y, 0f, 1f) : 0f;
			scale = Float.isFinite(scale) ? Math.clamp(scale, MIN_SCALE, MAX_SCALE) : 1f;
		}
	}

	/** AFK helper: dragon tracker HUD for /afk sessions, and getting back to /afk after a disconnect or warp. */
	public static final class Afk {
		public static final int MAX_DELAY_S = 300;

		public boolean enabled = true;
		/** Reconnect to the server after being disconnected during a session. */
		public boolean autoReconnect = true;
		/** Run /afk again when the AFK menu closes without you closing it (a warp, a lobby switch, a restart). */
		public boolean autoReopen = true;
		/** Seconds before the first reconnect attempt; later attempts wait longer, up to five minutes. */
		public int reconnectDelayS = 10;
		/** Seconds before running /afk again after the menu closed or after joining. */
		public int reopenDelayS = 5;
		/** Run before /afk when /afk doesn't open the menu (for when you were sent somewhere it doesn't work). */
		public String hubCommand = "hub";
		public boolean showHud = true;
		/** Show the tracker even with no session running. */
		public boolean hudAlways = false;
		public HudPosition hud = new HudPosition(0.01f, 0.3f, 1f);
		/** /gaafk test ... commands and the test buttons. Turn off once you trust it. */
		public boolean testTools = true;

		public Afk copy() {
			Afk c = new Afk();
			c.enabled = enabled;
			c.autoReconnect = autoReconnect;
			c.autoReopen = autoReopen;
			c.reconnectDelayS = reconnectDelayS;
			c.reopenDelayS = reopenDelayS;
			c.hubCommand = hubCommand;
			c.showHud = showHud;
			c.hudAlways = hudAlways;
			c.hud = hud.copy();
			c.testTools = testTools;
			return c;
		}

		void sanitize() {
			if (hud == null) hud = new HudPosition(0.01f, 0.3f, 1f);
			hud.sanitize();
			reconnectDelayS = Math.clamp(reconnectDelayS, 2, MAX_DELAY_S);
			reopenDelayS = Math.clamp(reopenDelayS, 1, 60);
			hubCommand = com.golemaddons.autosell.QuickSellCommand.normalize(hubCommand == null ? "" : hubCommand);
		}
	}

	/** Riftborn Seraph (scoreboard: Voidgloom Seraph V) helpers. */
	public static final class Riftborn {
		public static final int MAX_RADIUS = 64;

		public boolean anchors = true;
		/** Outline colour (ARGB) and the faint fill inside it. */
		public int anchorColor = 0xFFFF4FD8;
		public int anchorFill = 0x40FF4FD8;
		public float anchorLineWidth = 3f;
		public boolean anchorsThroughWalls = true;
		public int anchorRadius = 32;
		public boolean autoAttunement = false;
		/** Added to the 0.3 s swap cooldown, to allow for ping. */
		public int attunementMarginMs = 60;
		/** Also switch to the dagger the target needs. */
		public boolean switchDagger = true;
		/** When not aiming at a marked half, follow the action bar's "Right-click → FLESH" instruction. */
		public boolean actionBarHint = true;

		public Riftborn copy() {
			Riftborn c = new Riftborn();
			c.anchors = anchors;
			c.anchorColor = anchorColor;
			c.anchorFill = anchorFill;
			c.anchorLineWidth = anchorLineWidth;
			c.anchorsThroughWalls = anchorsThroughWalls;
			c.anchorRadius = anchorRadius;
			c.autoAttunement = autoAttunement;
			c.attunementMarginMs = attunementMarginMs;
			c.switchDagger = switchDagger;
			c.actionBarHint = actionBarHint;
			return c;
		}

		void sanitize() {
			anchorRadius = Math.clamp(anchorRadius, 4, MAX_RADIUS);
			anchorLineWidth = Float.isFinite(anchorLineWidth) ? Math.clamp(anchorLineWidth, 1f, 10f) : 3f;
			attunementMarginMs = Math.clamp(attunementMarginMs, 0, 500);
		}
	}

	/** Slayer chat cleaner: hides the boss messages that repeat every fight. Each group can be kept. */
	public static final class ChatCleaner {
		public boolean enabled = true;
		public boolean hitshield = true;
		public boolean mechanics = true;
		public boolean split = true;
		public boolean anchors = true;
		public boolean soulcry = true;
		/** Extra text to hide, "|"-separated: any chat line containing one of them is hidden. */
		public String customFilters = "";

		public boolean hides(com.golemaddons.chat.SlayerChatFilter.Category c) {
			return switch (c) {
				case HITSHIELD -> hitshield;
				case MECHANICS -> mechanics;
				case SPLIT -> split;
				case ANCHORS -> anchors;
				case SOULCRY -> soulcry;
			};
		}

		public ChatCleaner copy() {
			ChatCleaner c = new ChatCleaner();
			c.enabled = enabled;
			c.hitshield = hitshield;
			c.mechanics = mechanics;
			c.split = split;
			c.anchors = anchors;
			c.soulcry = soulcry;
			c.customFilters = customFilters;
			return c;
		}

		void sanitize() {
			if (customFilters == null) customFilters = "";
			if (customFilters.length() > 512) customFilters = customFilters.substring(0, 512);
		}
	}

	/** Custom scoreboard: the server's sidebar redrawn with chosen lines, order, background theme and text. */
	public static final class Scoreboard {
		public static final int MAX_ELEMENTS = 40;
		public static final int MAX_TEXT = 96;

		public boolean enabled = false;
		/** Hide the server's own sidebar while the custom one shows. */
		public boolean hideVanilla = true;
		public List<Element> elements = com.golemaddons.scoreboard.ScoreboardElement.defaults();
		/** "&" colour codes and "&#RRGGBB". Empty: the server's title. */
		public String header = "&d&lDragSim";
		/** Empty: no footer line. */
		public String footer = "&7dragsim.co";
		/** LEFT, CENTER or RIGHT, for the lines (header and footer are centred). */
		public String align = "LEFT";
		public boolean textShadow = true;
		public int padding = 5;
		public int lineSpacing = 1;
		public String theme = "AURORA";
		/** Colours per theme, so switching back and forth keeps each one's edits. */
		public Map<String, ThemeColors> themeColors = new LinkedHashMap<>();
		/** Background opacity, 0-100. */
		public int opacity = 85;
		/** Darkens the background behind the text, 0-80, for legibility over bright themes. */
		public int textBacking = 20;
		public boolean particles = true;
		/** Particle amount, 0-200 (% of the theme's normal). */
		public int particleDensity = 100;
		/** Animation speed, 0-300 (%). 0 freezes it. */
		public int animationSpeed = 100;
		public int cornerRadius = 5;
		public int borderWidth = 1;
		/** NONE, SOLID or THEME (animated along the theme's colours). */
		public String borderStyle = "THEME";
		public int borderColor = 0xFF9D7BFF;
		public HudPosition hud = new HudPosition(1f, 0.42f, 1f);

		/** One line (or group of lines) of the board. */
		public static final class Element {
			public String type;
			public boolean enabled = true;
			/** Text of a CUSTOM line. */
			public String text = "";

			public Element() {
			}

			public Element(String type, boolean enabled, String text) {
				this.type = type;
				this.enabled = enabled;
				this.text = text;
			}

			public Element copy() {
				return new Element(type, enabled, text);
			}
		}

		/** A theme's colours (ARGB): two or three background colours and the particle colour. */
		public static final class ThemeColors {
			public int primary;
			public int secondary;
			public int accent;
			public int particle;

			public ThemeColors() {
			}

			public ThemeColors(int primary, int secondary, int accent, int particle) {
				this.primary = primary;
				this.secondary = secondary;
				this.accent = accent;
				this.particle = particle;
			}

			public ThemeColors copy() {
				return new ThemeColors(primary, secondary, accent, particle);
			}
		}

		/** The colours being used for the current theme (its defaults until edited). */
		public ThemeColors colors() {
			com.golemaddons.scoreboard.ScoreboardTheme t = com.golemaddons.scoreboard.ScoreboardTheme.of(theme);
			return themeColors.computeIfAbsent(t.name(), k -> t.defaults());
		}

		public Scoreboard copy() {
			Scoreboard c = new Scoreboard();
			c.enabled = enabled;
			c.hideVanilla = hideVanilla;
			c.elements = new ArrayList<>();
			for (Element e : elements) c.elements.add(e.copy());
			c.header = header;
			c.footer = footer;
			c.align = align;
			c.textShadow = textShadow;
			c.padding = padding;
			c.lineSpacing = lineSpacing;
			c.theme = theme;
			c.themeColors = new LinkedHashMap<>();
			themeColors.forEach((k, v) -> c.themeColors.put(k, v.copy()));
			c.opacity = opacity;
			c.textBacking = textBacking;
			c.particles = particles;
			c.particleDensity = particleDensity;
			c.animationSpeed = animationSpeed;
			c.cornerRadius = cornerRadius;
			c.borderWidth = borderWidth;
			c.borderStyle = borderStyle;
			c.borderColor = borderColor;
			c.hud = hud.copy();
			return c;
		}

		void sanitize() {
			if (elements == null) elements = com.golemaddons.scoreboard.ScoreboardElement.defaults();
			elements.removeIf(e -> e == null || com.golemaddons.scoreboard.ScoreboardElement.of(e.type) == null);
			for (Element e : elements) {
				if (e.text == null) e.text = "";
				if (e.text.length() > MAX_TEXT) e.text = e.text.substring(0, MAX_TEXT);
			}
			while (elements.size() > MAX_ELEMENTS) elements.remove(elements.size() - 1);
			// Every server element is in the list exactly once (custom and blank lines can repeat).
			java.util.Set<String> seen = new java.util.HashSet<>();
			elements.removeIf(e -> !com.golemaddons.scoreboard.ScoreboardElement.of(e.type).repeatable && !seen.add(e.type));
			for (com.golemaddons.scoreboard.ScoreboardElement t : com.golemaddons.scoreboard.ScoreboardElement.values()) {
				if (!t.repeatable && !seen.contains(t.name())) elements.add(new Element(t.name(), false, ""));
			}
			if (header == null) header = "";
			if (footer == null) footer = "";
			if (header.length() > MAX_TEXT) header = header.substring(0, MAX_TEXT);
			if (footer.length() > MAX_TEXT) footer = footer.substring(0, MAX_TEXT);
			if (!java.util.Set.of("LEFT", "CENTER", "RIGHT").contains(align)) align = "LEFT";
			if (!java.util.Set.of("NONE", "SOLID", "THEME").contains(borderStyle)) borderStyle = "THEME";
			theme = com.golemaddons.scoreboard.ScoreboardTheme.of(theme).name();
			if (themeColors == null) themeColors = new LinkedHashMap<>();
			themeColors.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
			padding = Math.clamp(padding, 1, 12);
			lineSpacing = Math.clamp(lineSpacing, 0, 6);
			opacity = Math.clamp(opacity, 0, 100);
			textBacking = Math.clamp(textBacking, 0, 80);
			particleDensity = Math.clamp(particleDensity, 0, 200);
			animationSpeed = Math.clamp(animationSpeed, 0, 300);
			cornerRadius = Math.clamp(cornerRadius, 0, 12);
			borderWidth = Math.clamp(borderWidth, 0, 3);
			if (hud == null) hud = new HudPosition(1f, 0.42f, 1f);
			hud.sanitize();
		}
	}

	/** Bone swap: one key throws every Bonemerang in the hotbar, one after another. */
	public static final class BoneSwap {
		public boolean enabled = true;
		/** Items whose name contains this are thrown. */
		public String itemName = "Bonemerang";
		/** Time between two throws. */
		public int delayMs = 60;
		/** Each delay is changed by up to this much either way, at random. */
		public int variationMs = 20;
		/** 0: back to the slot you were on; 1-9: that hotbar slot. */
		public int endSlot = 0;

		public BoneSwap copy() {
			BoneSwap c = new BoneSwap();
			c.enabled = enabled;
			c.itemName = itemName;
			c.delayMs = delayMs;
			c.variationMs = variationMs;
			c.endSlot = endSlot;
			return c;
		}

		void sanitize() {
			if (itemName == null || itemName.isBlank()) itemName = "Bonemerang";
			if (itemName.length() > 48) itemName = itemName.substring(0, 48);
			delayMs = Math.clamp(delayMs, 0, 1000);
			variationMs = Math.clamp(variationMs, 0, 500);
			endSlot = Math.clamp(endSlot, 0, 9);
		}
	}

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
		/** Removes the thick purple haze in the Rift (boss-bar world fog and the dimension's fog). */
		public boolean clearRiftHaze = false;
		/** With the haze cleared, also use an overworld-style blue sky and fog colour instead of the End's. */
		public boolean riftNormalSky = true;
		/** Clear the haze everywhere, not only while in the Rift. */
		public boolean clearHazeEverywhere = false;
		/** Hide debuff screen effects (client only; the effects still apply). Each kind can be kept. */
		public boolean disableDebuffs = false;
		public boolean noBlindness = true;
		public boolean noDarkness = true;
		public boolean noNausea = true;
		public boolean noPumpkin = true;
		public boolean noFreezing = true;
		public boolean noFireOverlay = false;

		public Render copy() {
			Render c = new Render();
			c.dragonHitboxes = dragonHitboxes;
			c.clearRiftHaze = clearRiftHaze;
			c.riftNormalSky = riftNormalSky;
			c.clearHazeEverywhere = clearHazeEverywhere;
			c.disableDebuffs = disableDebuffs;
			c.noBlindness = noBlindness;
			c.noDarkness = noDarkness;
			c.noNausea = noNausea;
			c.noPumpkin = noPumpkin;
			c.noFreezing = noFreezing;
			c.noFireOverlay = noFireOverlay;
			return c;
		}
	}

	/** Settings shared by every sell feature: which screen is the sell GUI, click pacing, slot protection. */
	public static final class Selling {
		public static final int MIN_DELAY_TICKS = 1;
		public static final int MAX_DELAY_TICKS = 20;

		/** Regex fully matched against the NPC sell GUI's title. Blank disables all selling. */
		public String sellScreenTitlePattern = "Upgrades Shop";
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
		c.afk = afk.copy();
		c.riftborn = riftborn.copy();
		c.boneSwap = boneSwap.copy();
		c.chatCleaner = chatCleaner.copy();
		c.scoreboard = scoreboard.copy();
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
		if (afk == null) afk = new Afk();
		if (riftborn == null) riftborn = new Riftborn();
		if (boneSwap == null) boneSwap = new BoneSwap();
		if (chatCleaner == null) chatCleaner = new ChatCleaner();
		if (scoreboard == null) scoreboard = new Scoreboard();
		chatCleaner.sanitize();
		scoreboard.sanitize();
		afk.sanitize();
		riftborn.sanitize();
		boneSwap.sanitize();
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
