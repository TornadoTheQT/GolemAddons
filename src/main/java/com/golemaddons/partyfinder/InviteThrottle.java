package com.golemaddons.partyfinder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * Paces the invite commands the leader's game sends, so a burst of accepted requests can't get the leader kicked
 * for chat spam: one command per {@link #SPACING_MS}, at most {@link #MAX_QUEUED} waiting, no duplicates.
 * Client thread only. Plain Java, unit tested.
 */
public final class InviteThrottle {
	public static final long SPACING_MS = 2_500;
	public static final int MAX_QUEUED = 5;

	private final Deque<String> queue = new ArrayDeque<>();
	private final LongSupplier clock;
	private long lastSent = Long.MIN_VALUE / 2;

	public InviteThrottle(LongSupplier clock) {
		this.clock = clock;
	}

	/** Queues a name; false if it's already queued or the queue is full. */
	public boolean offer(String name) {
		if (queue.contains(name) || queue.size() >= MAX_QUEUED) return false;
		queue.add(name);
		return true;
	}

	/** The next name to invite now, or null if none is due. */
	public String poll() {
		long now = clock.getAsLong();
		if (queue.isEmpty() || now - lastSent < SPACING_MS) return null;
		lastSent = now;
		return queue.poll();
	}

	public void clear() {
		queue.clear();
	}

	public int size() {
		return queue.size();
	}
}
