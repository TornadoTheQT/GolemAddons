package com.golemaddons.afk;

/**
 * What the AFK helper should do next, as a pure state machine fed with what the game looks like each tick. The
 * client side carries out the actions and reports back; tests drive it with a fake clock.
 *
 * <p>While a session runs, the AFK menu should be open. If it closes without the player closing it (a warp, a lobby
 * restart), /afk is run again after a short delay; if that doesn't bring the menu back, the hub command is run first
 * and /afk tried again. If the connection drops, the helper reconnects to the same server, waiting longer after
 * each failed try (up to five minutes), then reopens the menu.
 */
public final class AfkSession {
	public enum State {
		/** The AFK menu is open. */
		AFKING,
		/** Waiting to run /afk (or the hub command) to get the menu back. */
		REOPENING,
		/** Disconnected; reconnecting when the countdown ends. */
		WAITING_TO_RECONNECT,
		/** A reconnect is in progress. */
		CONNECTING,
		STOPPED
	}

	/** What the game looks like right now. */
	public record View(boolean inWorld, boolean afkMenuOpen, boolean disconnected, boolean connecting) {
	}

	/** Something for the client to do. */
	public sealed interface Action {
		record Command(String command) implements Action {
		}

		record Reconnect() implements Action {
		}
	}

	public record Timing(long reopenDelayMs, long reconnectDelayMs, boolean autoReopen, boolean autoReconnect, String hubCommand) {
	}

	static final long MENU_TIMEOUT_MS = 8_000;
	static final long HUB_WAIT_MS = 6_000;
	static final long JOIN_TIMEOUT_MS = 45_000;
	static final long MAX_BACKOFF_MS = 300_000;

	private final long startedAt;
	private State state = State.AFKING;
	private long nextAt;
	private int reopenTries;
	private int reconnectTries;
	private int reconnects;
	private int reopens;
	private long stateSince;
	private boolean waitingForMenu;

	public AfkSession(long now) {
		this.startedAt = now;
		this.stateSince = now;
		// The player just ran /afk: give the menu time to open before treating it as missing.
		this.state = State.REOPENING;
		this.waitingForMenu = true;
		this.nextAt = now + MENU_TIMEOUT_MS;
	}

	public State state() {
		return state;
	}

	public long startedAt() {
		return startedAt;
	}

	public int reconnects() {
		return reconnects;
	}

	public int reopens() {
		return reopens;
	}

	/** Milliseconds until the next attempt, or 0. */
	public long waitLeft(long now) {
		return state == State.WAITING_TO_RECONNECT || (state == State.REOPENING && !waitingForMenu) ? Math.max(0, nextAt - now) : 0;
	}

	public void stop() {
		state = State.STOPPED;
	}

	/** Advances the session; returns an action to carry out now, or null. */
	public Action tick(long now, View view, Timing timing) {
		if (state == State.STOPPED) return null;
		if (view.disconnected() && state != State.WAITING_TO_RECONNECT) {
			if (!timing.autoReconnect()) {
				state = State.STOPPED;
				return null;
			}
			enter(State.WAITING_TO_RECONNECT, now);
			nextAt = now + backoff(timing.reconnectDelayMs(), reconnectTries);
			return null;
		}
		switch (state) {
			case AFKING -> {
				if (view.afkMenuOpen()) return null;
				if (!view.inWorld()) return null; // between worlds: wait for the next one
				if (!timing.autoReopen()) {
					state = State.STOPPED;
					return null;
				}
				enter(State.REOPENING, now);
				waitingForMenu = false;
				nextAt = now + timing.reopenDelayMs();
			}
			case REOPENING -> {
				if (view.afkMenuOpen()) {
					enter(State.AFKING, now);
					reopenTries = 0;
					reconnectTries = 0;
					return null;
				}
				if (!view.inWorld() || now < nextAt) return null;
				if (waitingForMenu) {
					// The last /afk didn't open the menu: try the hub first, then /afk again.
					waitingForMenu = false;
					reopenTries++;
					if (!timing.hubCommand().isBlank() && reopenTries % 2 == 1) {
						nextAt = now + HUB_WAIT_MS;
						return new Action.Command(timing.hubCommand());
					}
					nextAt = now + backoff(timing.reopenDelayMs(), reopenTries / 2);
					return null;
				}
				waitingForMenu = true;
				nextAt = now + MENU_TIMEOUT_MS;
				reopens++;
				return new Action.Command("afk");
			}
			case WAITING_TO_RECONNECT -> {
				if (!view.inWorld() && !view.disconnected() && !view.connecting()) {
					// The player left the disconnect screen themselves (Back to server list): they're awake.
					state = State.STOPPED;
					return null;
				}
				if (view.inWorld() && !view.disconnected()) {
					// Someone (the player, another mod) already reconnected.
					enter(State.REOPENING, now);
					waitingForMenu = false;
					nextAt = now + timing.reopenDelayMs();
					return null;
				}
				if (now < nextAt || view.connecting()) return null;
				enter(State.CONNECTING, now);
				reconnectTries++;
				reconnects++;
				return new Action.Reconnect();
			}
			case CONNECTING -> {
				if (view.inWorld()) {
					enter(State.REOPENING, now);
					waitingForMenu = false;
					nextAt = now + timing.reopenDelayMs();
				} else if (view.disconnected() || (!view.connecting() && now - stateSince > JOIN_TIMEOUT_MS)) {
					enter(State.WAITING_TO_RECONNECT, now);
					nextAt = now + backoff(timing.reconnectDelayMs(), reconnectTries);
				}
			}
			default -> {
			}
		}
		return null;
	}

	private void enter(State next, long now) {
		state = next;
		stateSince = now;
	}

	/** base, 2x, 4x... capped at five minutes. */
	static long backoff(long base, int tries) {
		long wait = base << Math.min(tries, 10);
		return Math.min(Math.max(base, wait), MAX_BACKOFF_MS);
	}
}
