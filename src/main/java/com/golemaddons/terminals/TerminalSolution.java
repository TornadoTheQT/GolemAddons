package com.golemaddons.terminals;

import java.util.List;

/**
 * What to click in a terminal: the steps in order, and a status line for the GUI. {@code ready} is false while the
 * chest is still filling in or does not look like the terminal, and then there are no steps.
 */
public record TerminalSolution(TerminalType type, List<Step> steps, Status status, String arg) {
	/** Click {@code slot} {@code clicks} times, with the right button when {@code right}. */
	public record Step(int slot, int clicks, boolean right) {
	}

	public enum Status {
		WAITING(false), UNRECOGNISED(false), NEXT(true), MAKE_GREEN(true), SELECT(true), SAME_COLOR(true), WAIT_ALIGN(true),
		CLICK_NOW(true), DONE(true);

		public final boolean ready;

		Status(boolean ready) {
			this.ready = ready;
		}

		public String key() {
			return "golemaddons.terminals.status." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public TerminalSolution {
		steps = List.copyOf(steps);
	}

	static TerminalSolution waiting(TerminalType type) {
		return new TerminalSolution(type, List.of(), Status.WAITING, "");
	}

	public boolean ready() {
		return status.ready;
	}

	/** The step for this slot, or null. */
	public Step stepAt(int slot) {
		for (Step s : steps) {
			if (s.slot() == slot) return s;
		}
		return null;
	}

	/** 0 for the step to click now, 1 for the one after, and so on; -1 if the slot has no step. */
	public int indexOf(int slot) {
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).slot() == slot) return i;
		}
		return -1;
	}
}
