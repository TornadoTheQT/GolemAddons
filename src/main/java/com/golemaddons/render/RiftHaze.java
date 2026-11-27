package com.golemaddons.render;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Clears the Rift's purple haze. The haze is the boss bar's "world fog" (thick fog from 10 to 96 blocks) over the
 * End's purple fog; with this on, fog goes back to render-distance fog and, optionally, the sky and fog take
 * overworld colours. Render-only: nothing the server sees changes.
 */
public final class RiftHaze {
	/** Plains-like daytime sky and fog. */
	public static final int SKY = 0xFF78A7FF;
	public static final int FOG = 0xFFC0D8FF;

	private static volatile GolemConfig.Render settings = new GolemConfig.Render();
	private static long checkedAt;
	private static boolean inRift;

	private RiftHaze() {
	}

	public static void register(ConfigManager manager) {
		manager.addListener(c -> settings = c.render.copy());
	}

	/** Whether to remove the haze right now. */
	public static boolean active() {
		GolemConfig.Render s = settings;
		if (!s.clearRiftHaze) return false;
		return s.clearHazeEverywhere || inRift();
	}

	/** Also swap the End sky and purple fog for overworld colours. */
	public static boolean normalSky() {
		return active() && settings.riftNormalSky;
	}

	/** The End sky box should be drawn as an overworld sky. */
	public static boolean overworldSkybox(DimensionType.Skybox skybox) {
		return skybox == DimensionType.Skybox.END && normalSky();
	}

	/** In the Rift: a boss bar or the sidebar mentions it, or the dimension is called "rift" (checked twice a second). */
	private static boolean inRift() {
		long now = System.currentTimeMillis();
		if (now - checkedAt < 500) return inRift;
		checkedAt = now;
		inRift = detect(Minecraft.getInstance());
		return inRift;
	}

	private static boolean detect(Minecraft mc) {
		if (mc.level == null) return false;
		try {
			if (mc.level.dimension().identifier().getPath().toLowerCase(Locale.ROOT).contains("rift")) return true;
			for (LerpingBossEvent e : ((com.golemaddons.mixin.BossHealthOverlayAccessor) mc.gui.getBossOverlay()).golemaddons$events().values()) {
				if (e.getName().getString().toLowerCase(Locale.ROOT).contains("rift")) return true;
			}
			for (String line : com.golemaddons.boss.Sidebar.lines(mc)) {
				if (line.toLowerCase(Locale.ROOT).contains("the rift")) return true;
			}
		} catch (RuntimeException e) {
			return false;
		}
		return false;
	}
}
