package com.golemaddons.scoreboard;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.cosmetics.CosmeticsFeature;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Util;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * The custom scoreboard: reads the server's sidebar each tick, sorts its lines into elements, lays them out in the
 * user's order and draws them in place of the vanilla sidebar. Drawing reuses the model built on the tick, so a
 * frame only paints.
 */
public final class ScoreboardFeature {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("golemaddons");
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);
	private static final Component BLANK = Component.empty();

	private static volatile GolemConfig.Scoreboard settings = new GolemConfig.Scoreboard();
	private static volatile ScoreboardRenderer.Model model;
	private static final long START = Util.getMillis();
	private static final Map<String, Component> CUSTOM_CACHE = new HashMap<>();

	/** >0 while GolemAddons itself reads the sidebar (it must see it even while it is hidden from others). */
	private static int reading;

	private ScoreboardFeature() {
	}

	public static void beginRead() {
		reading++;
	}

	public static void endRead() {
		if (reading > 0) reading--;
	}

	/** Hide the server's sidebar from the vanilla HUD and other mods: ours is on, "Hide server's" too, and it has something to show. */
	public static boolean hidesServerSidebar() {
		GolemConfig.Scoreboard s = settings;
		return reading == 0 && s.enabled && s.hideVanilla && model != null;
	}

	public static void register(ConfigManager manager) {
		manager.addListener(c -> {
			settings = c.scoreboard.copy();
			CUSTOM_CACHE.clear();
		});
		ClientTickEvents.END_CLIENT_TICK.register(ScoreboardFeature::tick);
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> (graphics, delta) -> {
			GolemConfig.Scoreboard s = settings;
			ScoreboardRenderer.Model m = model;
			if (!s.enabled || m == null) {
				vanilla.extractRenderState(graphics, delta);
				return;
			}
			if (!s.hideVanilla) vanilla.extractRenderState(graphics, delta);
			try {
				Minecraft mc = Minecraft.getInstance();
				draw(graphics, mc, m, s, graphics.guiWidth(), graphics.guiHeight());
			} catch (RuntimeException e) {
				// A bad line or setting must never break the HUD: fall back to the server's sidebar.
				model = null;
				LOGGER.warn("Custom scoreboard failed to draw", e);
			}
		});
	}

	/** Draws the board at its saved position on a screen of the given size. */
	public static void draw(net.minecraft.client.gui.GuiGraphicsExtractor g, Minecraft mc, ScoreboardRenderer.Model m, GolemConfig.Scoreboard s,
			int screenW, int screenH) {
		int[] size = ScoreboardRenderer.size(mc.font, m, s);
		float scale = s.hud.scale;
		int w = Math.round(size[0] * scale);
		int h = Math.round(size[1] * scale);
		int x = Math.round(s.hud.x * Math.max(0, screenW - w));
		int y = Math.round(s.hud.y * Math.max(0, screenH - h));
		ScoreboardRenderer.draw(g, mc.font, x, y, scale, m, s, seconds());
	}

	public static float seconds() {
		return (Util.getMillis() - START) / 1000f;
	}

	/** The current board, or null while the server shows no sidebar (or the feature is off). */
	public static ScoreboardRenderer.Model model() {
		return model;
	}

	private static void tick(Minecraft mc) {
		GolemConfig.Scoreboard s = settings;
		if (!s.enabled || mc.level == null || mc.player == null) {
			model = null;
			return;
		}
		try {
			model = build(mc, s);
		} catch (RuntimeException e) {
			model = null;
		}
	}

	/** Builds the board from the live sidebar, or null if there is none. */
	public static ScoreboardRenderer.Model build(Minecraft mc, GolemConfig.Scoreboard s) {
		Objective objective = objective(mc);
		if (objective == null) return null;
		Scoreboard scoreboard = objective.getScoreboard();
		List<String> plain = new ArrayList<>();
		List<Component> lines = new ArrayList<>();
		CosmeticsFeature cosmetics = CosmeticsFeature.instance();
		scoreboard.listPlayerScores(objective).stream().filter(e -> !e.isHidden()).sorted(ORDER).limit(15).forEach(score -> {
			PlayerTeam team = scoreboard.getPlayersTeam(score.owner());
			Component line = PlayerTeam.formatNameForTeam(team, score.ownerName());
			if (cosmetics != null) line = cosmetics.rewriteNames(line);
			lines.add(line);
			plain.add(com.golemaddons.boss.Sidebar.clean(line.getString()));
		});
		return build(mc, s, objective.getDisplayName(), plain, lines);
	}

	/** Builds a board from given lines (the editor's preview uses sample lines when there is no sidebar). */
	public static ScoreboardRenderer.Model build(Minecraft mc, GolemConfig.Scoreboard s, Component title, List<String> plain, List<Component> lines) {
		Map<ScoreboardElement, List<Component>> grouped = SidebarClassifier.classify(plain, lines, BLANK);
		Map<String, String> values = placeholders(mc);
		List<Component> out = ScoreboardLayout.build(s.elements, grouped, BLANK, text -> custom(Placeholders.fill(text, values)));
		Component header = s.header.isBlank() ? title : custom(Placeholders.fill(s.header, values));
		Component footer = s.footer.isBlank() ? null : custom(Placeholders.fill(s.footer, values));
		return ScoreboardRenderer.Model.of(mc.font, header, out, footer);
	}

	private static Objective objective(Minecraft mc) {
		beginRead();
		try {
			return findObjective(mc);
		} finally {
			endRead();
		}
	}

	private static Objective findObjective(Minecraft mc) {
		if (mc.level == null || mc.player == null) return null;
		Scoreboard scoreboard = mc.level.getScoreboard();
		PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
		if (team != null) {
			DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
			if (slot != null) {
				Objective o = scoreboard.getDisplayObjective(slot);
				if (o != null) return o;
			}
		}
		return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	private static Map<String, String> placeholders(Minecraft mc) {
		Map<String, String> v = new HashMap<>();
		LocalDateTime now = LocalDateTime.now();
		v.put("time", now.format(TIME));
		v.put("date", now.format(DATE));
		v.put("fps", Integer.toString(mc.getFps()));
		if (mc.player != null) {
			v.put("x", Integer.toString(mc.player.getBlockX()));
			v.put("y", Integer.toString(mc.player.getBlockY()));
			v.put("z", Integer.toString(mc.player.getBlockZ()));
			if (mc.getConnection() != null) {
				PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
				v.put("ping", info == null ? "?" : Integer.toString(info.getLatency()));
				v.put("players", Integer.toString(mc.getConnection().getOnlinePlayers().size()));
			}
		}
		return v;
	}

	/** "&" codes to a styled component (cached: most lines don't change between ticks). */
	public static Component custom(String text) {
		Component cached = CUSTOM_CACHE.get(text);
		if (cached != null) return cached;
		MutableComponent out = Component.empty();
		for (ColorCodes.Run r : ColorCodes.parse(text)) {
			Style style = Style.EMPTY.withBold(r.bold()).withItalic(r.italic()).withUnderlined(r.underline()).withStrikethrough(r.strike())
					.withObfuscated(r.obfuscated());
			if (r.color() >= 0) style = style.withColor(TextColor.fromRgb(r.color()));
			out.append(Component.literal(r.text()).withStyle(style));
		}
		if (CUSTOM_CACHE.size() > 256) CUSTOM_CACHE.clear();
		CUSTOM_CACHE.put(text, out);
		return out;
	}
}
