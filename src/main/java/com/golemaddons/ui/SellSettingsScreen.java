package com.golemaddons.ui;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.autosell.QuickSellCommand;
import com.golemaddons.autosell.RuleValidator;
import com.golemaddons.config.GolemConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Settings shared by every sell feature, plus the quick-sell key. */
final class SellSettingsScreen extends EditorScreen {
	private RuleValidator.Result titleResult;
	private String commandResult;
	private Button keyButton;
	private boolean listeningForKey;

	private int titleStatusY;
	private int delayTextX;
	private int delayTextY;
	private int quickHintY;
	private int commandStatusY;
	private int textX;

	SellSettingsScreen(Screen parent) {
		super(Component.translatable("golemaddons.settings.title"), parent);
	}

	@Override
	protected void build() {
		GolemConfig.Selling settings = working.selling;
		int panelW = maxContentWidth(420);
		int inner = panelW - 2 * PANEL_PAD - 2;
		int px = (width - panelW) / 2;
		int ix = px + PANEL_PAD + 2;
		textX = ix;

		int titleH = PANEL_LABEL + 20 + 14 + PANEL_PAD;
		int pacingH = PANEL_LABEL + 20 + PANEL_PAD + 2;
		int quickH = PANEL_LABEL + 12 + 20 + 14 + PANEL_PAD;
		int y = centeredTop(titleH + pacingH + quickH + 2 * PANEL_GAP);

		// Sell screen title
		panels.add(new Panel(px, y, panelW, titleH, Component.translatable("golemaddons.config.sell_screen_title")));
		EditBox titleBox = new EditBox(font, ix, y + PANEL_LABEL + 2, inner, 20, Component.translatable("golemaddons.config.sell_screen_title"));
		titleBox.setMaxLength(RuleValidator.MAX_PATTERN_LENGTH);
		titleBox.setValue(settings.sellScreenTitlePattern);
		titleBox.setHint(Component.translatable("golemaddons.config.sell_screen_title.hint"));
		titleResult = RuleValidator.validateScreenTitlePattern(settings.sellScreenTitlePattern);
		titleBox.setResponder(value -> {
			settings.sellScreenTitlePattern = value;
			titleResult = RuleValidator.validateScreenTitlePattern(value);
		});
		addRenderableWidget(titleBox);
		titleStatusY = y + PANEL_LABEL + 26;
		y += titleH + PANEL_GAP;

		// Pacing and protection
		panels.add(new Panel(px, y, panelW, pacingH, Component.translatable("golemaddons.settings.pacing")));
		int rowY = y + PANEL_LABEL + 2;
		int delayTextW = font.width(Component.translatable("golemaddons.config.delay", 20, 1000)) + 12;
		addRenderableWidget(Button.builder(Component.literal("-"), b -> changeDelay(-1)).bounds(ix, rowY, 20, 20).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> changeDelay(1)).bounds(ix + 20 + delayTextW, rowY, 20, 20).build());
		delayTextX = ix + 20 + delayTextW / 2;
		delayTextY = rowY + 6;
		Component hotbar = Component.translatable("golemaddons.config.protect_hotbar");
		int hotbarW = font.width(hotbar) + font.width(": OFF") + 16;
		addRenderableWidget(CycleButton.onOffBuilder(settings.protectHotbar)
				.create(ix + inner - hotbarW, rowY, hotbarW, 20, hotbar, (button, value) -> settings.protectHotbar = value));
		y += pacingH + PANEL_GAP;

		// Quick sell
		panels.add(new Panel(px, y, panelW, quickH, Component.translatable("golemaddons.quick.title")));
		quickHintY = y + PANEL_LABEL + 1;
		int quickRowY = y + PANEL_LABEL + 12;
		keyButton = Button.builder(Component.empty(), b -> {
			listeningForKey = !listeningForKey;
			updateKeyButton();
		}).tooltip(Tooltip.create(Component.translatable("golemaddons.quick.key.tooltip"))).build();
		int keyW = Math.max(110, font.width(Component.translatable("golemaddons.quick.key.listening")) + 16);
		keyButton.setRectangle(keyW, 20, ix + inner - keyW, quickRowY);
		updateKeyButton();
		addRenderableWidget(keyButton);

		EditBox commandBox = new EditBox(font, ix, quickRowY, inner - keyW - 6, 20, Component.translatable("golemaddons.quick.command"));
		commandBox.setMaxLength(QuickSellCommand.MAX_LENGTH + 1);
		commandBox.setValue(settings.quickSellCommand);
		commandBox.setHint(Component.translatable("golemaddons.quick.command.hint"));
		commandResult = QuickSellCommand.normalize(settings.quickSellCommand);
		commandBox.setResponder(value -> {
			settings.quickSellCommand = value;
			commandResult = QuickSellCommand.normalize(value);
		});
		addRenderableWidget(commandBox);
		commandStatusY = quickRowY + 24;

		addFooter(saveButton(), cancelButton());
		setInitialFocus(titleBox);
	}

	private void changeDelay(int delta) {
		GolemConfig.Selling settings = working.selling;
		settings.clickDelayTicks = Math.clamp(settings.clickDelayTicks + delta,
				GolemConfig.Selling.MIN_DELAY_TICKS, GolemConfig.Selling.MAX_DELAY_TICKS);
	}

	private void updateKeyButton() {
		KeyMapping key = GolemAddonsClient.quickSellKey();
		keyButton.setMessage(listeningForKey
				? Component.translatable("golemaddons.quick.key.listening").withColor(UiText.YELLOW & 0xFFFFFF)
				: Component.translatable("golemaddons.quick.key", key.getTranslatedKeyMessage()));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listeningForKey) {
			KeyMapping key = GolemAddonsClient.quickSellKey();
			// Escape clears the binding instead of closing the screen.
			key.setKey(event.key() == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
			KeyMapping.resetMapping();
			minecraft.options.save();
			listeningForKey = false;
			updateKeyButton();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (titleResult.valid()) {
			graphics.text(font, Component.translatable("golemaddons.config.sell_screen_title.ok"), textX, titleStatusY, UiText.GREEN);
		} else if (titleResult.problem() == RuleValidator.Problem.BLANK) {
			graphics.text(font, Component.translatable("golemaddons.config.sell_screen_title.required"), textX, titleStatusY, UiText.YELLOW);
		} else {
			graphics.text(font, UiText.problem(titleResult), textX, titleStatusY, UiText.RED);
		}

		int ticks = working.selling.clickDelayTicks;
		graphics.centeredText(font, Component.translatable("golemaddons.config.delay", ticks, ticks * 50), delayTextX, delayTextY, UiText.WHITE);

		graphics.text(font, Component.translatable("golemaddons.quick.hint"), textX, quickHintY, UiText.GRAY);
		if (commandResult == null) {
			graphics.text(font, Component.translatable("golemaddons.quick.command.invalid"), textX, commandStatusY, UiText.RED);
		} else if (commandResult.isEmpty()) {
			graphics.text(font, Component.translatable("golemaddons.quick.command.off"), textX, commandStatusY, UiText.YELLOW);
		} else if (GolemAddonsClient.quickSellKey().isUnbound()) {
			graphics.text(font, Component.translatable("golemaddons.quick.key.unbound", commandResult), textX, commandStatusY, UiText.YELLOW);
		} else {
			graphics.text(font, Component.translatable("golemaddons.quick.ready", GolemAddonsClient.quickSellKey().getTranslatedKeyMessage(),
					commandResult), textX, commandStatusY, UiText.GREEN);
		}
	}
}
