package com.golemaddons.ui;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.terminals.TerminalBoard;
import com.golemaddons.terminals.TerminalLayout;
import com.golemaddons.terminals.TerminalPractice;
import com.golemaddons.terminals.TerminalPuzzle;
import com.golemaddons.terminals.TerminalRenderer;
import com.golemaddons.terminals.TerminalSession;
import com.golemaddons.terminals.TerminalSolution;
import com.golemaddons.terminals.TerminalStacks;
import com.golemaddons.terminals.TerminalType;
import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Terminal solver settings: a live, clickable preview of the compact GUI on the left (in the colours and size being
 * edited), and two tabs on the right - how clicks work, and how it looks.
 */
final class TerminalsScreen extends EditorScreen {
	private static final int ROW = 20;
	private static final int TAB_H = 18;
	private static final String[] COLOR_KEYS = {"background", "tile", "solution", "second", "third", "blocked", "text"};

	private enum Tab {SOLVER, CLICKING, LOOK}

	private static Tab tab = Tab.SOLVER;
	private static int previewType = 0;
	private static int colorIndex = 2;

	private final long openedAt = Util.getMillis();
	private float tabSlideFrom = tab.ordinal();
	private final Random random = new Random();
	private TerminalPuzzle demo;
	private TerminalSession demoSession;
	private TerminalBoard demoBoard;
	private net.minecraft.world.item.ItemStack[] demoStacks;
	private int previewX0;
	private int previewY0;
	private int previewX1;
	private int previewY1;
	private int previewHintY;
	/** Hue, saturation, brightness of the colour being edited: kept apart so grey keeps its hue while dragging. */
	private final float[] hsb = new float[3];
	private GolemEditBox hex;
	/** Where the auto terms warning sign goes; -1 when the Clicking tab isn't showing. */
	private int warnX = -1;
	private int warnY;

	TerminalsScreen(Screen parent) {
		super(Component.translatable("golemaddons.terminals.title"), parent);
	}

	private GolemConfig.Terminals t() {
		return working.terminals;
	}

	@Override
	protected void build() {
		int totalW = maxContentWidth(660);
		int left = (width - totalW) / 2;
		int previewW = Math.round(totalW * 0.47f);
		int rightX = left + previewW + PANEL_GAP;
		int rightW = totalW - previewW - PANEL_GAP;
		int top = HEADER_HEIGHT + 8;
		int bottom = height - FOOTER_HEIGHT - 8;

		// Preview
		panels.add(new Panel(left, top, previewW, bottom - top, Component.translatable("golemaddons.terminals.panel.preview")));
		int px = left + PANEL_PAD + 2;
		int pw = previewW - 2 * PANEL_PAD - 2;
		addRenderableWidget(GolemCycle.builder((TerminalType type) -> Component.translatable(type.key), TerminalType.values()[previewType])
				.withValues(List.of(TerminalType.values()))
				.create(px, top + PANEL_LABEL + 2, pw, 16, Component.translatable("golemaddons.terminals.preview.type"), (b, v) -> {
					previewType = v.ordinal();
					newDemo();
				}));
		previewX0 = px;
		previewY0 = top + PANEL_LABEL + 22;
		previewX1 = px + pw;
		previewY1 = bottom - PANEL_PAD - 12;
		previewHintY = bottom - PANEL_PAD - 9;
		if (demo == null || demo.type() != TerminalType.values()[previewType]) newDemo();

		// Settings
		addRenderableWidget(new GolemSegmented(rightX, top, rightW, TAB_H, Component.translatable("golemaddons.terminals.tabs"),
				List.of(Component.translatable("golemaddons.terminals.tab.solver"), Component.translatable("golemaddons.terminals.tab.general"),
						Component.translatable("golemaddons.terminals.tab.look")),
				tab.ordinal(), UiText.ACCENT, openedAt + 60, i -> {
					tab = Tab.values()[i];
					rebuildWidgets();
				}).hideLabel().slideFrom(tabSlideFrom));
		tabSlideFrom = tab.ordinal();
		int y = top + TAB_H + 6;
		warnX = -1;
		if (tab == Tab.SOLVER) {
			buildSolver(rightX, y, rightW, bottom - y);
		} else if (tab == Tab.CLICKING) {
			buildGeneral(rightX, y, rightW, bottom - y);
		} else {
			buildLook(rightX, y, rightW, bottom - y);
		}

		addFooter(saveButton(), cancelButton(), GolemButton.builder(Component.translatable("golemaddons.terminals.practice"), b -> practice())
				.tooltip(Tooltip.create(Component.translatable("golemaddons.terminals.practice.tooltip"))).accent(UiText.ACCENT_2));
	}

