package com.golemaddons.ui;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.autoclick.ClickScheduler;
import com.golemaddons.config.GolemConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Auto clicker: on/off, the CPS range, and the key (or mouse button) to hold. */
final class AutoClickerScreen extends EditorScreen {
	private static final int ROW = 20;

	private GolemButton keyButton;
	private boolean listening;
	private int minTextX;
	private int maxTextX;
	private int cpsTextY;
	private int hintY;

	AutoClickerScreen(Screen parent) {
		super(Component.translatable("golemaddons.autoclicker.title"), parent);
	}

	@Override
	protected void build() {
		GolemConfig.AutoClicker s = working.autoClicker;
		int panelW = maxContentWidth(360);
		int inner = panelW - 2 * PANEL_PAD - 2;
		int px = (width - panelW) / 2;
		int ix = px + PANEL_PAD + 2;

		int mainH = PANEL_LABEL + ROW + 4 + ROW + PANEL_PAD + 2;
		int speedH = PANEL_LABEL + ROW + PANEL_PAD + 2;
		int y = centeredTop(mainH + speedH + PANEL_GAP + 24);

		panels.add(new Panel(px, y, panelW, mainH, Component.translatable("golemaddons.autoclicker.panel.main")));
		int rowY = y + PANEL_LABEL + 2;
		addRenderableWidget(GolemCycle.onOffBuilder(s.enabled)
				.create(ix, rowY, inner, ROW, Component.translatable("golemaddons.autoclicker.enabled"), (b, v) -> s.enabled = v));
		keyButton = GolemButton.builder(Component.empty(), b -> {
			listening = !listening;
			updateKeyButton();
		}).tooltip(Tooltip.create(Component.translatable("golemaddons.autoclicker.key.tooltip"))).bounds(ix, rowY + ROW + 4, inner, ROW).build();
		addRenderableWidget(keyButton);
		updateKeyButton();
		y += mainH + PANEL_GAP;

		panels.add(new Panel(px, y, panelW, speedH, Component.translatable("golemaddons.autoclicker.panel.speed")));
		rowY = y + PANEL_LABEL + 2;
		int half = (inner - 2 * GAP) / 2;
		// [-] min [+]   [-] max [+]
		addRenderableWidget(GolemButton.builder(Component.literal("-"), b -> change(-1, 0)).bounds(ix, rowY, ROW, ROW).build());
		addRenderableWidget(GolemButton.builder(Component.literal("+"), b -> change(1, 0)).bounds(ix + half - ROW, rowY, ROW, ROW).build());
		addRenderableWidget(GolemButton.builder(Component.literal("-"), b -> change(0, -1)).bounds(ix + half + 2 * GAP, rowY, ROW, ROW).build());
		addRenderableWidget(GolemButton.builder(Component.literal("+"), b -> change(0, 1)).bounds(ix + inner - ROW, rowY, ROW, ROW).build());
		minTextX = ix + half / 2;
		maxTextX = ix + half + 2 * GAP + half / 2;
		cpsTextY = rowY + 6;
		hintY = y + speedH + 6;

		addFooter(saveButton(), cancelButton());
	}

	/** Changes min/max by the deltas, keeping 1..20 and min <= max (pushing the other value along). */
	private void change(int minDelta, int maxDelta) {
		GolemConfig.AutoClicker s = working.autoClicker;
		s.minCps = Math.clamp(s.minCps + minDelta, ClickScheduler.MIN_CPS, ClickScheduler.MAX_CPS);
		s.maxCps = Math.clamp(s.maxCps + maxDelta, ClickScheduler.MIN_CPS, ClickScheduler.MAX_CPS);
		if (minDelta != 0 && s.minCps > s.maxCps) s.maxCps = s.minCps;
		if (maxDelta != 0 && s.maxCps < s.minCps) s.minCps = s.maxCps;
	}

	private void updateKeyButton() {
		KeyMapping key = GolemAddonsClient.autoClickerKey();
		keyButton.setMessage(listening
				? Component.translatable("golemaddons.autoclicker.key.listening").withColor(UiText.YELLOW & 0xFFFFFF)
				: Component.translatable("golemaddons.autoclicker.key", key.isUnbound()
						? Component.translatable("golemaddons.autoclicker.key.none") : key.getTranslatedKeyMessage()));
	}

	/** Saved right away, like the Controls screen, so the key works even if you press Cancel. */
	private void bind(InputConstants.Key input) {
		GolemAddonsClient.autoClickerKey().setKey(input);
		KeyMapping.resetMapping();
		minecraft.options.save();
		listening = false;
		updateKeyButton();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening) {
			// Escape clears the binding instead of closing the screen.
			bind(event.key() == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (listening) {
			bind(InputConstants.Type.MOUSE.getOrCreate(event.button()));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		GolemConfig.AutoClicker s = working.autoClicker;
		graphics.centeredText(font, Component.translatable("golemaddons.autoclicker.min", s.minCps), minTextX, cpsTextY, UiText.WHITE);
		graphics.centeredText(font, Component.translatable("golemaddons.autoclicker.max", s.maxCps), maxTextX, cpsTextY, UiText.WHITE);
		graphics.centeredText(font, Component.translatable("golemaddons.autoclicker.hint"), width / 2, hintY, UiText.GRAY);
		graphics.centeredText(font, Component.translatable("golemaddons.autoclicker.hint2"), width / 2, hintY + 11, UiText.DARK_GRAY);
	}
}
