package com.golemaddons.autosell;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.mixin.AbstractContainerScreenAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wires selling into the game: the button and keybind on the sell screen, the tick loop, and quick sell
 * (one key: run the shop command, sell everything once the sell screen opens, then close it).
 */
public final class AutoSellFeature {
	private static final Logger LOGGER = LoggerFactory.getLogger("golemaddons");
	private static final int MIN_BUTTON_WIDTH = 110;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;
	private static final int COUNT_REFRESH_TICKS = 5;
	/** How long quick sell waits for the sell screen to open after running the command. */
	static final int QUICK_OPEN_TIMEOUT_TICKS = 100;
	/** Ticks to wait after the sell screen opens so the server can send its contents before scanning. */
	static final int QUICK_SETTLE_TICKS = 6;

	private enum QuickPhase { IDLE, WAITING_FOR_SCREEN, SETTLING, SELLING }

	private final KeyMapping sellKey;
	private final KeyMapping quickSellKey;
	private AutoSellRules rules = AutoSellRules.DISABLED;
	/** Normalized quick-sell command; "" when off, null when invalid. */
	private String quickCommand = "";
	private QuickPhase quickPhase = QuickPhase.IDLE;
	private int quickTicks;
	private AbstractContainerScreen<?> quickScreen;
	private SellSession session;
	/** Result of the last run on the currently open screen, shown under the button. */
	private Component lastResult;
	private AbstractContainerScreen<?> lastResultScreen;