	private void buildSolver(int x, int y, int w, int h) {
		panels.add(new Panel(x, y, w, h, Component.translatable("golemaddons.terminals.panel.solver")));
		int ix = x + PANEL_PAD + 2;
		int inner = w - 2 * PANEL_PAD - 2;
		int half = (inner - GAP * 2) / 2;
		int rx = ix + inner - half;
		int rowY = y + PANEL_LABEL + 2;
		GolemConfig.Terminals t = t();
		toggle(ix, rowY, half, "golemaddons.terminals.enabled", t.enabled, v -> t.enabled = v);
		toggle(rx, rowY, half, "golemaddons.terminals.custom_gui", t.customGui, v -> t.customGui = v);
		rowY += ROW + 4;
		toggle(ix, rowY, half, "golemaddons.terminals.block_wrong", t.blockWrongClicks, v -> t.blockWrongClicks = v);
		toggle(rx, rowY, half, "golemaddons.terminals.fade", t.fadeOthers, v -> t.fadeOthers = v);
		rowY += ROW + 6;
		slider(ix, rowY, half, "golemaddons.terminals.fade_amount", 0, 95, 5, 70, () -> t.fadePercent, v -> t.fadePercent = (int) v, "%.0f%%");
		rowY = slider(rx, rowY, half, "golemaddons.terminals.others_saturation", 0, 100, 5, 100, () -> t.othersSaturation,
				v -> t.othersSaturation = (int) v, "%.0f%%");
		slider(ix, rowY, half, "golemaddons.terminals.practice_ping", 0, 500, 5, 120, () -> t.practicePingMs,
				v -> t.practicePingMs = (int) v, "%.0f ms");
	}

	private void buildGeneral(int x, int y, int w, int h) {
		panels.add(new Panel(x, y, w, h, Component.translatable("golemaddons.terminals.panel.general")));
		int ix = x + PANEL_PAD + 2;
		int inner = w - 2 * PANEL_PAD - 2;
		int half = (inner - GAP * 2) / 2;
		int rx = ix + inner - half;
		int rowY = y + PANEL_LABEL + 2;
		GolemConfig.Terminals t = t();
		toggle(ix, rowY, inner, "golemaddons.terminals.hover_click", t.hoverClick, v -> t.hoverClick = v);
		rowY += ROW + 4;
		// Auto terms gets a warning sign beside it: it plays the terminal for you.
		warnX = ix;
		warnY = rowY;
		addRenderableWidget(GolemCycle.onOffBuilder(t.autoTerms)
				.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.terminals.auto.tooltip")))
				.create(ix + 18, rowY, inner - 18, ROW, Component.translatable("golemaddons.terminals.auto")
						.withStyle(net.minecraft.ChatFormatting.RED), (b, v) -> t.autoTerms = v));
		rowY += ROW + 6;
		slider(ix, rowY, half, "golemaddons.terminals.hover_delay", 0, 300, 5, 40, () -> t.hoverDelayMs, v -> t.hoverDelayMs = (int) v, "%.0f ms");
		rowY = slider(rx, rowY, half, "golemaddons.terminals.click_delay", 0, 500, 5, 150, () -> t.clickDelayMs, v -> t.clickDelayMs = (int) v, "%.0f ms");
		slider(ix, rowY, half, "golemaddons.terminals.first_delay", 0, 1000, 10, 350, () -> t.firstClickDelayMs,
				v -> t.firstClickDelayMs = (int) v, "%.0f ms");
	}

