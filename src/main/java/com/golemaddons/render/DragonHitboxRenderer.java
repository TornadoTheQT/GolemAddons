package com.golemaddons.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.phys.AABB;

/**
 * Outlines the hit parts of ender dragons, and nothing else. Render-only: it reads entities the client already
 * has and draws through vanilla's per-frame gizmo collector (the same path F3+B uses). It sends no packets.
 *
 * <p>Called once per frame from {@code DebugRendererMixin} while the gizmo collector is active. The setting is a
 * cached boolean, so when it is off this costs one field read.
 */
public final class DragonHitboxRenderer {
	/** Parts of a dragon nobody is aiming at. */
	private static final GizmoStyle NORMAL = GizmoStyle.stroke(0xFF5CE1C6, 2f);
	/** The other parts of the dragon under the crosshair. */
	private static final GizmoStyle TARGET_DRAGON = GizmoStyle.stroke(0xFFE0B04A, 2.5f);
	/** The exact part under the crosshair. */
	private static final GizmoStyle TARGET_PART = GizmoStyle.stroke(0xFFFF5555, 4f);

	private static volatile boolean enabled;

	private DragonHitboxRenderer() {
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void emit(Frustum frustum, float partialTick) {
		if (!enabled) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || mc.options.hideGui
				|| mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
			return; // vanilla F3+B already draws dragon parts; don't double up
		}

		Entity aimed = mc.crosshairPickEntity;
		EnderDragonPart aimedPart = aimed instanceof EnderDragonPart part ? part : null;
		EnderDragon aimedDragon = aimedPart != null ? aimedPart.parentMob : aimed instanceof EnderDragon d ? d : null;

		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof EnderDragon dragon) || dragon.isRemoved()) {
				continue;
			}
			boolean targeted = dragon == aimedDragon;
			for (EnderDragonPart part : dragon.getSubEntities()) {
				// Same interpolation as vanilla: draw where the part is this frame, not where it was last tick.
				double dx = part.xo + (part.getX() - part.xo) * partialTick - part.getX();
				double dy = part.yo + (part.getY() - part.yo) * partialTick - part.getY();
				double dz = part.zo + (part.getZ() - part.zo) * partialTick - part.getZ();
				AABB box = part.getBoundingBox().move(dx, dy, dz);
				if (!frustum.isVisible(box)) {
					continue;
				}
				GizmoStyle style = part == aimedPart ? TARGET_PART : targeted ? TARGET_DRAGON : NORMAL;
				Gizmos.cuboid(box, style);
			}
		}
	}
}
