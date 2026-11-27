package com.golemaddons.boss;

import com.golemaddons.config.GolemConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;

/**
 * One helper for one boss. {@link Bosses} calls these only while that boss's fight is on (as the sidebar shows it),
 * except {@link #fightEnded}, which is called once when it ends so the helper can clear what it drew.
 */
public interface BossFeature {
	BossType boss();

	/** A chat line during the fight, as plain text (no formatting). */
	default void onChat(String line, GolemConfig config) {
	}

	/** Every client tick during the fight. */
	default void onTick(Minecraft mc, GolemConfig config) {
	}

	/** Every frame during the fight: add outlines and markers through vanilla's gizmo collector. */
	default void emitGizmos(Frustum frustum, float partialTick, GolemConfig config) {
	}

	default void fightEnded() {
	}
}
