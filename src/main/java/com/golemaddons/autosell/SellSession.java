package com.golemaddons.autosell;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One sell run: shift-clicks (quick-moves) each queued stack, one per {@code clickDelayTicks}, on the client tick.
 *
 * <p>Before every click it re-checks that the same screen and menu are open, the cursor is empty and the slot still
 * holds a sellable stack. Any doubt skips the slot or stops the run.
 */
public final class SellSession {
	public enum State { RUNNING, FINISHED, ABORTED }

	public enum StopReason {
		DONE("golemaddons.autosell.result.done"),
		NOTHING_TO_SELL("golemaddons.autosell.result.nothing"),
		CANCELLED("golemaddons.autosell.result.cancelled"),
		SCREEN_CHANGED("golemaddons.autosell.result.screen_changed"),
		CURSOR_NOT_EMPTY("golemaddons.autosell.result.cursor"),
		SERVER_REJECTED("golemaddons.autosell.result.rejected"),
		CLICK_LIMIT("golemaddons.autosell.result.click_limit"),
		DISCONNECTED("golemaddons.autosell.result.disconnected"),
		ERROR("golemaddons.autosell.result.error");

		public final String translationKey;

		StopReason(String translationKey) {
			this.translationKey = translationKey;
		}
	}

	/** Backstop against loops: never more clicks than two passes over a full inventory. */
	static final int MAX_CLICKS = 36 * 2;
	static final int MAX_CONSECUTIVE_FAILURES = 3;
	/** How long to wait for the server to confirm a click before counting it as rejected. */
	static final int CONFIRM_TIMEOUT_TICKS = 10;

	private final AbstractContainerScreen<?> screen;
	private final AbstractContainerMenu menu;
	private final int containerId;
	private final AutoSellRules rules;
	private final Deque<Integer> queue;
	private final int total;

	private State state = State.RUNNING;
	private StopReason stopReason;
	private int sold;
	private int clicks;
	private int consecutiveFailures;
	private int ticksSinceClick;
	private int pendingSlot = -1;
	private ItemStack pendingBefore = ItemStack.EMPTY;

	private SellSession(AbstractContainerScreen<?> screen, AutoSellRules rules, Deque<Integer> queue) {
		this.screen = screen;
		this.menu = screen.getMenu();
		this.containerId = menu.containerId;
		this.rules = rules;
		this.queue = queue;
		this.total = queue.size();
		// First click happens on the next tick.
		this.ticksSinceClick = rules.clickDelayTicks();
	}

	/** Builds the queue from the current contents. Returns a session that may already be finished (nothing to sell). */
	public static SellSession start(Minecraft mc, AbstractContainerScreen<?> screen, AutoSellRules rules) {
		SellSession session = new SellSession(screen, rules,
				mc.player == null ? new ArrayDeque<>() : new ArrayDeque<>(InventoryScan.sellableSlots(screen.getMenu(), mc.player, rules)));
		if (!session.contextValid(mc)) {
			session.stop(State.ABORTED, mc.player == null ? StopReason.DISCONNECTED : StopReason.SCREEN_CHANGED);
		} else if (!session.menu.getCarried().isEmpty()) {
			session.stop(State.ABORTED, StopReason.CURSOR_NOT_EMPTY);
		} else if (session.queue.isEmpty()) {
			session.stop(State.FINISHED, StopReason.NOTHING_TO_SELL);
		}
		return session;
	}

	public void tick(Minecraft mc) {
		if (state != State.RUNNING) {
			return;
		}
		try {
			tickInner(mc);
		} catch (RuntimeException e) {
			stop(State.ABORTED, StopReason.ERROR);
			throw e;
		}
	}

	private void tickInner(Minecraft mc) {
		if (!contextValid(mc)) {
			stop(State.ABORTED, mc.player == null ? StopReason.DISCONNECTED : StopReason.SCREEN_CHANGED);
			return;
		}
		ticksSinceClick++;

		if (pendingSlot >= 0) {
			ItemStack now = menu.getSlot(pendingSlot).getItem();
			if (!ItemStack.matches(now, pendingBefore)) {
				sold++;
				consecutiveFailures = 0;
				pendingSlot = -1;
			} else if (ticksSinceClick >= Math.max(CONFIRM_TIMEOUT_TICKS, rules.clickDelayTicks())) {
				pendingSlot = -1;
				if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
					stop(State.ABORTED, StopReason.SERVER_REJECTED);
					return;
				}
			} else {
				return; // still waiting for the server
			}
		}

		if (ticksSinceClick < rules.clickDelayTicks()) {
			return;
		}

		while (!queue.isEmpty()) {
			int slotIndex = queue.poll();
			Slot slot = menu.getSlot(slotIndex);
			if (!InventoryScan.isSellable(slot, mc.player, rules)) {
				continue; // moved or changed since the scan: skip it
			}
			if (!menu.getCarried().isEmpty()) {
				stop(State.ABORTED, StopReason.CURSOR_NOT_EMPTY);
				return;
			}
			if (clicks >= MAX_CLICKS) {
				stop(State.ABORTED, StopReason.CLICK_LIMIT);
				return;
			}
			pendingSlot = slotIndex;
			pendingBefore = slot.getItem().copy();
			ticksSinceClick = 0;
			clicks++;
			// Same packet a real shift-click sends.
			mc.gameMode.handleContainerInput(containerId, slotIndex, 0, ContainerInput.QUICK_MOVE, mc.player);
			return;
		}

		stop(State.FINISHED, StopReason.DONE);
	}

	private boolean contextValid(Minecraft mc) {
		return mc.player != null
				&& mc.gameMode != null
				&& mc.screen == screen
				&& mc.player.containerMenu == menu
				&& menu.containerId == containerId;
	}

	/** Stops the run. Safe to call at any time; only the first stop is recorded. */
	public void cancel(StopReason reason) {
		stop(State.ABORTED, reason);
	}

	private void stop(State newState, StopReason reason) {
		if (state != State.RUNNING) {
			return;
		}
		state = newState;
		stopReason = reason;
		pendingSlot = -1;
		queue.clear();
	}

	public boolean isRunning() {
		return state == State.RUNNING;
	}

	public boolean belongsTo(AbstractContainerScreen<?> other) {
		return screen == other;
	}

	public State state() {
		return state;
	}

	public StopReason stopReason() {
		return stopReason;
	}

	public int sold() {
		return sold;
	}

	public int total() {
		return total;
	}

	public AutoSellRules rules() {
		return rules;
	}
}
