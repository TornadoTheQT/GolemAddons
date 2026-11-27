package com.golemaddons.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.golemaddons.chat.SlayerChatFilter.Category;
import com.golemaddons.config.GolemConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlayerChatFilterTest {
	@Test
	void recognisesEveryRepeatedMessage() {
		assertEquals(Category.HITSHIELD, SlayerChatFilter.match(" Malevolent Hitshield! Land 100 hits to break it."));
		assertEquals(Category.MECHANICS, SlayerChatFilter.match(" BROKEN HEART RADIATION! Stay within 15 blocks and dodge the beams!"));
		assertEquals(Category.MECHANICS, SlayerChatFilter.match("It pulls itself back together. Hit it while it is still loose."));
		assertEquals(Category.SPLIT, SlayerChatFilter.match(
				"The seraph splits! You have 3 seconds to prepare. Aim at a half and right-click its dagger to match the mark:"));
		assertEquals(Category.SPLIT, SlayerChatFilter.match("- ✦ AETHER • Voidfang Dagger"));
		assertEquals(Category.SPLIT, SlayerChatFilter.match("-  ICHOR • Fleshrend Dagger"));
		assertEquals(Category.SOULCRY, SlayerChatFilter.match(" Soulcry! +400 ⫽ Ferocity against Endermen for 4s."));
		assertEquals(Category.SOULCRY, SlayerChatFilter.match(" Soulcry fades as the katana leaves your hand."));
		assertEquals(Category.HITSHIELD, SlayerChatFilter.match(" The Riftborn Seraph has spawned! Land 100 hits to break its hitshield."));
		assertEquals(Category.HITSHIELD, SlayerChatFilter.match(" The hitshield shatters! The Seraph can be damaged now."));
		assertEquals(Category.ANCHORS, SlayerChatFilter.match("Every anchor is down. It drops out of the air."));
		assertEquals(Category.ANCHORS, SlayerChatFilter.match("Anchor down. 2 left, -960M from it."));
		assertEquals(Category.ANCHORS, SlayerChatFilter.match("It hangs itself on 4 anchors. Shoot every one with a bow."));
		assertEquals(Category.MECHANICS, SlayerChatFilter.match(" NUKEKUBI FIXATION! Stare at the heads to destroy them."));
	}

	@Test
	void leavesOtherChatAlone() {
		assertNull(SlayerChatFilter.match("[VIP+] FemboyCatEars: jesus christ"));
		assertNull(SlayerChatFilter.match("Anchor down? who said that"));
		assertNull(SlayerChatFilter.match("- buying ICHOR dagger cheap"));
		assertNull(SlayerChatFilter.match(""));
		assertNull(SlayerChatFilter.match(null));
	}

	@Test
	void groupsCanBeKeptAndCustomFiltersWork() {
		GolemConfig.ChatCleaner s = new GolemConfig.ChatCleaner();
		List<String> none = List.of();
		assertTrue(ChatCleanerFeature.shouldHide("§cAnchor down. 1 left, -960M from it.", s, none));
		s.anchors = false;
		assertFalse(ChatCleanerFeature.shouldHide("Anchor down. 1 left, -960M from it.", s, none));
		List<String> custom = SlayerChatFilter.customFilters(" you found | x |RARE DROP");
		assertEquals(List.of("you found", "rare drop"), custom);
		assertTrue(ChatCleanerFeature.shouldHide("RARE DROP! Null Ovoid", s, custom));
		s.enabled = false;
		assertFalse(ChatCleanerFeature.shouldHide("RARE DROP! Null Ovoid", s, custom));
	}
}
