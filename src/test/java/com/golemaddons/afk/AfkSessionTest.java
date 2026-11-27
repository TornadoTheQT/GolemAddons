package com.golemaddons.afk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AfkSessionTest {
	private static final AfkSession.Timing T = new AfkSession.Timing(5_000, 10_000, true, true, "hub");
	private static final AfkSession.View AFK = new AfkSession.View(true, true, false, false);
	private static final AfkSession.View NO_MENU = new AfkSession.View(true, false, false, false);
	private static final AfkSession.View DISCONNECTED = new AfkSession.View(false, false, true, false);
	private static final AfkSession.View CONNECTING = new AfkSession.View(false, false, false, true);
	private static final AfkSession.View LOADING = new AfkSession.View(false, false, false, false);

	/** Ticks until an action comes out, returning it (or null after the limit). */
	private static AfkSession.Action until(AfkSession s, long[] now, AfkSession.View view, long limitMs) {
		long end = now[0] + limitMs;
		while (now[0] < end) {
			now[0] += 50;
			AfkSession.Action a = s.tick(now[0], view, T);
			if (a != null) return a;
		}
		return null;
	}

	@Test
	void aWarpReopensTheMenuAfterTheDelay() {
		long[] now = {0};
		AfkSession s = new AfkSession(0);
		assertNull(until(s, now, AFK, 1000));
		assertEquals(AfkSession.State.AFKING, s.state());
		long closedAt = now[0];
		AfkSession.Action a = until(s, now, NO_MENU, 20_000);
		assertEquals(new AfkSession.Action.Command("afk"), a);
		assertTrue(now[0] - closedAt >= 5_000, "waits the reopen delay");
		until(s, now, AFK, 200);
		assertEquals(AfkSession.State.AFKING, s.state());
	}

	@Test
	void whenAfkDoesntWorkItTriesTheHubThenAfk() {
		long[] now = {0};
		AfkSession s = new AfkSession(0);
		until(s, now, AFK, 200);
		assertEquals(new AfkSession.Action.Command("afk"), until(s, now, NO_MENU, 20_000));
		assertEquals(new AfkSession.Action.Command("hub"), until(s, now, NO_MENU, 20_000));
		assertEquals(new AfkSession.Action.Command("afk"), until(s, now, NO_MENU, 20_000));
	}

	@Test
	void aDroppedConnectionReconnectsWithBackoffThenReopens() {
		long[] now = {0};
		AfkSession s = new AfkSession(0);
		until(s, now, AFK, 200);
		long dropped = now[0];
		assertInstanceOf(AfkSession.Action.Reconnect.class, until(s, now, DISCONNECTED, 60_000));
		assertTrue(now[0] - dropped >= 10_000);
		// The reconnect fails: the next try waits twice as long.
		until(s, now, CONNECTING, 2_000);
		long failed = now[0];
		assertInstanceOf(AfkSession.Action.Reconnect.class, until(s, now, DISCONNECTED, 120_000));
		assertTrue(now[0] - failed >= 20_000, "backoff");
		assertEquals(2, s.reconnects());
		// It works: loading, then in the world, then /afk after the reopen delay.
		until(s, now, LOADING, 2_000);
		assertEquals(new AfkSession.Action.Command("afk"), until(s, now, NO_MENU, 20_000));
		until(s, now, AFK, 200);
		assertEquals(AfkSession.State.AFKING, s.state());
	}

	@Test
	void leavingTheDisconnectScreenYourselfStopsIt() {
		long[] now = {0};
		AfkSession s = new AfkSession(0);
		until(s, now, AFK, 200);
		until(s, now, DISCONNECTED, 1_000);
		until(s, now, LOADING, 200); // the server list, not a reconnect
		assertEquals(AfkSession.State.STOPPED, s.state());
	}

	@Test
	void backoffIsCapped() {
		assertEquals(10_000, AfkSession.backoff(10_000, 0));
		assertEquals(40_000, AfkSession.backoff(10_000, 2));
		assertEquals(AfkSession.MAX_BACKOFF_MS, AfkSession.backoff(10_000, 30));
	}
}
