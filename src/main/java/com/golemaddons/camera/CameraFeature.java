package com.golemaddons.camera;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;

/** Camera customizer: no front view, custom distance, clip through blocks. Render-only. */
public final class CameraFeature {
	/** Null when the feature is off, so the mixins pass through to vanilla. */
	private static volatile GolemConfig.Camera settings;

	private CameraFeature() {
	}

	public static void register(ConfigManager config) {
		config.addListener(c -> settings = c.camera.enabled ? c.camera.copyForRender() : null);
		// The front view can also come from options.txt or another mod: step past it to the back view.
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			GolemConfig.Camera s = settings;
			if (s != null && s.disableFrontView && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		});
	}

	public static GolemConfig.Camera settings() {
		return settings;
	}

	/** The view F5 switches to: vanilla's choice, or the one after it when that is the disabled front view. */
	public static CameraType nextPerspective(CameraType vanillaNext) {
		GolemConfig.Camera s = settings;
		return s != null && s.disableFrontView && vanillaNext == CameraType.THIRD_PERSON_FRONT ? vanillaNext.cycle() : vanillaNext;
	}
}
