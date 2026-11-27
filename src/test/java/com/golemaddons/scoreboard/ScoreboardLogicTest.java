package com.golemaddons.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.golemaddons.config.GolemConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScoreboardLogicTest {
	private static final List<String> DRAGSIM = List.of("Name: qtTornado", "Purse: 4,249,512,747", "Gems: 675", "Carrot Buff: 1d 3h",
			"Pet: [Lvl 100] Ender Dragon", "Booster: Whale Booster", "", "⏣ Dragon's Den", "", "Riftborn Seraph", "XP: 0/13,750", "", "dragsim.co");

	@Test
	void classifiesTheDragsimSidebar() {
		Map<ScoreboardElement, List<String>> g = SidebarClassifier.classify(DRAGSIM, DRAGSIM, "");
		assertEquals(List.of("Name: qtTornado"), g.get(ScoreboardElement.NAME));
		assertEquals(List.of("⏣ Dragon's Den"), g.get(ScoreboardElement.LOCATION));
		assertEquals(List.of("Riftborn Seraph", "XP: 0/13,750"), g.get(ScoreboardElement.ACTIVITY));
		assertEquals(List.of("dragsim.co"), g.get(ScoreboardElement.WEBSITE));
		assertEquals(ScoreboardElement.LOCATION, SidebarClassifier.typeOf("◎ Hub"));
		assertEquals(ScoreboardElement.ACTIVITY, SidebarClassifier.typeOf("Slay the boss!"));
	}

	@Test
	void activityKeepsGapsBetweenItsGroups() {
		List<String> lines = List.of("Event: Golem", "", "Riftborn Seraph", "Slay the boss!");
		Map<ScoreboardElement, List<String>> g = SidebarClassifier.classify(lines, lines, "<gap>");
		assertEquals(List.of("Event: Golem", "<gap>", "Riftborn Seraph", "Slay the boss!"), g.get(ScoreboardElement.ACTIVITY));
	}

	@Test
	void layoutFollowsUserOrderAndCollapsesBlanks() {
		String blank = "";
		Map<ScoreboardElement, List<String>> g = SidebarClassifier.classify(DRAGSIM, DRAGSIM, blank);
		List<GolemConfig.Scoreboard.Element> els = new ArrayList<>();
		els.add(new GolemConfig.Scoreboard.Element("BLANK", true, ""));
		els.add(new GolemConfig.Scoreboard.Element("PURSE", true, ""));
		els.add(new GolemConfig.Scoreboard.Element("BLANK", true, ""));
		els.add(new GolemConfig.Scoreboard.Element("BLANK", true, ""));
		els.add(new GolemConfig.Scoreboard.Element("GEMS", false, ""));
		els.add(new GolemConfig.Scoreboard.Element("CUSTOM", true, "hi {x}"));
		els.add(new GolemConfig.Scoreboard.Element("BOOSTER", true, ""));
		els.add(new GolemConfig.Scoreboard.Element("BLANK", true, ""));
		els.add(new GolemConfig.Scoreboard.Element("CUSTOM", true, "   "));
		List<String> out = ScoreboardLayout.build(els, g, blank, t -> "[" + t + "]");
		assertEquals(List.of("Purse: 4,249,512,747", "", "[hi {x}]", "Booster: Whale Booster"), out);
	}

	@Test
	void layoutSkipsMissingServerLines() {
		List<GolemConfig.Scoreboard.Element> els = ScoreboardElement.defaults();
		List<String> out = ScoreboardLayout.build(els, Map.of(), "", t -> t);
		assertEquals(List.of("&7{date} &8{time}"), out);
	}

	@Test
	void colourCodes() {
		List<ColorCodes.Run> runs = ColorCodes.parse("&d&lDrag&#55FFFFSim && more&r!");
		assertEquals(3, runs.size());
		assertEquals("Drag", runs.get(0).text());
		assertEquals(0xFF55FF, runs.get(0).color());
		assertTrue(runs.get(0).bold());
		assertEquals("Sim & more", runs.get(1).text());
		assertEquals(0x55FFFF, runs.get(1).color());
		assertFalse(runs.get(1).bold());
		assertEquals("!", runs.get(2).text());
		assertEquals(-1, runs.get(2).color());
		assertEquals("a &z b &#12", ColorCodes.strip("a &z b &#12"));
		assertEquals(List.of(), ColorCodes.parse(null));
	}

	@Test
	void placeholders() {
		assertEquals("12:30 · 60fps {nope} {", Placeholders.fill("{time} · {fps}fps {nope} {", Map.of("time", "12:30", "fps", "60")));
		assertEquals("plain", Placeholders.fill("plain", Map.of()));
	}

	@Test
	void roundedShapeCoversTheBoxWithoutCorners() {
		RoundedShape s = RoundedShape.of(100, 40, 6, 4);
		assertTrue(s.contains(50, 20, 1, 1));
		assertFalse(s.contains(0, 0, 1, 1));
		assertFalse(s.contains(99.5f, 39.5f, 1, 1));
		int covered = 0;
		for (int i = 0; i < s.columns(); i++) covered += s.x1[i] - s.x0[i];
		assertEquals(100, covered);
		assertTrue(s.top[0] > 0 && s.top[s.columns() / 2] == 0);
		RoundedShape tiny = RoundedShape.of(4, 4, 12, 4); // radius larger than the box is clamped
		assertEquals(2, tiny.r);
	}

	@Test
	void sanitizeKeepsEachServerLineOnceAndFixesBadValues() {
		GolemConfig.Scoreboard s = new GolemConfig.Scoreboard();
		s.elements = new ArrayList<>(List.of(new GolemConfig.Scoreboard.Element("PURSE", true, ""),
				new GolemConfig.Scoreboard.Element("PURSE", true, ""), new GolemConfig.Scoreboard.Element("NOPE", true, ""),
				new GolemConfig.Scoreboard.Element("BLANK", true, null)));
		s.theme = "lava lamp";
		s.align = "diagonal";
		s.cornerRadius = 99;
		s.hud = null;
		com.golemaddons.config.GolemConfigAccess.sanitize(s);
		assertEquals(1, s.elements.stream().filter(e -> e.type.equals("PURSE")).count());
		assertTrue(s.elements.stream().anyMatch(e -> e.type.equals("NAME") && !e.enabled));
		assertTrue(s.elements.stream().noneMatch(e -> e.type.equals("NOPE")));
		assertEquals("AURORA", s.theme);
		assertEquals("LEFT", s.align);
		assertEquals(12, s.cornerRadius);
		assertEquals(1f, s.hud.scale);
	}
}
