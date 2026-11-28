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
 * <p>The menu's clock says "Credits earned this session: N", and the dragon behind the latest reward carries a
 * "Your latest reward" line. When the credits go up, one reward is counted for the dragon that then carries the mark
 * (waiting a moment for the mark, which can update after the credits). The menu's own count is the source of truth,
 * so reopening the menu or a timer jump can never add rewards.
 *
 * <p>Without a credits line (an older menu), a reward is counted only when the mark moves to another dragon, or when
 * the countdown starts over from near zero.
 */
public final class AfkTracker {
	/** One slot of the menu: the item's name and its tooltip lines, as plain text. */
	public record Slot(String name, List<String> lore) {
	}

	private static final Pattern TIMER = Pattern.compile("(\\d{1,3}):(\\d{2})(?!.*\\d:\\d)");
	private static final Pattern CREDITS = Pattern.compile("credits earned this session:\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);
	/** How long to wait for the latest-reward mark after the credits went up. */
	static final long MARK_WAIT_MS = 3000;

	private final Map<String, Integer> counts = new LinkedHashMap<>();
	private int total;
	private int credits;
	private String lastLatest;
	private int lastTimer = -1;
	/** Credits last read from the menu, or -1 before the first reading. */
	private int lastCredits = -1;
	private boolean creditsSeen;
	/** Rewards seen in the credits but not yet given a dragon. */
	private int pending;
	private long pendingAt;
	private String latestBeforePending;

	public static boolean isAfkMenu(String title) {
		return title != null && title.strip().toLowerCase(Locale.ROOT).startsWith("afk rewards");
	}

	/** Seconds shown in the title, or -1. */
	static int timer(String title) {
		if (title == null) return -1;
		Matcher m = TIMER.matcher(title);
		return m.find() ? Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)) : -1;
	}

	/** "Credits earned this session: N" anywhere in the menu, or -1. */
	static int credits(List<Slot> slots) {
		for (Slot s : slots) {
			if (s == null) continue;
			for (String line : s.lore()) {
				if (line == null) continue;
				Matcher m = CREDITS.matcher(line);
				if (m.find()) {
					try {
						return Integer.parseInt(m.group(1).replace(",", ""));
					} catch (NumberFormatException e) {
						return -1;
					}
				}
			}
		}
		return -1;
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

	/** The menu was (re)opened: readings start over as a baseline (nothing is counted for the reopen itself). */
	public void menuOpened() {
		lastTimer = -1;
	}

	/** Called whenever the menu's contents or title may have changed. Returns the dragon just counted, or null. */
	public String observe(long now, String title, List<Slot> slots) {
		String latest = latest(slots);
		int c = credits(slots);
		String counted = null;
		if (c >= 0) {
			creditsSeen = true;
			if (lastCredits < 0) {
				lastCredits = c; // what was earned before we were watching isn't ours to count
			} else if (c > lastCredits) {
				if (pending == 0) {
					latestBeforePending = lastLatest;
					pendingAt = now;
				}
				pending += c - lastCredits;
				credits += c - lastCredits;
				lastCredits = c;
			} else if (c < lastCredits) {
				// The server started a new session (a reconnect): its credits are all new.
				if (c > 0 && pending == 0) {
					latestBeforePending = lastLatest;
					pendingAt = now;
				}
				pending += c;
				credits += c;
				lastCredits = c;
			}
			if (pending > 0) {
				boolean markMoved = latest != null && !latest.equals(latestBeforePending);
				if (markMoved || now - pendingAt >= MARK_WAIT_MS) {
					String dragon = latest != null ? latest : "Dragon";
					// One rise in the credits is one reward (a reward can be worth more than one credit).
					count(dragon);
					counted = dragon;
					pending = 0;
				}
			}
		} else if (!creditsSeen) {
			counted = fallback(title, latest);
		}
		if (latest != null) lastLatest = latest;
		return counted;
	}

	/** Older menus without a credits line: the mark moving, or the countdown starting over. */
	private String fallback(String title, String latest) {
		int t = timer(title);
		boolean restarted = lastTimer >= 0 && t >= 0 && lastTimer <= 15 && t >= lastTimer + 120;
		if (t >= 0) lastTimer = t;
		if (latest == null || lastLatest == null) return null;
		if (!latest.equals(lastLatest) || restarted) {
			count(latest);
			return latest;
		}
		return null;
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

	/** Loot credits earned while tracking (from the menu's own count). */
	public int credits() {
		return credits;
	}

	public void clear() {
		counts.clear();
		total = 0;
		credits = 0;
		lastLatest = null;
		lastCredits = -1;
		creditsSeen = false;
		pending = 0;
		menuOpened();
	}
}
