package com.golemaddons.render;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * Removes the screen effects of debuffs, on this client only. The effects themselves still apply (blindness still
 * stops sprinting, and so on): only what is drawn changes.
 */
public final class Debuffs {
	private static volatile GolemConfig.Render settings = new GolemConfig.Render();

	private Debuffs() {
	}

	public static void register(ConfigManager manager) {
		manager.addListener(c -> settings = c.render.copy());
	}

	/** Whether this effect's visuals (fog, darkening, wobble) are hidden. */
	public static boolean hides(Holder<MobEffect> effect) {
		GolemConfig.Render s = settings;
		if (!s.disableDebuffs || effect == null) return false;
		if (effect.is(MobEffects.BLINDNESS)) return s.noBlindness;
		if (effect.is(MobEffects.DARKNESS)) return s.noDarkness;
		if (effect.is(MobEffects.NAUSEA)) return s.noNausea;
		return false;
	}

	/** Whether a full-screen texture overlay (carved pumpkin, freezing) is hidden. */
	public static boolean hidesOverlay(Identifier texture) {
		GolemConfig.Render s = settings;
		if (!s.disableDebuffs || texture == null) return false;
		String path = texture.getPath();
		if (path.contains("pumpkinblur")) return s.noPumpkin;
		if (path.contains("powder_snow_outline")) return s.noFreezing;
		return false;
	}

	public static boolean hidesFire() {
		GolemConfig.Render s = settings;
		return s.disableDebuffs && s.noFireOverlay;
	}
}
