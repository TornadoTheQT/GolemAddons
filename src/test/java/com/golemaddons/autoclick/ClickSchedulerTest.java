package com.golemaddons.autoclick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClickSchedulerTest {
	private static final long TICK = 50_000_000L;

	/** Runs ticks for {@code seconds} and returns the average clicks per second. */
	private static double simulate(ClickScheduler s, int min, int max, int seconds) {
		long now = 1_000_000_000L;
		int clicks = 0;
		for (int i = 0; i < seconds * 20; i++) {
			clicks += s.due(now, min, max);
			now += TICK;
		}
		return clicks / (double) seconds;
	}

	@Test
	void averageRateStaysInsideTheRange() {
		Random r = new Random(1);
		double cps = simulate(new ClickScheduler(r::nextDouble), 8, 12, 120);
		assertTrue(cps >= 8 && cps <= 12, "cps " + cps);
	}

	@Test
	void fixedRateIsHit() {
		assertEquals(10.0, simulate(new ClickScheduler(() -> 0.5), 10, 10, 60), 0.2);
		assertEquals(20.0, simulate(new ClickScheduler(() -> 0.5), 20, 20, 60), 0.2);
	}

	@Test
	void gapsAreIrregular() {
		Random r = new Random(7);
		ClickScheduler s = new ClickScheduler(r::nextDouble);
		Set<Integer> gaps = new HashSet<>();
		long now = 0;
		int last = -1;
		for (int i = 0; i < 400; i++) {
			if (s.due(now, 6, 14) > 0) {
				if (last >= 0) gaps.add(i - last);
				last = i;
			}
			now += TICK;
		}
		assertTrue(gaps.size() > 1, "gaps " + gaps);
	}

	@Test
	void clicksImmediatelyAndNeverBurstsAfterLag() {
		ClickScheduler s = new ClickScheduler(() -> 0.5);
		assertEquals(1, s.due(0, 10, 10), "first click on key down");
		assertTrue(s.due(5_000_000_000L, 10, 10) <= ClickScheduler.MAX_PER_TICK, "no burst after a 5 s stall");
	}

	@Test
	void swappedAndOutOfRangeLimitsAreHandled() {
		double cps = simulate(new ClickScheduler(() -> 0.5), 15, 5, 60);
		assertEquals(10.0, cps, 0.3);
		assertEquals(20.0, simulate(new ClickScheduler(() -> 0.5), 50, 99, 30), 0.3);
	}

	@Test
	void resetStartsFresh() {
		ClickScheduler s = new ClickScheduler(() -> 0.5);
		s.due(0, 1, 1);
		assertEquals(0, s.due(TICK, 1, 1));
		s.reset();
		assertEquals(1, s.due(2 * TICK, 1, 1));
	}
}
