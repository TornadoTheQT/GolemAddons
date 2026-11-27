package com.golemaddons.autosell;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The Minecraft-facing half of matching: turns stacks and slots into the plain values {@link ItemMatcher} uses. */
public final class InventoryScan {
	private InventoryScan() {
	}

	public static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static boolean stackMatches(ItemMatcher matcher, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		try {
			return matcher.matches(stack.getHoverName().getString(), itemId(stack));
		} catch (RuntimeException e) {
			return false; // fail closed
		}
	}

	/** A container screen that could be an NPC GUI: not the player's own inventory or the creative menu. */
	public static boolean isCandidateScreen(Screen screen) {
		return screen instanceof AbstractContainerScreen<?>
				&& !(screen instanceof InventoryScreen)
				&& !(screen instanceof CreativeModeInventoryScreen);
	}

	/** Why a player-inventory slot may not be sold, or null if it may. */
	public enum Protection { NOT_PLAYER_INVENTORY, SELECTED_SLOT, HOTBAR }

	public static Protection protection(Slot slot, Player player, boolean protectHotbar) {
		Inventory inventory = player.getInventory();
		if (slot.container != inventory) {
			return Protection.NOT_PLAYER_INVENTORY;
		}
		int index = slot.getContainerSlot();
		// 0-8 hotbar, 9-35 main inventory. Armor, offhand and anything else are never touched.
		if (index < 0 || index >= Inventory.INVENTORY_SIZE) {
			return Protection.NOT_PLAYER_INVENTORY;
		}
		if (index == inventory.getSelectedSlot()) {
			return Protection.SELECTED_SLOT;
		}
		if (protectHotbar && index < Inventory.getSelectionSize()) {
			return Protection.HOTBAR;
		}
		return null;
	}

	public static boolean isSellable(Slot slot, Player player, AutoSellRules rules) {
		if (protection(slot, player, rules.protectHotbar()) != null) return false;
		ItemStack stack = slot.getItem();
		return stackMatches(rules.matcher(), stack) || rules.pets().matches(stack);
	}

	/** Menu slot indexes of every sellable stack, in menu order. */
	public static List<Integer> sellableSlots(AbstractContainerMenu menu, Player player, AutoSellRules rules) {
		List<Integer> out = new ArrayList<>();
		if (rules.sellsNothing()) {
			return out;
		}
		for (int i = 0; i < menu.slots.size(); i++) {
			if (isSellable(menu.slots.get(i), player, rules)) {
				out.add(i);
			}
		}
		return out;
	}
}
