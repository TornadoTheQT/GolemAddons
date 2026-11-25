package com.golemaddons.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ProfileCacheTest {
	private final AtomicLong now = new AtomicLong(1_000_000);
	private final ProfileCache<String> cache = new ProfileCache<>(now::get);
	private final UUID a = UUID.randomUUID();
	private final UUID b = UUID.randomUUID();

	@Test
	void queuesOnceAndDedupsWhileInFlight() {
		assertTrue(cache.request(a));
		assertFalse(cache.request(a), "already queued");
		assertEquals(List.of(a), cache.drain(100));
		assertFalse(cache.request(a), "in flight");
		cache.resolved(a, "x");
		assertEquals("x", cache.value(a));
		assertFalse(cache.request(a), "fresh");
	}

	@Test
	void refreshesAfterTtlButKeepsShowingStaleValue() {
		cache.request(a);
		cache.drain(10);
		cache.resolved(a, "x");
		now.addAndGet(ProfileCache.FRESH_MS);
		assertTrue(cache.request(a));
		assertEquals("x", cache.value(a), "stale value stays until the refresh lands");
	}

	@Test
	void negativeEntriesLastLonger() {
		cache.request(a);
		cache.drain(10);
		cache.resolved(a, null);
		assertNull(cache.value(a));
		now.addAndGet(ProfileCache.FRESH_MS);
		assertFalse(cache.request(a));
		now.addAndGet(ProfileCache.ABSENT_MS - ProfileCache.FRESH_MS);
		assertTrue(cache.request(a));
	}

	@Test
	void failuresBackOffExponentially() {
		cache.request(a);
		cache.failed(cache.drain(10));
		now.addAndGet(ProfileCache.FIRST_RETRY_MS - 1);
		assertFalse(cache.request(a));
		now.addAndGet(1);
		assertTrue(cache.request(a));
		cache.failed(cache.drain(10));
		now.addAndGet(ProfileCache.FIRST_RETRY_MS);
		assertFalse(cache.request(a), "second failure waits twice as long");
		now.addAndGet(ProfileCache.FIRST_RETRY_MS);
		assertTrue(cache.request(a));
	}

	@Test
	void drainRespectsBatchSize() {
		for (int i = 0; i < 150; i++) cache.request(UUID.randomUUID());
		assertEquals(100, cache.drain(100).size());
		assertEquals(50, cache.queued());
	}

	@Test
	void clearQueueAllowsRequeue() {
		cache.request(a);
		cache.request(b);
		cache.clearQueue();
		assertEquals(0, cache.queued());
		assertTrue(cache.request(a));
	}
}
