package com.golemaddons.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.golemaddons.autosell.ItemMatcher;
import com.golemaddons.autosell.SellRule;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.config.GolemConfigAccess;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DragonCatalogTest {
	private static final Set<String> KEPT_BY_DEFAULT = Set.of("Chaotic", "Berserk", "Arcane", "Superior", "Wise", "Golden");
	private static final Set<String> SOLD_BY_DEFAULT = Set.of("Young", "Strong", "Unstable", "Old", "Protector");

	@Test
	void defaultsSellEverythingExceptTheSixKeptDragons() {
		List<String> names = DragonCatalog.itemNames(new GolemConfig.DragonSeller());
		// 5 dragons x 5 pieces + 6 extras
		assertEquals(31, names.size());
		for (String dragon : SOLD_BY_DEFAULT) {
			for (String piece : List.of("Helmet", "Chestplate", "Leggings", "Boots", "Fragment")) {
				assertTrue(names.contains(dragon + " Dragon " + piece), dragon + " " + piece);
			}
		}
		for (String dragon : KEPT_BY_DEFAULT) {
			assertTrue(names.stream().noneMatch(n -> n.startsWith(dragon + " Dragon")), dragon);
		}
		assertTrue(names.containsAll(List.of("Summoning Eye", "Crystal Fragment", "Dragon Scale", "Dragon Claw", "Aspect of the Dragons",
				"Travel Scroll to Dragon Nest")));
	}

	@Test
	void everyGeneratedRulePassesTheValidator() {
		GolemConfig.DragonSeller all = new GolemConfig.DragonSeller();
		for (DragonCatalog.DragonType d : DragonCatalog.DragonType.values()) all.setDragon(d, true);
		List<SellRule> rules = DragonCatalog.rulesFor(all);
		assertEquals(11 * 5 + 6, rules.size());
		assertEquals(rules.size(), ItemMatcher.compileEnabled(rules).size(), "a dragon rule was rejected by the validator");
	}

	@Test
	void matchingIsExactSoVariantsAreNeverSold() {
		ItemMatcher m = ItemMatcher.compileEnabled(DragonCatalog.rulesFor(new GolemConfig.DragonSeller()));
		assertTrue(m.matches("Young Dragon Helmet", "minecraft:player_head"));
		assertTrue(m.matches("§6Old Dragon Fragment", "minecraft:player_head"));
		assertTrue(m.matches("Aspect of the Dragons", "minecraft:diamond_sword"));
		// Kept dragons, reforged or starred items, lowercase, and partial names are left alone.
		assertFalse(m.matches("Superior Dragon Chestplate", "minecraft:leather_chestplate"));
		assertFalse(m.matches("Golden Dragon Fragment", "minecraft:player_head"));
		assertFalse(m.matches("Strong Wise Dragon Helmet", "minecraft:player_head"));
		assertFalse(m.matches("Heroic Aspect of the Dragons", "minecraft:diamond_sword"));
		assertFalse(m.matches("Young Dragon Boots ✪✪", "minecraft:leather_boots"));
		assertFalse(m.matches("young dragon helmet", "minecraft:player_head"));
		assertFalse(m.matches("Dragon Helmet", "minecraft:player_head"));
	}

	@Test
	void pieceAndExtraToggles() {
		GolemConfig.DragonSeller s = new GolemConfig.DragonSeller();
		s.setPiece(DragonCatalog.Piece.FRAGMENT, false);
		s.setExtra(DragonCatalog.Extra.ASPECT_OF_THE_DRAGONS, false);
		List<String> names = DragonCatalog.itemNames(s);
		assertFalse(names.contains("Young Dragon Fragment"));
		assertTrue(names.contains("Young Dragon Helmet"));
		assertFalse(names.contains("Aspect of the Dragons"));
		assertTrue(names.contains("Summoning Eye"));
	}

	@Test
	void disabledSellerSellsNothing() {
		GolemConfig.DragonSeller s = new GolemConfig.DragonSeller();
		s.enabled = false;
		assertTrue(DragonCatalog.rulesFor(s).isEmpty());
	}

	@Test
	void itemsAddedInAnUpdateStayKeptForExistingConfigs() {
		// A config saved before the travel scroll existed: its extras map lacks the new key.
		GolemConfig config = new com.google.gson.Gson().fromJson("""
				{"configVersion":2,"dragonSeller":{"enabled":true,
				 "extras":{"SUMMONING_EYE":true,"CRYSTAL_FRAGMENT":true,"DRAGON_SCALE":true,"DRAGON_CLAW":true,"ASPECT_OF_THE_DRAGONS":true}}}
				""", GolemConfig.class);
		GolemConfigAccess.sanitize(config);
		assertFalse(config.dragonSeller.sellsExtra(DragonCatalog.Extra.TRAVEL_SCROLL_DRAGONS_NEST), "upgrade must not start selling it");
		assertTrue(config.dragonSeller.sellsExtra(DragonCatalog.Extra.SUMMONING_EYE));
		// Sections that are missing entirely get the defaults.
		assertTrue(config.dragonSeller.sellsDragon(DragonCatalog.DragonType.YOUNG));
		assertTrue(config.dragonSeller.sellsPiece(DragonCatalog.Piece.HELMET));
	}

	@Test
	void freshInstallSellsTheTravelScroll() {
		assertTrue(new GolemConfig.DragonSeller().sellsExtra(DragonCatalog.Extra.TRAVEL_SCROLL_DRAGONS_NEST));
		assertTrue(DragonCatalog.itemNames(new GolemConfig.DragonSeller()).contains("Travel Scroll to Dragon Nest"));
	}
}
