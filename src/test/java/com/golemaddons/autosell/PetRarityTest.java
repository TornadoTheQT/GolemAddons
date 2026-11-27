package com.golemaddons.autosell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class PetRarityTest {
	@Test
	void readsPetLevels() {
		assertEquals(100, PetRarity.level("[Lvl 100] Ender Dragon"));
		assertEquals(1, PetRarity.level(" [Lvl 1] Wolf "));
		assertEquals(-1, PetRarity.level("Ender Dragon"));
		assertEquals(-1, PetRarity.level("[Lvl] Wolf"));
		assertEquals(-1, PetRarity.level("Pet: [Lvl 100] Ender Dragon"));
		assertEquals(-1, PetRarity.level(null));
	}

	@Test
	void rarityFromNameColour() {
		assertEquals(PetRarity.COMMON, PetRarity.ofColor(0xFFFFFF));
		assertEquals(PetRarity.UNCOMMON, PetRarity.ofColor(0x55FF55));
		assertEquals(PetRarity.RARE, PetRarity.ofColor(0x5555FF));
		assertEquals(PetRarity.EPIC, PetRarity.ofColor(0xAA00AA));
		assertEquals(PetRarity.LEGENDARY, PetRarity.ofColor(0xFFAA00));
		assertEquals(PetRarity.MYTHIC, PetRarity.ofColor(0xFF55FF));
		assertEquals(PetRarity.LEGENDARY, PetRarity.ofColor(0xFFB000)); // a close hex shade
		assertNull(PetRarity.ofColor(0x00AAAA)); // dark aqua isn't a pet rarity
		assertNull(PetRarity.ofColor(-1));
	}

	@Test
	void colourOfTheNameNotTheLevelTag() {
		Component styled = Component.literal("[Lvl 100] ").withColor(0xAAAAAA).append(Component.literal("Ender Dragon").withColor(0xFFAA00));
		assertEquals(0xFFAA00, PetSelling.nameColor(styled));
		assertEquals(0xFF55FF, PetSelling.nameColor(Component.literal("§7[Lvl 5] §dBaby Yeti")));
		assertEquals(0xFFFFFF, PetSelling.nameColor(Component.literal("[Lvl 5] Bee")));
		assertEquals(-1, PetSelling.nameColor(Component.literal("Bee")));
	}
}
