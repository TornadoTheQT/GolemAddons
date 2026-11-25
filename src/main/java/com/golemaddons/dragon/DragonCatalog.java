package com.golemaddons.dragon;

import com.golemaddons.autosell.SellRule;
import com.golemaddons.config.GolemConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The fixed set of dragon drops the Dragon Seller knows about. Plain Java (no Minecraft classes), so it is unit tested.
 *
 * <p>Every item becomes an exact, quoted full-name rule ({@code \Q...\E}), so reforged or starred variants such as
 * "Strong Wise Dragon Helmet" or "Young Dragon Boots ✪" never match.
 */
public final class DragonCatalog {
	public enum DragonType {
		YOUNG("Young", 0xF0F0F0, true),
		WISE("Wise", 0x55FFFF, false),
		STRONG("Strong", 0xFF5555, true),
		UNSTABLE("Unstable", 0xB04CFF, true),
		OLD("Old", 0xBBBBBB, true),
		PROTECTOR("Protector", 0x8A8A9A, true),
		SUPERIOR("Superior", 0xFFAA00, false),
		ARCANE("Arcane", 0xFF66FF, false),
		CHAOTIC("Chaotic", 0xE03C3C, false),
		BERSERK("Berserk", 0xFF7A1A, false),
		GOLDEN("Golden", 0xFFD84A, false);

		public final String displayName;
		/** RGB, no alpha. */
		public final int color;
		public final boolean sellByDefault;

		DragonType(String displayName, int color, boolean sellByDefault) {
			this.displayName = displayName;
			this.color = color;
			this.sellByDefault = sellByDefault;
		}
	}

	public enum Piece {
		HELMET("Helmet"),
		CHESTPLATE("Chestplate"),
		LEGGINGS("Leggings"),
		BOOTS("Boots"),
		FRAGMENT("Fragment");

		public final String displayName;

		Piece(String displayName) {
			this.displayName = displayName;
		}
	}

	public enum Extra {
		SUMMONING_EYE("Summoning Eye"),
		CRYSTAL_FRAGMENT("Crystal Fragment"),
		DRAGON_SCALE("Dragon Scale"),
		DRAGON_CLAW("Dragon Claw"),
		ASPECT_OF_THE_DRAGONS("Aspect of the Dragons"),
		TRAVEL_SCROLL_DRAGONS_NEST("Travel Scroll to Dragon Nest");

		public final String itemName;

		Extra(String itemName) {
			this.itemName = itemName;
		}
	}

	private DragonCatalog() {
	}

	/** e.g. "Young Dragon Helmet", "Golden Dragon Fragment". */
	public static String itemName(DragonType dragon, Piece piece) {
		return dragon.displayName + " Dragon " + piece.displayName;
	}

	/** Exact item names the given settings would sell, in a stable order. Empty when the seller is off. */
	public static List<String> itemNames(GolemConfig.DragonSeller settings) {
		List<String> names = new ArrayList<>();
		if (!settings.enabled) {
			return names;
		}
		for (DragonType dragon : DragonType.values()) {
			if (!settings.sellsDragon(dragon)) continue;
			for (Piece piece : Piece.values()) {
				if (settings.sellsPiece(piece)) names.add(itemName(dragon, piece));
			}
		}
		for (Extra extra : Extra.values()) {
			if (settings.sellsExtra(extra)) names.add(extra.itemName);
		}
		return names;
	}

	/** One enabled, exact-name rule per item. Still goes through the normal validator when compiled. */
	public static List<SellRule> rulesFor(GolemConfig.DragonSeller settings) {
		List<SellRule> rules = new ArrayList<>();
		for (String name : itemNames(settings)) {
			rules.add(new SellRule(true, name, Pattern.quote(name), "", false));
		}
		return rules;
	}
}
