package com.golemaddons.terminals;

import java.util.Arrays;
import java.util.Locale;

/**
 * A plain snapshot of a terminal chest: for each slot the item id (without "minecraft:"), its name, count and
 * whether it glints. Kept free of Minecraft classes so the solvers can be tested on their own.
 */
public final class TerminalBoard {
	/** One slot. An empty slot is {@link #EMPTY}: id "air", count 0. */
	public record Item(String id, String name, int count, boolean glint) {
		public static final Item EMPTY = new Item("air", "", 0, false);

		public Item {
			id = id == null ? "air" : id.toLowerCase(Locale.ROOT);
			if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
			name = TerminalType.plain(name);
		}

		public boolean isEmpty() {
			return count <= 0 || id.equals("air");
		}

		public boolean is(String itemId) {
			return id.equals(itemId);
		}
	}

	private final Item[] slots;

	public TerminalBoard(Item[] slots) {
		this.slots = slots.clone();
		for (int i = 0; i < this.slots.length; i++) {
			if (this.slots[i] == null) this.slots[i] = Item.EMPTY;
		}
	}

	public int size() {
		return slots.length;
	}

	/** The item in a slot; outside the board reads as empty. */
	public Item get(int slot) {
		return slot >= 0 && slot < slots.length ? slots[slot] : Item.EMPTY;
	}

	public Item get(int row, int column) {
		return column < 0 || column > 8 ? Item.EMPTY : get(row * 9 + column);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TerminalBoard b && Arrays.equals(slots, b.slots);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(slots);
	}
}
