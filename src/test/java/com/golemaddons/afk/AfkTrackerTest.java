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

	private static List<AfkTracker.Slot> menu(String latestOn, int credits) {
		List<AfkTracker.Slot> m = new java.util.ArrayList<>(menu(latestOn));
		m.add(new AfkTracker.Slot("NEXT REWARD IN 03:11", List.of("Keep this menu open.", "Movement does not reset your timer.",
				"Credits earned this session: " + credits)));
		return m;
	}

	@Test
	void countsFromTheMenusCredits() {
		AfkTracker t = new AfkTracker();
		long now = 0;
		// Credits earned before we were watching aren't counted.
		assertNull(t.observe(now, title(600), menu("Old Dragon", 5)));
		// Lots of ticks, timer running, reopen in between: nothing.
		for (int i = 0; i < 300; i++) assertNull(t.observe(now += 1000, title(599 - i), menu("Old Dragon", 5)));
		t.menuOpened();
		assertNull(t.observe(now += 1000, title(900), menu("Old Dragon", 5)));
		// A reward where the mark moves with the credits.
		assertEquals("Wise Dragon", t.observe(now += 1000, title(900), menu("Wise Dragon", 6)));
		// The credits go up before the mark moves: wait for the mark.
		assertNull(t.observe(now += 50, title(900), menu("Wise Dragon", 7)));
		assertEquals("Superior Dragon", t.observe(now += 50, title(900), menu("Superior Dragon", 7)));
		// The same dragon twice: counted once the wait is over.
		assertNull(t.observe(now += 50, title(900), menu("Superior Dragon", 8)));
		assertEquals("Superior Dragon", t.observe(now += AfkTracker.MARK_WAIT_MS, title(899), menu("Superior Dragon", 8)));
		// Timer jumps never count.
		assertNull(t.observe(now += 1000, title(10), menu("Superior Dragon", 8)));
		assertNull(t.observe(now += 1000, title(900), menu("Superior Dragon", 8)));
		assertEquals(3, t.total());
		assertEquals(2, t.counts().get("Superior Dragon"));
		assertEquals(3, t.credits());
		// A reconnect starts the server's count over: its new credits are new rewards.
		t.menuOpened();
		assertNull(t.observe(now += 60_000, title(900), menu("Superior Dragon", 0)));
		assertEquals("Old Dragon", t.observe(now += 1000, title(900), menu("Old Dragon", 1)));
		assertEquals(4, t.total());
	}

	@Test
	void withoutCreditsTheMarkMovingCounts() {
		AfkTracker t = new AfkTracker();
		assertNull(t.observe(0, title(500), menu("Old Dragon")));
		assertNull(t.observe(1000, title(499), menu("Old Dragon")));
		t.menuOpened();
		// Reopened: the timer is different, no new reward.
		assertNull(t.observe(60_000, title(900), menu("Old Dragon")));
		assertEquals("Wise Dragon", t.observe(61_000, title(899), menu("Wise Dragon")));
		// Countdown starting over from near zero: the same dragon again.
		assertNull(t.observe(62_000, title(5), menu("Wise Dragon")));
		assertEquals("Wise Dragon", t.observe(63_000, title(900), menu("Wise Dragon")));
		assertEquals(2, t.total());
	}
}
