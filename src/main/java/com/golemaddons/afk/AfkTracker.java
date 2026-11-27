package com.golemaddons.afk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts the dragons an /afk session has rewarded, from what the AFK Rewards menu shows. Pure (no Minecraft classes).
 *
 * <p>The menu marks the dragon behind the most recent reward with a "Your latest reward" line, and its title shows
 * the reward timer ("AFK Rewards · 10:31"). A reward is counted when that mark moves to another dragon, or when the
 * timer jumps (it starts over after each reward) - so the same dragon twice in a row still counts twice. Reopening
 * the menu also restarts the timer, so the first reading after a reopen is only a new starting point.
 */
public final class AfkTracker {
	/** One slot of the menu: the item's name and its tooltip lines, as plain text. */
	public record Slot(String name, List<String> lore) {
	}

	private static final Pattern TIMER = Pattern.compile("(\\d{1,3}):(\\d{2})(?!.*\\d:\\d)");
	/** A timer reading this far from where it should be means it started over. */
	static final int JUMP_SECONDS = 45;

	private final Map<String, Integer> counts = new LinkedHashMap<>();
	private int total;
	private String lastLatest;
	private int lastTimer = -1;
	private long lastTimerAt;
	/** -1 counts down, +1 counts up, 0 not known yet. */
	private int direction;
	private boolean resetSeen;
	private long resetAt;

	public static boolean isAfkMenu(String title) {
		return title != null && title.strip().toLowerCase(Locale.ROOT).startsWith("afk rewards");
	}

	/** Seconds shown in the title, or -1. */
	static int timer(String title) {
		if (title == null) return -1;
		Matcher m = TIMER.matcher(title);
		return m.find() ? Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)) : -1;
	}

	/** The dragon carrying the "latest reward" line, or null. */
	static String latest(List<Slot> slots) {
		for (Slot s : slots) {
			if (s == null || s.name() == null) continue;
			for (String line : s.lore()) {
				if (line != null && line.toLowerCase(Locale.ROOT).contains("latest reward")) return s.name().strip();
			}
		}
		return null;
	}

	/** The menu was (re)opened: its timer starts over, so the next reading is a new starting point. */
	public void menuOpened() {
		lastTimer = -1;
		direction = 0;
		resetSeen = false;
	}

	/** Called whenever the menu's contents or title may have changed. Returns the dragon just counted, or null. */
	public String observe(long now, String title, List<Slot> slots) {
		int t = timer(title);
		String latest = latest(slots);
		boolean reset = false;
		if (t >= 0) {
			if (lastTimer >= 0) {
				int dt = (int) Math.round((now - lastTimerAt) / 1000.0);
				int change = t - lastTimer;
				if (direction == 0 && change != 0 && Math.abs(change) <= dt + 2) direction = Integer.signum(change);
				if (direction != 0) {
					int expected = lastTimer + direction * dt;
					reset = Math.abs(t - expected) > JUMP_SECONDS;
				}
			}
			if (t != lastTimer) {
				lastTimer = t;
				lastTimerAt = now;
			}
		}
		if (reset) {
			resetSeen = true;
			resetAt = now;
		}
		String counted = null;
		if (latest != null) {
			boolean moved = lastLatest != null && !latest.equals(lastLatest);
			if (moved || (resetSeen && lastLatest != null)) {
				counted = latest;
				count(latest);
				resetSeen = false;
			}
			if (lastLatest == null && resetSeen) {
				// First mark seen right after a reset: that's the reward that caused it.
				counted = latest;
				count(latest);
				resetSeen = false;
			}
			lastLatest = latest;
		}
		// A reset with no mark showing up within a few seconds isn't attributed to anything.
		if (resetSeen && now - resetAt > 5000) resetSeen = false;
		return counted;
	}

	/** Counts a reward directly (the test command uses it). */
	public void count(String dragon) {
		counts.merge(dragon, 1, Integer::sum);
		total++;
	}

	public Map<String, Integer> counts() {
		return java.util.Collections.unmodifiableMap(counts);
	}

	public int total() {
		return total;
	}

	public void clear() {
		counts.clear();
		total = 0;
		lastLatest = null;
		menuOpened();
	}
}