	private void buildLook(int x, int y, int w, int h) {
		panels.add(new Panel(x, y, w, h, Component.translatable("golemaddons.terminals.panel.look")));
		int ix = x + PANEL_PAD + 2;
		int inner = w - 2 * PANEL_PAD - 2;
		int rowY = y + PANEL_LABEL + 2;
		GolemConfig.Terminals t = t();
		int half = (inner - GAP * 2) / 2;
		int rx = ix + inner - half;
		slider(ix, rowY, half, "golemaddons.terminals.scale", GolemConfig.Terminals.MIN_SCALE, GolemConfig.Terminals.MAX_SCALE, 0.05, 1.5,
				() -> t.scale, v -> t.scale = (float) v, "%.2fx");
		rowY = slider(rx, rowY, half, "golemaddons.terminals.gap", 0, GolemConfig.Terminals.MAX_GAP, 1, 2, () -> t.gap, v -> t.gap = (int) v, "%.0f px");
		rowY += 2;

		List<Component> names = java.util.Arrays.stream(COLOR_KEYS)
				.map(k -> (Component) Component.translatable("golemaddons.terminals.color_name." + k)).toList();
		addRenderableWidget(new ColorSwatchRow(ix, rowY, inner, 14, names, this::color, colorIndex, i -> {
			colorIndex = i;
			loadHsb();
			hex.setValue(hexOf(color(colorIndex)));
		}));
		rowY += 14 + 5;
		loadHsb();
		slider(ix, rowY, half, "golemaddons.terminals.hue", 0, 360, 1, 0, () -> hsb[0] * 360.0, v -> {
			hsb[0] = (float) v / 360f;
			applyHsb();
		}, "%.0f°");
		rowY = slider(rx, rowY, half, "golemaddons.terminals.saturation", 0, 100, 1, 0, () -> hsb[1] * 100.0, v -> {
			hsb[1] = (float) v / 100f;
			applyHsb();
		}, "%.0f%%");
		slider(ix, rowY, half, "golemaddons.terminals.brightness", 0, 100, 1, 100, () -> hsb[2] * 100.0, v -> {
			hsb[2] = (float) v / 100f;
			applyHsb();
		}, "%.0f%%");
		rowY = slider(rx, rowY, half, "golemaddons.terminals.opacity", 0, 100, 1, 100, () -> (color(colorIndex) >>> 24) / 2.55, v -> {
			int a = (int) Math.round(v * 2.55);
			setColor(colorIndex, (a << 24) | (color(colorIndex) & 0xFFFFFF));
			hex.setValue(hexOf(color(colorIndex)));
		}, "%.0f%%");
		rowY += 2;
		hex = addRenderableWidget(new GolemEditBox(font, ix, rowY, half, ROW, Component.translatable("golemaddons.terminals.hex")));
		hex.setMaxLength(9);
		hex.setValue(hexOf(color(colorIndex)));
		hex.setTooltip(Tooltip.create(Component.translatable("golemaddons.terminals.hex.tooltip")));
		hex.setResponder(text -> {
			Integer parsed = parseHex(text);
			if (parsed != null && parsed != color(colorIndex)) {
				setColor(colorIndex, parsed);
				loadHsb();
			}
		});
		addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.terminals.reset_colors"), b -> {
			t.colors = new GolemConfig.Terminals.Colors();
			rebuildWidgets();
		}).bounds(ix + inner - half, rowY, half, ROW).build());
	}

	private void toggle(int x, int y, int w, String key, boolean value, Consumer<Boolean> onChange) {
		addRenderableWidget(GolemCycle.onOffBuilder(value)
				.withTooltip(v -> Tooltip.create(Component.translatable(key + ".tooltip")))
				.create(x, y, w, ROW, Component.translatable(key), (b, v) -> onChange.accept(v)));
	}

	private int slider(int x, int y, int w, String key, double min, double max, double step, double def,
			java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set, String format) {
		GolemSlider slider = addRenderableWidget(new GolemSlider(x, y, w, Component.translatable(key), min, max, step, def, get, set,
				v -> String.format(Locale.ROOT, format, v), UiText.ACCENT_2));
		slider.setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
		return y + GolemSlider.HEIGHT + 3;
	}

	// ---- Colours ---------------------------------------------------------------------------------------------

	private int color(int i) {
		GolemConfig.Terminals.Colors c = t().colors;
		return switch (i) {
			case 0 -> c.background;
			case 1 -> c.tile;
			case 2 -> c.solution;
			case 3 -> c.second;
			case 4 -> c.third;
			case 5 -> c.blocked;
			default -> c.text;
		};
	}

	private void setColor(int i, int argb) {
		GolemConfig.Terminals.Colors c = t().colors;
		switch (i) {
			case 0 -> c.background = argb;
			case 1 -> c.tile = argb;
			case 2 -> c.solution = argb;
			case 3 -> c.second = argb;
			case 4 -> c.third = argb;
			case 5 -> c.blocked = argb;
			default -> c.text = argb;
		}
	}

	private void loadHsb() {
		int c = color(colorIndex);
		float[] read = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
		// Keep the hue (and saturation) while the colour is grey or black, so the slider doesn't jump.
		if (read[2] > 0 && read[1] > 0) hsb[0] = read[0];
		if (read[2] > 0) hsb[1] = read[1];
		hsb[2] = read[2];
	}

	private void applyHsb() {
		int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF;
		setColor(colorIndex, (color(colorIndex) & 0xFF000000) | rgb);
		if (hex != null) hex.setValue(hexOf(color(colorIndex)));
	}

	static String hexOf(int argb) {
		return String.format(Locale.ROOT, "#%08X", argb);
	}

	/** "#AARRGGBB", "AARRGGBB", or 6 digits for an opaque colour. Null for anything else. */
	static Integer parseHex(String text) {
		String s = text.strip();
		if (s.startsWith("#")) s = s.substring(1);
		if (!s.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) return null;
		long v = Long.parseLong(s, 16);
		return (int) (s.length() == 6 ? 0xFF000000L | v : v);
	}

	// ---- Preview ---------------------------------------------------------------------------------------------

	private void newDemo() {
		demo = TerminalPuzzle.create(TerminalType.values()[previewType], random, TerminalStacks.pool());
		demoSession = new TerminalSession(-1, demo.title(), Util.getMillis());
	}

	private TerminalLayout previewLayout() {
		return TerminalLayout.of(demo.type(), previewX0, previewY0, previewX1 - previewX0, previewY1 - previewY0, t().scale, t().gap);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (demo != null && event.x() >= previewX0 && event.x() < previewX1 && event.y() >= previewY0 && event.y() < previewY1) {
			int slot = previewLayout().slotAt(event.x(), event.y());
			long now = Util.getMillis();
			Boolean button = TerminalSession.buttonFor(demoSession.view(demo.board(), now), slot);
			if (button != null) {
				demo.click(slot, button);
			} else if (slot >= 0) {
				demoSession.blocked(slot, now);
			}
			if (demo.solved()) newDemo();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		if (warnX >= 0) {
			// A big red exclamation mark beside Auto terms.
			graphics.fill(warnX, warnY, warnX + 15, warnY + ROW, 0xFFB01818);
			graphics.outline(warnX, warnY, 15, ROW, 0xFFFF5555);
			graphics.pose().pushMatrix();
			graphics.pose().translate(warnX + 8, warnY + 3);
			graphics.pose().scale(2f, 2f);
			graphics.centeredText(font, "!", 0, 0, UiText.WHITE);
			graphics.pose().popMatrix();
		}
		if (demo == null) return;
		long now = Util.getMillis();
		demo.advance(now);
		TerminalLayout layout = previewLayout();
		TerminalBoard board = demo.board();
		if (!board.equals(demoBoard)) {
			demoBoard = board;
			demoStacks = new net.minecraft.world.item.ItemStack[board.size()];
			for (int i = 0; i < board.size(); i++) demoStacks[i] = TerminalStacks.stack(board.get(i));
		}
		net.minecraft.world.item.ItemStack[] stacks = demoStacks;
		TerminalSolution view = demoSession.view(board, now);
		int hovered = layout.slotAt(mouseX, mouseY);
		graphics.enableScissor(previewX0, previewY0, previewX1, previewY1);
		graphics.fillGradient(previewX0, previewY0, previewX1, previewY1, 0x40202838, 0x60101018);
		TerminalRenderer.drawCompact(graphics, font, layout, TerminalRenderer.Frame.of(demo.title(), board,
				i -> stacks[i], view, hovered, demoSession.blockedSlot(), now - demoSession.blockedAt(), t()), t().colors);
		graphics.disableScissor();
		graphics.centeredText(font, Component.translatable("golemaddons.terminals.preview.hint"), (previewX0 + previewX1) / 2, previewHintY,
				UiText.DARK_GRAY);
	}

	private void practice() {
		if (GolemAddonsClient.config().save(working.copy())) {
			minecraft.setScreen(null);
			TerminalPractice.start(shuffledTypes());
		}
	}

	private static List<TerminalType> shuffledTypes() {
		List<TerminalType> types = new java.util.ArrayList<>(List.of(TerminalType.values()));
		java.util.Collections.shuffle(types);
		return types;
	}
}
