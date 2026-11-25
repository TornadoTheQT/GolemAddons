package com.golemaddons.terminals;

import com.golemaddons.terminals.TerminalSolution.Status;
import com.golemaddons.terminals.TerminalSolution.Step;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * One open terminal: which clicks have been sent and not yet answered, which the server has confirmed, and the
 * hover-click timing. Pure, so its rules are tested directly.
 *
 * <p>A sent click is taken off the hints at once, so the next one can be clicked without waiting a round trip.
 * The server's slot update for it confirms it; one that is never answered (a dropped packet, a board reset) is
 * forgotten after {@link #ANSWER_TIMEOUT_MS} and its hint comes back.
 */
public final class TerminalSession {
	public static final long ANSWER_TIMEOUT_MS = 900;
	/** Assumed round trip until one has been measured. */
	static final long DEFAULT_PING_MS = 150;
	/** Extra time a Melody click is given on top of the ping, for the frame and the hand that clicks it. */
	static final long MELODY_MARGIN_MS = 60;
	/** Round trip of terminal clicks, measured from click to answer and smoothed. Shared by all terminals. */
	private static long pingEstimate = -1;

	/** The timing the hover click follows, in milliseconds. */
	public record HoverTiming(long firstClickDelay, long hoverDelay, long clickDelay) {
	}

	private record Pending(int slot, boolean right, long at) {
	}

	private final int containerId;
	private final String title;
	private final TerminalType type;
	private final long openedAt;
	private final Set<Integer> confirmed = new HashSet<>();
	private final List<Pending> pending = new ArrayList<>();
	private long lastClickAt = Long.MIN_VALUE / 2;
	private int hovered = -1;
	private long hoverSince;
	private int blockedSlot = -1;
	private long blockedAt = Long.MIN_VALUE / 2;
	private boolean finishedReported;
	// Melody timing: which slot the moving pane is in, since when, and how long it usually stays.
	private String moverSlot = "";
	private long moverSince;
	private long stepEstimate = -1;

	public TerminalSession(int containerId, String title, long now) {
		this.containerId = containerId;
		this.title = title;
		this.type = TerminalType.of(title);
		this.openedAt = now;
	}

	public int containerId() {
		return containerId;
	}

	public String title() {
		return title;
	}

	public TerminalType type() {
		return type;
	}

	public long openedAt() {
		return openedAt;
	}

	/** The solution as the player should see it: the server's board, less the clicks already on their way. */
	public TerminalSolution view(TerminalBoard board, long now) {
		pending.removeIf(p -> now - p.at() > ANSWER_TIMEOUT_MS);
		TerminalSolution raw = TerminalSolver.solve(title, board, confirmed);
		if (raw != null && type == TerminalType.MELODY) raw = timeMelody(raw, now);
		if (raw == null || !raw.ready() || pending.isEmpty()) return raw;
		// Melody: one click per alignment. Until the server has judged it, there is nothing to click.
		if (type == TerminalType.MELODY) return new TerminalSolution(type, List.of(), Status.WAIT_ALIGN, "");
		List<Step> steps = new ArrayList<>();
		for (Step step : raw.steps()) {
			int sent = 0;
			for (Pending p : pending) {
				if (p.slot() == step.slot() && p.right() == step.right()) sent++;
			}
			if (step.clicks() > sent) steps.add(new Step(step.slot(), step.clicks() - sent, step.right()));
		}
		if (steps.isEmpty()) return new TerminalSolution(type, steps, Status.DONE, "");
		String arg = type == TerminalType.ORDER ? Integer.toString(board.get(steps.getFirst().slot()).count()) : raw.arg();
		return new TerminalSolution(type, steps, raw.status(), arg);
	}

	/**
	 * Melody is judged when the click reaches the server. Only offer the click while there is still time for it to
	 * arrive before the pane moves on: the pane's usual time per column, less how long it has been here, must cover
	 * the ping. Until one step has been timed, nothing is offered.
	 */
	private TerminalSolution timeMelody(TerminalSolution raw, long now) {
		if (raw.status() != Status.WAIT_ALIGN && raw.status() != Status.CLICK_NOW) return raw;
		if (!raw.arg().equals(moverSlot)) {
			long interval = now - moverSince;
			if (!moverSlot.isEmpty() && interval >= 40 && interval <= 3000) {
				stepEstimate = stepEstimate < 0 ? interval : (stepEstimate * 3 + interval) / 4;
			}
			moverSlot = raw.arg();
			moverSince = now;
		}
		if (raw.status() != Status.CLICK_NOW) return raw;
		long ping = pingEstimate < 0 ? DEFAULT_PING_MS : pingEstimate;
		if (stepEstimate < 0 || now - moverSince + ping + MELODY_MARGIN_MS > stepEstimate) {
			return new TerminalSolution(raw.type(), List.of(), Status.WAIT_ALIGN, raw.arg());
		}
		return raw;
	}

	private static void samplePing(long sample) {
		if (sample < 0 || sample > ANSWER_TIMEOUT_MS) return;
		pingEstimate = pingEstimate < 0 ? sample : (pingEstimate * 3 + sample) / 4;
	}

	/** For tests: forget the measured ping. */
	static void resetPing() {
		pingEstimate = -1;
	}

	/**
	 * Whether a click on this slot with this button is a correct one. In Click in Order only the next number is;
	 * in Same Color the button must turn the pane the right way.
	 */
	public static boolean correct(TerminalSolution view, int slot, boolean right) {
		if (view == null || !view.ready()) return false;
		int index = view.indexOf(slot);
		if (index < 0) return false;
		if (view.type() == TerminalType.ORDER && index != 0) return false;
		return view.type() != TerminalType.RUBIX || view.steps().get(index).right() == right;
	}

	/** The button a correct click on this slot uses (Same Color decides it per pane), or null when there is none. */
	public static Boolean buttonFor(TerminalSolution view, int slot) {
		if (view == null || !view.ready()) return null;
		int index = view.indexOf(slot);
		if (index < 0 || (view.type() == TerminalType.ORDER && index != 0)) return null;
		return view.steps().get(index).right();
	}

	/**
	 * Called every frame with the slot under the cursor (-1 for none). Returns the button to click with now
	 * (true = right) when hover click should fire, else null.
	 */
	public Boolean hoverClick(TerminalSolution view, int slot, long now, HoverTiming timing) {
		if (slot != hovered) {
			hovered = slot;
			hoverSince = now;
		}
		if (slot < 0 || now - openedAt < timing.firstClickDelay() || now - hoverSince < timing.hoverDelay()
				|| now - lastClickAt < timing.clickDelay()) {
			return null;
		}
		return buttonFor(view, slot);
	}

	/**
	 * Auto terms: the step to click now, or null while waiting out the first-click delay, the click delay, or the
	 * server. Takes the first step, so Click in Order goes in order and Same Colour uses each pane's own button.
	 */
	public Step autoClick(TerminalSolution view, long now, HoverTiming timing) {
		if (view == null || !view.ready() || view.steps().isEmpty()) return null;
		if (now - openedAt < timing.firstClickDelay() || now - lastClickAt < timing.clickDelay()) return null;
		return view.steps().getFirst();
	}

	public void sent(int slot, boolean right, long now) {
		pending.add(new Pending(slot, right, now));
		lastClickAt = now;
	}

	public void blocked(int slot, long now) {
		blockedSlot = slot;
		blockedAt = now;
	}

	/** The slot of the last refused click and how long ago it was, for a brief red flash. */
	public int blockedSlot() {
		return blockedSlot;
	}

	public long blockedAt() {
		return blockedAt;
	}

	public boolean hasPending(int slot) {
		for (Pending p : pending) {
			if (p.slot() == slot) return true;
		}
		return false;
	}

	/** The server sent this slot: that answers the oldest click on it. */
	public void serverSlot(int slot, long now) {
		if (type == TerminalType.MELODY) {
			if (!pending.isEmpty()) samplePing(now - pending.getFirst().at());
			pending.clear();
			return;
		}
		for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (p.slot() == slot) {
				it.remove();
				confirmed.add(slot);
				samplePing(now - p.at());
				return;
			}
		}
	}

	/** The server resent the whole chest: every click on the way has been handled. */
	public void serverContent() {
		for (Pending p : pending) confirmed.add(p.slot());
		pending.clear();
	}

	/** True once, the first time the terminal is seen solved; for the practice timer. */
	public boolean markFinished() {
		if (finishedReported) return false;
		finishedReported = true;
		return true;
	}
}
