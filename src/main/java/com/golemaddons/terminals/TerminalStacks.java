package com.golemaddons.terminals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Converts between real item stacks and the plain {@link TerminalBoard} snapshot. */
public final class TerminalStacks {
	private static final Map<String, Integer> DYE_RGB = Map.ofEntries(
			Map.entry("white", 0xF0F0F0), Map.entry("orange", 0xF9801D), Map.entry("magenta", 0xC74EBD),
			Map.entry("light_blue", 0x3AB3DA), Map.entry("yellow", 0xFED83D), Map.entry("lime", 0x80C71F),
			Map.entry("pink", 0xF38BAA), Map.entry("gray", 0x474F52), Map.entry("light_gray", 0x9D9D97),
			Map.entry("cyan", 0x169C9C), Map.entry("purple", 0x8932B8), Map.entry("blue", 0x3C44AA),
			Map.entry("brown", 0x835432), Map.entry("green", 0x5E7C16), Map.entry("red", 0xB02E26),
			Map.entry("black", 0x1D1D21));

	private static List<TerminalPuzzle.PoolItem> pool;

	private TerminalStacks() {
	}

	/** The chest part of a menu (not the player's inventory) as a board. */
	public static TerminalBoard board(AbstractContainerMenu menu, int size) {
		TerminalBoard.Item[] slots = new TerminalBoard.Item[size];
		for (int i = 0; i < size && i < menu.slots.size(); i++) slots[i] = item(menu.getSlot(i).getItem());
		return new TerminalBoard(slots);
	}

	public static TerminalBoard.Item item(ItemStack stack) {
		if (stack.isEmpty()) return TerminalBoard.Item.EMPTY;
		return new TerminalBoard.Item(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getHoverName().getString(),
				stack.getCount(), stack.hasFoil());
	}

	/** A stack that reads back as the same board item (used by the practice terminals and the settings preview). */
	public static ItemStack stack(TerminalBoard.Item item) {
		if (item.isEmpty()) return ItemStack.EMPTY;
		Item type = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(item.id()));
		if (type == Items.AIR) return ItemStack.EMPTY;
		ItemStack stack = new ItemStack(type, Math.max(1, Math.min(item.count(), 99)));
		if (item.name().isBlank()) {
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		} else if (!stack.getHoverName().getString().equals(item.name())) {
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(item.name()));
		}
		if (item.glint()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** Every item with a name that starts with a letter, for the practice Starts With and Select All boards. */
	public static synchronized List<TerminalPuzzle.PoolItem> pool() {
		if (pool == null) {
			List<TerminalPuzzle.PoolItem> items = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) continue;
				String id = BuiltInRegistries.ITEM.getKey(item).getPath();
				String name = new ItemStack(item).getHoverName().getString();
				if (!name.isEmpty() && Character.isLetter(name.charAt(0)) && !id.endsWith("_spawn_egg")) {
					items.add(new TerminalPuzzle.PoolItem(id, name));
				}
			}
			pool = List.copyOf(items);
		}
		return pool;
	}

	/** RGB of a dyed glass pane, or -1 for anything else. */
	public static int paneRgb(TerminalBoard.Item item) {
		if (!item.id().endsWith("_stained_glass_pane")) return -1;
		Integer rgb = DYE_RGB.get(item.id().substring(0, item.id().length() - "_stained_glass_pane".length()));
		return rgb == null ? -1 : rgb;
	}
}
