package com.golemaddons.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BoneSwapTest {
	@Test
	void findsBonesLeftToRight() {
		List<String> hotbar = Arrays.asList("Aspect of the End", null, "Bonemerang", "Golden Sword", "Bonemerang",
				"✦ Heroic Bonemerang", null, "Bow", "Star");
		assertEquals(List.of(2, 4, 5), BoneSwap.matchingSlots(hotbar, "bonemerang"));
		assertEquals(List.of(), BoneSwap.matchingSlots(hotbar, "shortbow"));
	}

	@Test
	void delayStaysInsideTheVariation() {
		Random r = new Random(1);
		for (int i = 0; i < 1000; i++) {
			long d = BoneSwap.delay(60, 20, r);
			assertTrue(d >= 40 && d <= 80, Long.toString(d));
			assertTrue(BoneSwap.delay(5, 50, r) >= 0);
		}
		assertEquals(60, BoneSwap.delay(60, 0, r));
	}
}
