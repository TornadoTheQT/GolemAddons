package com.golemaddons.ui;

import com.golemaddons.afk.AfkFeature;
import com.golemaddons.afk.AfkHud;
import com.golemaddons.config.GolemConfig;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** AFK helper settings: reconnect and reopen behaviour, the tracker HUD, the current session, and test tools. */
final class AfkScreen extends EditorScreen {
	private static final int ROW = 20;
	private static final int TAB_H = 18;
	private static int tab;

	private int infoX;
	private int infoY;

	AfkScreen(Screen parent) {
		super(Component.translatable("golemaddons.afk.title"), parent);
	}

	@Override
	protected void build() {
		GolemConfig.Afk a = working.afk;
		int panelW = maxContentWidth(420);
		int inner = panelW - 2 * PANEL_PAD - 2;
		int half = (inner - GAP * 2) / 2;
		int px = (width - panelW) / 2;
		int ix = px + PANEL_PAD + 2;
		int rx = ix + inner - half;
		int top = HEADER_HEIGHT + 8;
		int bottom = height - FOOTER_HEIGHT - 8;
		addRenderableWidget(new GolemSegmented(px, top, panelW, TAB_H, Component.translatable("golemaddons.afk.tabs"),
				List.of(Component.translatable("golemaddons.afk.tab.helper"), Component.translatable("golemaddons.afk.tab.session")),
				tab, UiText.ACCENT, 0, i -> {
					tab = i;
					rebuildWidgets();
				}).hideLabel());
		int y = top + TAB_H + 6;
		panels.add(new Panel(px, y, panelW, bottom - y, Component.translatable(tab == 0 ? "golemaddons.afk.panel.helper" : "golemaddons.afk.panel.session")));
		int rowY = y + PANEL_LABEL + 2;
		infoX = ix;
		if (tab == 0) {
			toggle(ix, rowY, half, "golemaddons.afk.enabled", a.enabled, v -> a.enabled = v);
			toggle(rx, rowY, half, "golemaddons.afk.reconnect", a.autoReconnect, v -> a.autoReconnect = v);
			rowY += ROW + 4;
			toggle(ix, rowY, half, "golemaddons.afk.reopen", a.autoReopen, v -> a.autoReopen = v);
			toggle(rx, rowY, half, "golemaddons.afk.show_hud", a.showHud, v -> a.showHud = v);
			rowY += ROW + 6;
			slider(ix, rowY, half, "golemaddons.afk.reconnect_delay", 2, 120, 1, 10, () -> a.reconnectDelayS, v -> a.reconnectDelayS = (int) v, "%.0f s");
			rowY = slider(rx, rowY, half, "golemaddons.afk.reopen_delay", 1, 30, 1, 5, () -> a.reopenDelayS, v -> a.reopenDelayS = (int) v, "%.0f s");
			GolemEditBox hub = addRenderableWidget(new GolemEditBox(font, ix, rowY, half, ROW, Component.translatable("golemaddons.afk.hub")));
			hub.setMaxLength(64);
			hub.setValue(a.hubCommand);
			hub.setHint(Component.translatable("golemaddons.afk.hub.hint"));
			hub.setTooltip(Tooltip.create(Component.translatable("golemaddons.afk.hub.tooltip")));
			hub.setResponder(v -> a.hubCommand = v);
			addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.afk.edit_hud"), b -> editHud())
					.tooltip(Tooltip.create(Component.translatable("golemaddons.afk.edit_hud.tooltip"))).bounds(rx, rowY, half, ROW).build());
			rowY += ROW + 4;
			toggle(ix, rowY, inner, "golemaddons.afk.hud_always", a.hudAlways, v -> a.hudAlways = v);
			infoY = -1;
		} else {
			AfkFeature f = AfkFeature.instance();
			addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.afk.stop"), b -> {
				f.stop("golemaddons.afk.stopped.command");
				rebuildWidgets();
			}).bounds(ix, rowY, half, ROW).build()).active = f.running();
			addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.afk.reset_counts"), b -> f.tracker().clear())
					.bounds(rx, rowY, half, ROW).build());
			rowY += ROW + 4;
			infoY = rowY;
			int testY = bottom - PANEL_PAD - ROW * 2 - 4;
			toggle(ix, testY, inner, "golemaddons.afk.test_tools", a.testTools, v -> {
				a.testTools = v;
				rebuildWidgets();
			});
			testY += ROW + 4;
			int third = (inner - GAP * 4) / 3;
			boolean canTest = a.testTools && minecraft.level != null;
			test(ix, testY, third, "golemaddons.afk.test.disconnect", canTest, f::testDisconnect);
			test(ix + third + GAP * 2, testY, third, "golemaddons.afk.test.lobby", canTest, f::testLobby);
			test(ix + inner - third, testY, third, "golemaddons.afk.test.reward", a.testTools, () -> f.testReward(null));
		}
		addFooter(saveButton(), cancelButton());
	}

	private void test(int x, int y, int w, String key, boolean active, Runnable action) {
		addRenderableWidget(GolemButton.builder(Component.translatable(key), b -> {
			// Keep the settings, close the menu, then do it in the game.
			if (com.golemaddons.GolemAddonsClient.config().save(working.copy())) {
				minecraft.setScreen(null);
				minecraft.schedule(action);
			}
		}).tooltip(Tooltip.create(Component.translatable(key + ".tooltip"))).bounds(x, y, w, ROW).build()).active = active;
	}

	private void editHud() {
		AfkFeature f = AfkFeature.instance();
		minecraft.setScreen(new HudEditScreen(this, Component.translatable("golemaddons.afk.edit_hud"), working.afk.hud,
				new GolemConfig.Afk().hud, font -> AfkHud.size(font, sample()), (g, x, y, scale) -> AfkHud.draw(g, font, x, y, scale, sample())));
	}

	private static AfkHud.Model sample() {
		AfkFeature f = AfkFeature.instance();
		if (f.running() && f.tracker().total() > 0) return f.model(Util.getMillis());
		Map<String, Integer> counts = new java.util.LinkedHashMap<>();
		counts.put("Old Dragon", 3);
		counts.put("Wise Dragon", 2);
		counts.put("Superior Dragon", 1);
		return new AfkHud.Model("2h 14m", Component.translatable("golemaddons.afk.status.afk"), 0xFF55FF55, counts, 6, 1);
	}

	private void toggle(int x, int y, int w, String key, boolean value, Consumer<Boolean> onChange) {
		addRenderableWidget(GolemCycle.onOffBuilder(value)
				.withTooltip(v -> Tooltip.create(Component.translatable(key + ".tooltip")))
				.create(x, y, w, ROW, Component.translatable(key), (b, v) -> onChange.accept(v)));
	}

	private int slider(int x, int y, int w, String key, double min, double max, double step, double def,
			java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set, String format) {
		GolemSlider s = addRenderableWidget(new GolemSlider(x, y, w, Component.translatable(key), min, max, step, def, get, set,
				v -> String.format(Locale.ROOT, format, v), UiText.ACCENT_2));
		s.setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
		return y + GolemSlider.HEIGHT + 3;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		if (infoY < 0) return;
		AfkHud.Model m = AfkFeature.instance().model(Util.getMillis());
		int y = infoY;
		graphics.text(font, Component.translatable("golemaddons.afk.info.status", m.status()), infoX, y, m.statusColor());
		y += 11;
		if (!m.elapsed().isEmpty()) {
			graphics.text(font, Component.translatable("golemaddons.afk.info.time", m.elapsed(), m.reconnects()), infoX, y, UiText.GRAY);
			y += 11;
		}
		graphics.text(font, Component.translatable("golemaddons.afk.info.total", m.total()), infoX, y, UiText.GRAY);
	}
}
