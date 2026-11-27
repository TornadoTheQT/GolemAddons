package com.golemaddons.afk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AfkTrackerTest {
	private static List<AfkTracker.Slot> menu(String latestOn) {
		return List.of(slot("Old Dragon", latestOn), slot("Wise Dragon", latestOn), slot("Superior Dragon", latestOn),
				new AfkTracker.Slot("DRAGON'S REST", List.of("A new reward every 15 minutes.")));
	}

	private static AfkTracker.Slot slot(String name, String latestOn) {
		return new AfkTracker.Slot(name, name.equals(latestOn) ? List.of("Your latest reward: +1 Loot Credit", "Claim with /compensation.")
				: List.of("Dragon Loot Credit", "Claim earned credits with /compensation."));
	}

	private static String title(int seconds) {
		return String.format("AFK Rewards · %d:%02d", seconds / 60, seconds % 60);
	}

	@Test
	void recognisesTheMenuAndTimer() {
		assertTrue(AfkTracker.isAfkMenu("AFK Rewards · 10:31"));
		assertEquals(631, AfkTracker.timer("AFK Rewards · 10:31"));
		assertEquals("Old Dragon", AfkTracker.latest(menu("Old Dragon")));
	}

	@Test
	void countsEachRewardOnceEvenTheSameDragonTwice() {
		for (int direction : new int[] {-1, 1}) {
			AfkTracker t = new AfkTracker();
			long now = 0;
			int timer = direction < 0 ? 900 : 0;
			// What was there before the session is not counted.
			assertNull(t.observe(now, title(timer), menu("Old Dragon")));
			String latest = "Old Dragon";
			String[] rewards = {"Wise Dragon", "Wise Dragon", "Superior Dragon", "Old Dragon"};
			for (String reward : rewards) {
				for (int s = 0; s < 899; s++) {
					now += 1000;
					timer += direction;
					assertNull(t.observe(now, title(timer), menu(latest)), "nothing between rewards");
				}
				now += 1000;
				timer = direction < 0 ? 900 : 0; // the timer starts over with each reward
				latest = reward;
				String got = t.observe(now, title(timer), menu(latest));
				// The mark may arrive a moment after the timer reset.
				if (got == null) got = t.observe(now + 300, title(timer), menu(latest));
				assertEquals(reward, got, "direction " + direction);
			}
			assertEquals(4, t.total());
			assertEquals(2, t.counts().get("Wise Dragon"));
		}
	}

	@Test
	void reopeningTheMenuIsNotAReward() {
		AfkTracker t = new AfkTracker();
		t.observe(0, title(500), menu("Old Dragon"));
		t.observe(1000, title(499), menu("Old Dragon"));
		t.menuOpened();
		// Reopened after a disconnect: the timer started over, no new reward.
		assertNull(t.observe(60_000, title(900), menu("Old Dragon")));
		assertNull(t.observe(61_000, title(899), menu("Old Dragon")));
		assertEquals(0, t.total());
		// But a reward that landed while we weren't looking still counts once the mark moved.
		t.menuOpened();
		assertEquals("Wise Dragon", t.observe(90_000, title(900), menu("Wise Dragon")));
	}
}
