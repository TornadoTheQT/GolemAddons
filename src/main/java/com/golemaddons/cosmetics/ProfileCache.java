package com.golemaddons.cosmetics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-UUID cosmetics cache with freshness, negative entries and retry backoff. Plain Java, unit tested with a fake
 * clock. Reads ({@link #value}) are a single lock-free map lookup, so the render thread can call them every frame;
 * a stale value keeps being shown until the refresh lands, so nothing flickers.
 *
 * <p>Also tracks which UUIDs are queued or in flight so the same player is never requested twice at once.
 */
public final class ProfileCache<V> {
	public static final long FRESH_MS = 10 * 60_000L;
	public static final long ABSENT_MS = 15 * 60_000L;
	public static final long FIRST_RETRY_MS = 30_000L;
	public static final long MAX_RETRY_MS = 10 * 60_000L;
	public static final int MAX_ENTRIES = 5_000;

	/** Immutable, swapped whole, so readers never see a half-updated entry. */
	private record Entry<V>(V value, long refreshAt, int failures) {
	}

	private final Map<UUID, Entry<V>> entries = new ConcurrentHashMap<>();
	/** Queued or in flight. */
	private final Map<UUID, Boolean> pending = new ConcurrentHashMap<>();
	private final List<UUID> queue = new ArrayList<>();
	private final LongSupplier clock;

	public ProfileCache(LongSupplier clock) {
		this.clock = clock;
	}

	/** The cached cosmetics, fresh or stale; null when none are known. Safe from any thread, allocation-free. */
	public V value(UUID uuid) {
		Entry<V> entry = entries.get(uuid);
		return entry == null ? null : entry.value();
	}

	/** Queues the UUID if it has no fresh entry and isn't already queued or in flight. Returns true if queued. */
	public synchronized boolean request(UUID uuid) {
		Entry<V> entry = entries.get(uuid);
		if (entry != null && clock.getAsLong() < entry.refreshAt()) {
			return false;
		}
		if (pending.putIfAbsent(uuid, Boolean.TRUE) != null) {
			return false;
		}
		queue.add(uuid);
		return true;
	}

	public synchronized int queued() {
		return queue.size();
	}

	/** Takes up to {@code max} queued UUIDs for one request. They stay pending until resolved. */
	public synchronized List<UUID> drain(int max) {
		int n = Math.min(max, queue.size());
		List<UUID> batch = new ArrayList<>(queue.subList(0, n));
		queue.subList(0, n).clear();
		return batch;
	}

	/** Stores a result; {@code value == null} means "this player has no cosmetics". */
	public void resolved(UUID uuid, V value) {
		long now = clock.getAsLong();
		entries.put(uuid, new Entry<>(value, now + (value == null ? ABSENT_MS : FRESH_MS), 0));
		pending.remove(uuid);
		trim();
	}

	/** The lookup failed (network, server error): keep any old value and back off before retrying. */
	public void failed(Collection<UUID> uuids) {
		long now = clock.getAsLong();
		for (UUID uuid : uuids) {
			Entry<V> old = entries.get(uuid);
			int failures = old == null ? 1 : old.failures() + 1;
			long delay = Math.min(MAX_RETRY_MS, FIRST_RETRY_MS << Math.min(10, failures - 1));
			entries.put(uuid, new Entry<>(old == null ? null : old.value(), now + delay, failures));
			pending.remove(uuid);
		}
		trim();
	}

	/** Forget everything queued (e.g. on disconnect); cached values stay. */
	public synchronized void clearQueue() {
		for (UUID uuid : queue) pending.remove(uuid);
		queue.clear();
	}

	public int size() {
		return entries.size();
	}

	/** Rarely runs: drops expired entries first, then the ones due soonest, until under the cap. */
	private void trim() {
		if (entries.size() <= MAX_ENTRIES) {
			return;
		}
		long now = clock.getAsLong();
		entries.entrySet().removeIf(e -> e.getValue().refreshAt() <= now && !pending.containsKey(e.getKey()));
		if (entries.size() > MAX_ENTRIES) {
			List<Map.Entry<UUID, Entry<V>>> all = new ArrayList<>(entries.entrySet());
			all.sort((a, b) -> Long.compare(a.getValue().refreshAt(), b.getValue().refreshAt()));
			for (int i = 0; i < all.size() - MAX_ENTRIES * 3 / 4; i++) {
				entries.remove(all.get(i).getKey());
			}
		}
	}
}