	public AutoSellFeature(KeyMapping.Category category) {
		this.sellKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.golemaddons.autosell", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		this.quickSellKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.golemaddons.quicksell", InputConstants.Type.KEYSYM, InputConstants.KEY_I, category));
	}

	public KeyMapping quickSellKey() {
		return quickSellKey;
	}

	public void register(ConfigManager config) {
		config.addListener(c -> {
			rules = AutoSellRules.from(c);
			quickCommand = QuickSellCommand.normalize(c.selling.quickSellCommand);
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> container
					&& InventoryScan.isCandidateScreen(screen)
					&& rules.isSellScreenTitle(screen.getTitle().getString())) {
				attach(mc, container);
			}
		});
	}

	private void attach(Minecraft mc, AbstractContainerScreen<?> screen) {
		AbstractContainerScreenAccessor pos = (AbstractContainerScreenAccessor) screen;
		int buttonWidth = Math.max(MIN_BUTTON_WIDTH, 16 + Math.max(
				mc.font.width(Component.translatable("golemaddons.autosell.button.no_rules")),
				mc.font.width(Component.translatable("golemaddons.autosell.button.sell", 36))));
		int left = pos.golemaddons$getLeftPos();
		int top = pos.golemaddons$getTopPos();
		int right = left + pos.golemaddons$getImageWidth();
		// Prefer the right of the GUI, then above it, then the left; never on top of the slots.
		int x;
		int y;
		if (right + GAP + buttonWidth <= screen.width) {
			x = right + GAP;
			y = top;
		} else if (top - GAP - BUTTON_HEIGHT >= 0) {
			x = left;
			y = top - GAP - BUTTON_HEIGHT;
		} else {
			x = Math.max(0, left - GAP - buttonWidth);
			y = top;
		}

		Button button = Button.builder(Component.empty(), b -> toggle(mc, screen))
				.bounds(x, y, buttonWidth, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("golemaddons.autosell.button.tooltip")))
				.build();
		Screens.getWidgets(screen).add(button);
		int statusY = y + BUTTON_HEIGHT + 3;
		int[] tickCounter = {0};
		updateButton(mc, screen, button);

		ScreenEvents.afterTick(screen).register(s -> {
			if (isRunningOn(screen) || ++tickCounter[0] % COUNT_REFRESH_TICKS == 0) {
				updateButton(mc, screen, button);
			}
		});
		ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
			if (lastResult != null && lastResultScreen == screen) {
				graphics.text(Screens.getFont(s), lastResult, x, statusY, 0xFFFFFFFF, true);
			}
		});
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> {
			if (!sellKey.isUnbound() && sellKey.matches(key)) {
				toggle(mc, screen);
				return false;
			}
			return true;
		});
		ScreenEvents.remove(screen).register(s -> {
			if (session != null && session.belongsTo(screen)) {
				session.cancel(SellSession.StopReason.SCREEN_CHANGED);
				finishSession(mc);
			}
			if (lastResultScreen == screen) {
				lastResult = null;
				lastResultScreen = null;
			}
		});
	}

	private boolean isRunningOn(AbstractContainerScreen<?> screen) {
		return session != null && session.isRunning() && session.belongsTo(screen);
	}

	private void updateButton(Minecraft mc, AbstractContainerScreen<?> screen, Button button) {
		if (isRunningOn(screen)) {
			button.active = true;
			button.setMessage(Component.translatable("golemaddons.autosell.button.stop", session.sold(), session.total()));
			return;
		}
		AutoSellRules current = rules;
		if (current.sellsNothing()) {
			button.active = false;
			button.setMessage(Component.translatable("golemaddons.autosell.button.no_rules"));
			return;
		}
		int count = mc.player == null ? 0 : InventoryScan.sellableSlots(screen.getMenu(), mc.player, current).size();
		button.active = count > 0;
		button.setMessage(count > 0
				? Component.translatable("golemaddons.autosell.button.sell", count)
				: Component.translatable("golemaddons.autosell.button.nothing"));
	}

	private void toggle(Minecraft mc, AbstractContainerScreen<?> screen) {
		if (isRunningOn(screen)) {
			session.cancel(SellSession.StopReason.CANCELLED);
			finishSession(mc);
			return;
		}
		if (session != null && session.isRunning()) {
			return; // a run on another screen is still winding down
		}
		AutoSellRules current = rules;
		if (mc.screen != screen || current.sellsNothing() || !current.isSellScreenTitle(screen.getTitle().getString())) {
			return;
		}
		lastResult = null;
		session = SellSession.start(mc, screen, current);
		lastResultScreen = screen;
		if (!session.isRunning()) {
			finishSession(mc);
		}
	}

	private void onEndTick(Minecraft mc) {
		while (quickSellKey.consumeClick()) {
			startQuickSell(mc);
		}
		tickQuickSell(mc);
		if (session == null) {
			return;
		}
		try {
			session.tick(mc);
		} catch (RuntimeException e) {
			LOGGER.error("Auto-sell stopped after an unexpected error", e);
		}
		if (!session.isRunning()) {
			finishSession(mc);
		}
	}

	private void startQuickSell(Minecraft mc) {
		if (mc.player == null || mc.screen != null || quickPhase != QuickPhase.IDLE || session != null) {
			return;
		}
		String problem = null;
		if (quickCommand == null) {
			problem = "golemaddons.quick.error.command";
		} else if (quickCommand.isEmpty()) {
			problem = "golemaddons.quick.error.off";
		} else if (!rules.titleResult().valid()) {
			problem = "golemaddons.quick.error.title";
		} else if (rules.sellsNothing()) {
			problem = "golemaddons.quick.error.nothing";
		}
		if (problem != null) {
			chat(mc, Component.translatable(problem).withStyle(ChatFormatting.YELLOW));
			return;
		}
		quickPhase = QuickPhase.WAITING_FOR_SCREEN;
		quickTicks = 0;
		chat(mc, Component.translatable("golemaddons.quick.running", quickCommand).withStyle(ChatFormatting.GRAY));
		mc.player.connection.sendCommand(quickCommand);
	}

	private void tickQuickSell(Minecraft mc) {
		if (quickPhase == QuickPhase.IDLE || quickPhase == QuickPhase.SELLING) {
			return;
		}
		if (mc.player == null) {
			resetQuickSell();
			return;
		}
		quickTicks++;
		if (quickPhase == QuickPhase.WAITING_FOR_SCREEN) {
			if (mc.screen instanceof AbstractContainerScreen<?> container && InventoryScan.isCandidateScreen(container)
					&& rules.isSellScreenTitle(container.getTitle().getString())) {
				quickScreen = container;
				quickPhase = QuickPhase.SETTLING;
				quickTicks = 0;
			} else if (quickTicks > QUICK_OPEN_TIMEOUT_TICKS) {
				chat(mc, (mc.screen == null
						? Component.translatable("golemaddons.quick.error.no_screen", quickCommand)
						: Component.translatable("golemaddons.quick.error.wrong_screen", quickCommand, mc.screen.getTitle().getString()))
						.withStyle(ChatFormatting.RED));
				resetQuickSell();
			}
			return;
		}
		// SETTLING: give the server a moment to fill the menu before scanning it.
		if (mc.screen != quickScreen) {
			resetQuickSell();
			return;
		}
		if (quickTicks >= QUICK_SETTLE_TICKS) {
			quickPhase = QuickPhase.SELLING;
			toggle(mc, quickScreen);
			if (quickPhase == QuickPhase.SELLING && session == null) {
				resetQuickSell(); // the run never started, so there is nothing to finish or close
			}
		}
	}

	private void resetQuickSell() {
		quickPhase = QuickPhase.IDLE;
		quickScreen = null;
		quickTicks = 0;
	}

	private static void chat(Minecraft mc, Component message) {
		if (mc.player != null) {
			mc.gui.getChat().addClientSystemMessage(Component.literal("[GolemAddons] ").withStyle(ChatFormatting.GRAY).append(message));
		}
	}

	private void finishSession(Minecraft mc) {
		SellSession done = session;
		session = null;
		if (done == null) {
			return;
		}
		boolean ok = done.state() == SellSession.State.FINISHED;
		MutableComponent message = Component.translatable(done.stopReason().translationKey, done.sold(), done.total())
				.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
		lastResult = message;
		List<SellRule> tripped = done.rules().matcher().trippedRules();
		chat(mc, message);
		for (SellRule rule : tripped) {
			chat(mc, Component.translatable("golemaddons.autosell.result.tripped", rule.displayName()).withStyle(ChatFormatting.YELLOW));
		}

		if (quickPhase == QuickPhase.SELLING) {
			AbstractContainerScreen<?> screen = quickScreen;
			resetQuickSell();
			// Quick sell closes the GUI (like pressing Escape) only after a clean finish. If the run was stopped,
			// the GUI stays open so the reason is visible.
			if (ok && mc.player != null && mc.screen == screen) {
				mc.player.closeContainer();
			}
		}
	}
}
