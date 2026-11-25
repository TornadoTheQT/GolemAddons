package com.golemaddons.animation;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;

/**
 * Held item animations: keeps an immutable copy of the active settings for the renderer mixin, so the render path
 * never touches the config or allocates.
 */
public final class AnimationFeature {
	/** Null when the feature is off or set to vanilla values: the mixin then does nothing at all. */
	private static volatile HeldItemAnimation active;

	private AnimationFeature() {
	}

	public static void register(ConfigManager config) {
		config.addListener(AnimationFeature::apply);
	}

	private static void apply(GolemConfig config) {
		GolemConfig.Animations a = config.animations;
		HeldItemAnimation next = a.enabled && !a.current.isVanilla() ? a.current.copy() : null;
		active = next;
	}

	/** The settings to draw with, or null for vanilla. Do not modify. */
	public static HeldItemAnimation active() {
		return active;
	}
}
