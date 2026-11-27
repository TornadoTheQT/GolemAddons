package com.golemaddons.boss;

import com.golemaddons.config.GolemConfig;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Riftborn Seraph anchors. "It hangs itself on N anchors. Shoot every one with a bow." starts a search for sea
 * lanterns around you; each one found gets an outline until it breaks. If fewer than N turn up (the blocks can arrive
 * a moment after the message), the search runs again every second for a few seconds.
 */
final class RiftbornAnchors implements BossFeature {
	static final Pattern MESSAGE = Pattern.compile("It hangs itself on (\\d+) anchors?\\. Shoot every one with a bow\\.", Pattern.CASE_INSENSITIVE);
	private static final long RESCAN_MS = 1000;
	private static final long RESCAN_FOR_MS = 6000;

	private final Set<BlockPos> anchors = new LinkedHashSet<>();
	private int expected;
	private long searchUntil;
	private long nextScan;

	@Override
	public BossType boss() {
		return BossType.RIFTBORN_SERAPH;
	}

	/** The anchor count in the message, or -1. */
	static int count(String line) {
		Matcher m = MESSAGE.matcher(line);
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	@Override
	public void onChat(String line, GolemConfig config) {
		if (!config.riftborn.anchors) return;
		int n = count(line);
		if (n < 0) return;
		anchors.clear();
		expected = n;
		long now = net.minecraft.util.Util.getMillis();
		searchUntil = now + RESCAN_FOR_MS;
		nextScan = now;
	}

	@Override
	public void onTick(Minecraft mc, GolemConfig config) {
		ClientLevel level = mc.level;
		if (level == null || anchors.isEmpty() && searchUntil == 0) return;
		anchors.removeIf(pos -> !level.getBlockState(pos).is(Blocks.SEA_LANTERN));
		long now = net.minecraft.util.Util.getMillis();
		if (searchUntil > 0 && now >= nextScan) {
			anchors.addAll(scan(level, mc.player.blockPosition(), config.riftborn.anchorRadius));
			nextScan = now + RESCAN_MS;
			if (anchors.size() >= expected || now > searchUntil) searchUntil = 0;
		}
	}

	static List<BlockPos> scan(ClientLevel level, BlockPos center, int radius) {
		List<BlockPos> found = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int minY = Math.max(level.getMinY(), center.getY() - radius);
		int maxY = Math.min(level.getMaxY(), center.getY() + radius);
		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				if (!level.hasChunkAt(p.set(x, center.getY(), z))) continue;
				for (int y = minY; y <= maxY; y++) {
					if (level.getBlockState(p.set(x, y, z)).is(Blocks.SEA_LANTERN)) found.add(p.immutable());
				}
			}
		}
		return found;
	}

	@Override
	public void emitGizmos(Frustum frustum, float partialTick, GolemConfig config) {
		if (anchors.isEmpty() || !config.riftborn.anchors) return;
		GolemConfig.Riftborn r = config.riftborn;
		GizmoStyle style = (r.anchorFill >>> 24) == 0 ? GizmoStyle.stroke(r.anchorColor, r.anchorLineWidth)
				: GizmoStyle.strokeAndFill(r.anchorColor, r.anchorLineWidth, r.anchorFill);
		for (BlockPos pos : anchors) {
			if (!frustum.isVisible(new AABB(pos))) continue;
			GizmoProperties props = Gizmos.cuboid(pos, 0.02f, style);
			if (r.anchorsThroughWalls) props.setAlwaysOnTop();
		}
	}

	int size() {
		return anchors.size();
	}

	@Override
	public void fightEnded() {
		anchors.clear();
		searchUntil = 0;
	}
}
