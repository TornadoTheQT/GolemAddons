package com.golemaddons.boss;

import com.golemaddons.config.GolemConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;

/**
 * Riftborn Seraph auto attunement. While you aim at something with a mark (VOID, FLESH, ICHOR, AETHER) on its name
 * tag, the matching dagger is put in your hand and, if it's in the wrong attunement, right-clicked once to switch it,
 * never faster than the 0.3 s cooldown plus a margin for ping. After a switch it waits for the server to update the
 * dagger before deciding again, so it never double-switches.
 */
final class AutoAttunement implements BossFeature {
	static final long SWAP_COOLDOWN_MS = 300;
	private static final long UPDATE_WAIT_MS = 1000;

	private long lastSwapAt = Long.MIN_VALUE / 2;
	private String swappedFrom;
	private boolean warnedUnreadable;

	@Override
	public BossType boss() {
		return BossType.RIFTBORN_SERAPH;
	}

	@Override
	public void onTick(Minecraft mc, GolemConfig config) {
		GolemConfig.Riftborn r = config.riftborn;
		if (!r.autoAttunement || mc.player == null || mc.gameMode == null || mc.screen != null) return;
		Attunement need = targetMark(mc);
		if (need == null) return;
		var inventory = mc.player.getInventory();
		int slot = daggerSlot(mc, need.dagger());
		if (slot < 0) return;
		if (inventory.getSelectedSlot() != slot) {
			if (!r.switchDagger) return;
			inventory.setSelectedSlot(slot);
			return; // let the slot change reach the server first
		}
		ItemStack dagger = inventory.getItem(slot);
		String signature = signature(dagger);
		long now = Util.getMillis();
		// Just switched: wait for the server's updated dagger (or give up waiting after a second).
		if (signature.equals(swappedFrom) && now - lastSwapAt < UPDATE_WAIT_MS) return;
		Attunement current = Attunement.current(need.dagger(), dagger.getHoverName().getString(), lore(dagger));
		if (current == null) {
			if (!warnedUnreadable) {
				warnedUnreadable = true;
				mc.gui.getChat().addClientSystemMessage(Component.literal("[GolemAddons] ").withStyle(ChatFormatting.GRAY)
						.append(Component.translatable("golemaddons.riftborn.attunement.unreadable").withStyle(ChatFormatting.RED)));
			}
			return;
		}
		if (current == need || now - lastSwapAt < SWAP_COOLDOWN_MS + r.attunementMarginMs) return;
		mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
		lastSwapAt = now;
		swappedFrom = signature;
	}

	/** The mark of whatever is under the crosshair: its own name, or a name tag floating just above it. */
	static Attunement targetMark(Minecraft mc) {
		Entity target = mc.crosshairPickEntity;
		if (target == null || mc.level == null) return null;
		for (String text : namesAround(mc, target)) {
			Attunement a = Attunement.markIn(text);
			if (a != null) return a;
		}
		return null;
	}

	/**
	 * Name tags belonging to the target, nearest first. The two halves stand close together, so the search area
	 * catches both halves' tags; the one nearest the top of the aimed half is its own.
	 */
	static List<String> namesAround(Minecraft mc, Entity target) {
		record Tag(String text, double distance) {
		}
		List<Tag> tags = new ArrayList<>();
		if (target.hasCustomName()) tags.add(new Tag(target.getCustomName().getString(), -1));
		double tx = target.getX();
		double ty = target.getY() + target.getBbHeight();
		double tz = target.getZ();
		AABB tagArea = target.getBoundingBox().inflate(1.0, 0, 1.0).expandTowards(0, 3.0, 0);
		for (Entity e : mc.level.getEntities(target, tagArea)) {
			String text = e instanceof Display.TextDisplay display ? display.getText().getString()
					: e.hasCustomName() ? e.getCustomName().getString() : null;
			if (text == null || text.isBlank()) continue;
			double dx = e.getX() - tx;
			double dy = e.getY() - ty;
			double dz = e.getZ() - tz;
			tags.add(new Tag(text, dx * dx + dz * dz + dy * dy * 0.25));
		}
		tags.sort(java.util.Comparator.comparingDouble(Tag::distance));
		List<String> out = new ArrayList<>();
		for (Tag t : tags) out.add(t.text());
		return out;
	}

	static int daggerSlot(Minecraft mc, Attunement.Dagger dagger) {
		var inventory = mc.player.getInventory();
		if (dagger.isIn(inventory.getItem(inventory.getSelectedSlot()).getHoverName().getString())) return inventory.getSelectedSlot();
		for (int i = 0; i < 9; i++) {
			if (dagger.isIn(inventory.getItem(i).getHoverName().getString())) return i;
		}
		return -1;
	}

	static List<String> lore(ItemStack stack) {
		List<String> out = new ArrayList<>();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) for (Component line : lore.lines()) out.add(line.getString());
		return out;
	}

	private static String signature(ItemStack stack) {
		return stack.getHoverName().getString() + "|" + String.join("|", lore(stack));
	}

	@Override
	public void fightEnded() {
		swappedFrom = null;
	}
}
