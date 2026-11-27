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
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Riftborn Seraph anchors. "It hangs itself on N anchors. Shoot every one with a bow." starts a search for sea
 * lantern blocks around you; each one found gets an outline until it breaks. The search repeats every half second
 * until all N are found (they can appear a moment after the message), and "Every anchor is down." clears everything.
 */
final class RiftbornAnchors implements BossFeature {
	static final Pattern MESSAGE = Pattern.compile("It hangs itself on (\\d+) anchors?\\. Shoot every one with a bow\\.", Pattern.CASE_INSENSITIVE);
	static final Pattern ALL_DOWN = Pattern.compile("Every anchor is down", Pattern.CASE_INSENSITIVE);
	private static final long RESCAN_MS = 500;
	/** How long the search keeps looking after the message (the anchors take a moment to appear). */
	private static final long SEARCH_FOR_MS = 30_000;

	private final Set<BlockPos> blocks = new LinkedHashSet<>();
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
		if (ALL_DOWN.matcher(line).find()) {
			fightEnded();
			return;
		}
		if (!config.riftborn.anchors) return;
		int n = count(line);
		if (n < 0) return;
		blocks.clear();
		expected = n;
		long now = Util.getMillis();
		searchUntil = now + SEARCH_FOR_MS;
		nextScan = now;
	}

	int size() {
		return blocks.size();
	}

	@Override
	public void onTick(Minecraft mc, GolemConfig config) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || size() == 0 && searchUntil == 0) return;
		blocks.removeIf(pos -> !level.getBlockState(pos).is(Blocks.SEA_LANTERN));
		long now = Util.getMillis();
		if (searchUntil > 0 && now >= nextScan) {
			blocks.addAll(scan(level, mc.player.blockPosition(), config.riftborn.anchorRadius));
			nextScan = now + RESCAN_MS;
			if (size() >= expected || now > searchUntil) searchUntil = 0;
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
		if (size() == 0 || !config.riftborn.anchors) return;
		GolemConfig.Riftborn r = config.riftborn;
		GizmoStyle style = (r.anchorFill >>> 24) == 0 ? GizmoStyle.stroke(r.anchorColor, r.anchorLineWidth)
				: GizmoStyle.strokeAndFill(r.anchorColor, r.anchorLineWidth, r.anchorFill);
		List<AABB> boxes = new ArrayList<>();
		for (BlockPos pos : blocks) boxes.add(new AABB(pos).inflate(0.02));
		for (AABB box : boxes) {
			if (!frustum.isVisible(box)) continue;
			GizmoProperties props = Gizmos.cuboid(box, style);
			if (r.anchorsThroughWalls) props.setAlwaysOnTop();
		}
	}

	@Override
	public void fightEnded() {
		blocks.clear();
		searchUntil = 0;
	}
}
