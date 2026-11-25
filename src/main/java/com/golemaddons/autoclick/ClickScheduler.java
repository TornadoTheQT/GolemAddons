package com.golemaddons.autoclick;

import java.util.function.DoubleSupplier;

/**
 * Decides when the next click happens. Each gap between clicks uses a fresh random rate between the minimum and
 * maximum CPS, so the rhythm is never regular. Plain Java, unit tested.
 *
 * <p>Asked once per game tick; returns how many clicks are due (normally 0 or 1). After a stall (lag, a long frame)
 * it starts a fresh rhythm instead of firing a burst of missed clicks.
 */
public final class ClickScheduler {
	public static final int MIN_CPS = 1;
	public static final int MAX_CPS = 20;
	/** At most this many clicks in one tick, so a late tick can't burst. */
	static final int MAX_PER_TICK = 2;
	private static final long STALL_NANOS = 250_000_000L;

	private final DoubleSupplier random;
	private long nextClickAt = Long.MIN_VALUE;

	public ClickScheduler(DoubleSupplier random) {
		this.random = random;
	}

	/** Clicks due at {@code now} (nanoseconds) for a rate between {@code minCps} and {@code maxCps}. */
	public int due(long now, int minCps, int maxCps) {
		int lo = Math.clamp(Math.min(minCps, maxCps), MIN_CPS, MAX_CPS);
		int hi = Math.clamp(Math.max(minCps, maxCps), MIN_CPS, MAX_CPS);
		if (nextClickAt == Long.MIN_VALUE || now - nextClickAt > STALL_NANOS) {
			nextClickAt = now; // first click right away when the key goes down
		}
		int clicks = 0;
		while (now >= nextClickAt && clicks < MAX_PER_TICK) {
			clicks++;
			nextClickAt += interval(lo, hi);
		}
		return clicks;
	}

	/** Key released or clicking paused: the next press starts fresh. */
	public void reset() {
		nextClickAt = Long.MIN_VALUE;
	}

	private long interval(int lo, int hi) {
		double cps = lo + random.getAsDouble() * (hi - lo);
		return (long) (1_000_000_000.0 / cps);
	}
}
