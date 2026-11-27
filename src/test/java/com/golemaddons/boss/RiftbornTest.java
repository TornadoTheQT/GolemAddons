package com.golemaddons.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RiftbornTest {
	@Test
	void fightIsRecognisedFromTheSidebar() {
		assertTrue(BossType.RIFTBORN_SERAPH.matches(List.of("DRAGSIM", "Slayer Quest", "Voidgloom Seraph V", "Slay the boss!")));
		assertFalse(BossType.RIFTBORN_SERAPH.matches(List.of("Voidgloom Seraph V", "Combat XP 120/2000")));
		assertEquals("Voidgloom Seraph V", Sidebar.clean("§5Voidgloom §cSeraph V​"));
	}

	@Test
	void anchorMessage() {
		assertEquals(4, RiftbornAnchors.count("It hangs itself on 4 anchors. Shoot every one with a bow."));
		assertEquals(1, RiftbornAnchors.count("It hangs itself on 1 anchor. Shoot every one with a bow."));
		assertEquals(-1, RiftbornAnchors.count("The seraph splits!"));
	}

	@Test
	void marksOnNameTags() {
		assertEquals(Attunement.VOID, Attunement.markIn("VOID ☯ 3"));
		assertEquals(Attunement.FLESH, Attunement.markIn("❤ FLESH"));
		assertNull(Attunement.markIn("Voidgloom Seraph 2.4M❤"), "lower-case words in a name aren't marks");
		assertNull(Attunement.markIn("Voidfang Dagger"));
		assertNull(Attunement.markIn("VOID FLESH"), "two marks: can't tell");
		assertEquals(Attunement.Dagger.FLESHREND, Attunement.ICHOR.dagger());
		assertEquals(Attunement.Dagger.VOIDFANG, Attunement.AETHER.dagger());
	}

	/** Exactly what /gaseraph target printed in a real fight. */
	@Test
	void realReversedHalfTags() {
		assertEquals(Attunement.VOID, Attunement.markIn("[Lv8000] reggaD gnafdioV \u2022 DIOV \u262f 960M/960M"));
		assertEquals(Attunement.FLESH, Attunement.markIn("[Lv8000] reggaD dnerhselF \u2022 HSELF \u2764 960M/960M"));
		assertEquals(Attunement.Dagger.VOIDFANG, Attunement.markIn("[Lv8000] reggaD gnafdioV \u2022 DIOV \u262f 960M/960M").dagger());
	}

	/** The real Suspicious Fleshrend Dagger lore, as /gaseraph item printed it. */
	@Test
	void realDaggerLore() {
		List<String> lore = List.of("\ud83d\udde1 Damage: +360.4 (+30.4)", "Strength: +150", "Attuned: \u2620 ICHOR (right-click to swap)", "",
				"Attune                    RIGHT CLICK", "Swap between \u2764 FLESH and \u2620 ICHOR.", "Aim at a half and right-click to match it.",
				"A Riftborn half that shares your attunement takes 3x damage from it.", "Cooldown: 0.3s");
		var d = Attunement.Dagger.FLESHREND;
		assertTrue(d.isIn("Suspicious Fleshrend Dagger"));
		assertEquals(Attunement.ICHOR, Attunement.current(d, "Suspicious Fleshrend Dagger", lore));
		List<String> flesh = new java.util.ArrayList<>(lore);
		flesh.set(2, "Attuned: \u2764 FLESH (right-click to swap)");
		assertEquals(Attunement.FLESH, Attunement.current(d, "Suspicious Fleshrend Dagger", flesh));
	}

	@Test
	void daggerAttunementFromLore() {
		var d = Attunement.Dagger.FLESHREND;
		List<String> lore = List.of("Damage: +120", "Attuned: Ichor", "", "Ability: Switch  RIGHT CLICK",
				"Switch between FLESH and ICHOR attunement.");
		assertEquals(Attunement.ICHOR, Attunement.current(d, "Fleshrend Dagger", lore));
		assertEquals(Attunement.FLESH, Attunement.current(d, "Fleshrend Dagger [FLESH]", List.of("Switch between FLESH and ICHOR.")));
		assertNull(Attunement.current(d, "Fleshrend Dagger", List.of("Switch between FLESH and ICHOR.")), "can't tell: does nothing");
		assertTrue(d.isIn("✦ Fleshrend Dagger"));
		assertEquals(Attunement.ICHOR, d.other(Attunement.FLESH));
	}
}
