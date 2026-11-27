package com.golemaddons.autosell;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

/**
 * Which pets to sell: by rarity (the colour of the pet's name) and up to a level. Immutable; part of a sell run's
 * snapshot. Anything it can't read for sure (no level tag, an unknown colour) is not sold.
 */
public record PetSelling(Set<PetRarity> rarities, int maxLevel) {
	public static final PetSelling NONE = new PetSelling(Set.of(), 0);

	public static PetSelling of(Set<PetRarity> rarities, int maxLevel) {
		return rarities.isEmpty() ? NONE : new PetSelling(Set.copyOf(EnumSet.copyOf(rarities)), maxLevel);
	}

	public boolean isEmpty() {
		return rarities.isEmpty();
	}

	public boolean matches(ItemStack stack) {
		if (isEmpty() || stack.isEmpty()) return false;
		try {
			Component name = stack.getHoverName();
			String plain = com.golemaddons.boss.Sidebar.clean(name.getString());
			int level = PetRarity.level(plain);
			if (level < 0 || level > maxLevel) return false;
			PetRarity rarity = PetRarity.ofColor(nameColor(name));
			return rarity != null && rarities.contains(rarity);
		} catch (RuntimeException e) {
			return false; // fail closed
		}
	}

	/**
	 * Colour (RGB) of the first visible character after the "[Lvl N]" tag, or -1. Follows component styles and
	 * any "§" codes inside the text.
	 */
	static int nameColor(Component name) {
		int[] result = {-1};
		boolean[] afterTag = {false};
		name.visit((style, text) -> {
			int color = colorOf(style);
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				if (c == '§' && i + 1 < text.length()) {
					ChatFormatting f = ChatFormatting.getByCode(text.charAt(++i));
					if (f != null && f.getColor() != null) color = f.getColor();
					else if (f == ChatFormatting.RESET) color = -1;
					continue;
				}
				if (!afterTag[0]) {
					if (c == ']') afterTag[0] = true;
					continue;
				}
				if (!Character.isWhitespace(c)) {
					result[0] = color;
					return java.util.Optional.of(Boolean.TRUE);
				}
			}
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return result[0];
	}

	private static int colorOf(Style style) {
		TextColor c = style.getColor();
		// No colour set: item names show white.
		return c == null ? 0xFFFFFF : c.getValue() & 0xFFFFFF;
	}
}
