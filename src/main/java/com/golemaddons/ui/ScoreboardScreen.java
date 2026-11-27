package com.golemaddons.ui;

import com.golemaddons.config.GolemConfig;
import com.golemaddons.scoreboard.ScoreboardElement;
import com.golemaddons.scoreboard.ScoreboardFeature;
import com.golemaddons.scoreboard.ScoreboardRenderer;
import com.golemaddons.scoreboard.ScoreboardTheme;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Custom scoreboard editor: a live preview on the left (the real sidebar when there is one, sample lines when
 * not), and three tabs on the right - which lines and in what order, the background, and the text.
 */
final class ScoreboardScreen extends EditorScreen {
	private static final int ROW = 20;
	private static final int TAB_H = 18;
	private static final String[] COLOR_KEYS = {"primary", "secondary", "accent", "particle", "border"};

	private enum Tab {LINES, BACKGROUND, TEXT}

	private static Tab tab = Tab.LINES;
	private static int colorIndex;
	private static int selectedLine;

	private final long openedAt = Util.getMillis();
	private float tabSlideFrom = tab.ordinal();
	private Panel previewPanel;
	private int previewX0;
	private int previewY0;
	private int previewX1;
	private int previewY1;
	private ScoreboardRenderer.Model previewModel;
	private long previewBuiltAt;
	private boolean previewIsSample;
	private final float[] hsb = new float[3];
	private GolemEditBox hex;
	private GolemEditBox lineText;
	private int codesHintY = -1;
	private int codesHintX;

	ScoreboardScreen(Screen parent) {
		super(Component.translatable("golemaddons.scoreboard.title"), parent);
	}

	private GolemConfig.Scoreboard s() {
		return working.scoreboard;
	}

	@Override
	protected void build() {
		int totalW = maxContentWidth(660);
		int left = (width - totalW) / 2;
		int previewW = Math.round(totalW * 0.4f);
		int rightX = left + previewW + PANEL_GAP;
		int rightW = totalW - previewW - PANEL_GAP;
		int top = HEADER_HEIGHT + 8;
		int bottom = height - FOOTER_HEIGHT - 8;

		// The preview is as tall as the visible pane and stays in view while a long tab scrolls.
		int previewBottom = Math.min(bottom, viewHeight() - FOOTER_HEIGHT - 8);
		previewPanel = new Panel(left, top, previewW, Math.max(60, previewBottom - top), Component.translatable("golemaddons.scoreboard.panel.preview"));
		previewModel = null;

		addRenderableWidget(new GolemSegmented(rightX, top, rightW, TAB_H, Component.translatable("golemaddons.scoreboard.tabs"),
				List.of(Component.translatable("golemaddons.scoreboard.tab.lines"), Component.translatable("golemaddons.scoreboard.tab.background"),
						Component.translatable("golemaddons.scoreboard.tab.text")),
				tab.ordinal(), UiText.ACCENT, openedAt + 60, i -> {
					tab = Tab.values()[i];
					rebuildWidgets();
				}).hideLabel().slideFrom(tabSlideFrom));
		tabSlideFrom = tab.ordinal();
		int y = top + TAB_H + 6;
		codesHintY = -1;
		switch (tab) {
			case LINES -> buildLines(rightX, y, rightW, bottom - y);
			case BACKGROUND -> buildBackground(rightX, y, rightW, bottom - y);
			case TEXT -> buildText(rightX, y, rightW, bottom - y);
		}
		addFooter(saveButton(), cancelButton(), GolemButton.builder(Component.translatable("golemaddons.scoreboard.move"), b -> editPosition())
				.tooltip(Tooltip.create(Component.translatable("golemaddons.scoreboard.move.tooltip"))).accent(UiText.ACCENT_2));
	}

	// ---- Lines -----------------------------------------------------------------------------------------------

	private void buildLines(int x, int y, int w, int h) {
		panels.add(new Panel(x, y, w, h, Component.translatable("golemaddons.scoreboard.panel.lines")));
		GolemConfig.Scoreboard s = s();
		int ix = x + PANEL_PAD + 2;
		int inner = w - 2 * PANEL_PAD - 2;
		int half = (inner - GAP * 2) / 2;
		int rx = ix + inner - half;
		int rowY = y + PANEL_LABEL + 2;
		toggle(ix, rowY, half, "golemaddons.scoreboard.enabled", s.enabled, v -> s.enabled = v);
		toggle(rx, rowY, half, "golemaddons.scoreboard.hide_vanilla", s.hideVanilla, v -> s.hideVanilla = v);
		rowY += ROW + 4;
		int below = ROW + 4 + ROW + 12 + PANEL_PAD;
		int listH = Math.max(ElementList.ROW * 4, (y + h - below) - rowY);
		listH -= listH % ElementList.ROW;
		ElementList list = addRenderableWidget(new ElementList(ix, rowY, inner, listH, s.elements, selectedLine, i -> {
			selectedLine = i;
			syncLineText();
		}));
		list.setTooltip(Tooltip.create(Component.translatable("golemaddons.scoreboard.lines.tooltip")));
		rowY += listH + 4;
		int third = (inner - GAP * 4) / 3;
		addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.scoreboard.add_custom"), b -> add(ScoreboardElement.CUSTOM, "&f"))
				.bounds(ix, rowY, third, ROW).build());
		addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.scoreboard.add_blank"), b -> add(ScoreboardElement.BLANK, ""))
				.bounds(ix + third + GAP * 2, rowY, third, ROW).build());
		addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.scoreboard.remove"), b -> removeSelected())
				.tooltip(Tooltip.create(Component.translatable("golemaddons.scoreboard.remove.tooltip")))
				.bounds(ix + inner - third, rowY, third, ROW).build());
		rowY += ROW + 4;
		lineText = addRenderableWidget(new GolemEditBox(font, ix, rowY, inner, ROW, Component.translatable("golemaddons.scoreboard.line_text")));
		lineText.setMaxLength(GolemConfig.Scoreboard.MAX_TEXT);
		lineText.setTooltip(Tooltip.create(Component.translatable("golemaddons.scoreboard.line_text.tooltip")));
		lineText.setResponder(text -> {
			GolemConfig.Scoreboard.Element e = selectedElement();
			if (e != null && ScoreboardElement.of(e.type) == ScoreboardElement.CUSTOM) e.text = text;
		});
		syncLineText();
		codesHintX = ix;
		codesHintY = rowY + ROW + 2;
	}

	private GolemConfig.Scoreboard.Element selectedElement() {
		List<GolemConfig.Scoreboard.Element> list = s().elements;
		return selectedLine >= 0 && selectedLine < list.size() ? list.get(selectedLine) : null;
	}

	private void syncLineText() {
		if (lineText == null) return;
		GolemConfig.Scoreboard.Element e = selectedElement();
		boolean custom = e != null && ScoreboardElement.of(e.type) == ScoreboardElement.CUSTOM;
		lineText.setEditable(custom);
		lineText.active = custom;
		lineText.setValue(custom ? e.text : "");
		lineText.setHint(Component.translatable(custom ? "golemaddons.scoreboard.line_text.hint" : "golemaddons.scoreboard.line_text.pick"));
	}

	private void add(ScoreboardElement type, String text) {
		List<GolemConfig.Scoreboard.Element> list = s().elements;
		if (list.size() >= GolemConfig.Scoreboard.MAX_ELEMENTS) return;
		int at = selectedLine >= 0 && selectedLine < list.size() ? selectedLine + 1 : list.size();
		list.add(at, new GolemConfig.Scoreboard.Element(type.name(), true, text));
		selectedLine = at;
		rebuildWidgets();
		if (type == ScoreboardElement.CUSTOM && lineText != null) setFocused(lineText);
	}

	private void removeSelected() {
		GolemConfig.Scoreboard.Element e = selectedElement();
		if (e == null) return;
		ScoreboardElement type = ScoreboardElement.of(e.type);
		if (type != null && !type.repeatable) {
			e.enabled = false; // server lines stay in the list (so they can be turned back on), just hidden
		} else {
			s().elements.remove(selectedLine);
			selectedLine = Math.min(selectedLine, s().elements.size() - 1);
		}
		rebuildWidgets();
	}

	// ---- Background ------------------------------------------------------------------------------------------

	private void buildBackground(int x, int y, int w, int h) {
		panels.add(new Panel(x, y, w, h, Component.translatable("golemaddons.scoreboard.panel.background")));
		GolemConfig.Scoreboard s = s();
		int ix = x + PANEL_PAD + 2;
		int inner = w - 2 * PANEL_PAD - 2;
		int half = (inner - GAP * 2) / 2;
		int rx = ix + inner - half;
		int rowY = y + PANEL_LABEL + 2;
		addRenderableWidget(GolemCycle.builder((ScoreboardTheme t) -> Component.translatable(t.key()), ScoreboardTheme.of(s.theme))
				.withValues(List.of(ScoreboardTheme.values()))
				.withTooltip(t -> Tooltip.create(Component.translatable(t.key() + ".tooltip")))
				.create(ix, rowY, half, ROW, Component.translatable("golemaddons.scoreboard.theme"), (b, v) -> {
					s.theme = v.name();
					rebuildWidgets();
				}));
		toggle(rx, rowY, half, "golemaddons.scoreboard.particles", s.particles, v -> s.particles = v);
		rowY += ROW + 5;

		List<Component> names = new ArrayList<>();
		for (String k : COLOR_KEYS) names.add(Component.translatable("golemaddons.scoreboard.color." + k));
		addRenderableWidget(new ColorSwatchRow(ix, rowY, inner, 14, names, this::color, colorIndex, i -> {
			colorIndex = i;
			loadHsb();
			hex.setValue(TerminalsScreen.hexOf(color(colorIndex)));
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
			hex.setValue(TerminalsScreen.hexOf(color(colorIndex)));
		}, "%.0f%%");
		hex = addRenderableWidget(new GolemEditBox(font, ix, rowY, half, ROW, Component.translatable("golemaddons.terminals.hex")));
		hex.setMaxLength(9);
		hex.setValue(TerminalsScreen.hexOf(color(colorIndex)));
		hex.setTooltip(Tooltip.create(Component.translatable("golemaddons.terminals.hex.tooltip")));
		hex.setResponder(text -> {
			Integer parsed = TerminalsScreen.parseHex(text);
			if (parsed != null && parsed != color(colorIndex)) {
				setColor(colorIndex, parsed);
				loadHsb();
			}
		});
		addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.scoreboard.reset_colors"), b -> {
			s.themeColors.remove(ScoreboardTheme.of(s.theme).name());
			s.borderColor = new GolemConfig.Scoreboard().borderColor;
			rebuildWidgets();
		}).bounds(rx, rowY, half, ROW).build());
		rowY += ROW + 6;

		slider(ix, rowY, half, "golemaddons.scoreboard.opacity", 0, 100, 5, 85, () -> s.opacity, v -> s.opacity = (int) v, "%.0f%%");
		rowY = slider(rx, rowY, half, "golemaddons.scoreboard.backing", 0, 80, 5, 20, () -> s.textBacking, v -> s.textBacking = (int) v, "%.0f%%");
		slider(ix, rowY, half, "golemaddons.scoreboard.speed", 0, 300, 10, 100, () -> s.animationSpeed, v -> s.animationSpeed = (int) v, "%.0f%%");
		rowY = slider(rx, rowY, half, "golemaddons.scoreboard.density", 0, 200, 10, 100, () -> s.particleDensity, v -> s.particleDensity = (int) v,
				"%.0f%%");
		slider(ix, rowY, half, "golemaddons.scoreboard.radius", 0, 12, 1, 5, () -> s.cornerRadius, v -> s.cornerRadius = (int) v, "%.0f px");
		rowY = slider(rx, rowY, half, "golemaddons.scoreboard.border_width", 0, 3, 1, 1, () -> s.borderWidth, v -> s.borderWidth = (int) v, "%.0f px");
		addRenderableWidget(GolemCycle.builder((String v) -> Component.translatable("golemaddons.scoreboard.border." + v.toLowerCase(Locale.ROOT)), s.borderStyle)
				.withValues(List.of("THEME", "SOLID", "NONE"))
				.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.scoreboard.border.tooltip")))
				.create(ix, rowY, inner, ROW, Component.translatable("golemaddons.scoreboard.border"), (b, v) -> s.borderStyle = v));
	}

	private int color(int i) {
		GolemConfig.Scoreboard s = s();
		GolemConfig.Scoreboard.ThemeColors c = s.colors();
		return switch (i) {
			case 0 -> c.primary;
			case 1 -> c.secondary;
			case 2 -> c.accent;
			case 3 -> c.particle;
			default -> s.borderColor;
		};
	}

	private void setColor(int i, int argb) {
		GolemConfig.Scoreboard s = s();
		GolemConfig.Scoreboard.ThemeColors c = s.colors();
		switch (i) {
			case 0 -> c.primary = argb;
			case 1 -> c.secondary = argb;
			case 2 -> c.accent = argb;
			case 3 -> c.particle = argb;
			default -> s.borderColor = argb;
		}
	}

	private void loadHsb() {
		int c = color(colorIndex);
		float[] read = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
		if (read[2] > 0 && read[1] > 0) hsb[0] = read[0];
		if (read[2] > 0) hsb[1] = read[1];
		hsb[2] = read[2];
	}

	private void applyHsb() {
		int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF;
		setColor(colorIndex, (color(colorIndex) & 0xFF000000) | rgb);
		if (hex != null) hex.setValue(TerminalsScreen.hexOf(color(colorIndex)));
	}

	// ---- Text ------------------------------------------------------------------------------------------------

	private void buildText(int x, int y, int w, int h) {
		panels.add(new Panel(x, y, w, h, Component.translatable("golemaddons.scoreboard.panel.text")));
		GolemConfig.Scoreboard s = s();
		int ix = x + PANEL_PAD + 2;
		int inner = w - 2 * PANEL_PAD - 2;
		int half = (inner - GAP * 2) / 2;
		int rx = ix + inner - half;
		int rowY = y + PANEL_LABEL + 2;
		GolemEditBox header = addRenderableWidget(new GolemEditBox(font, ix, rowY, inner, ROW, Component.translatable("golemaddons.scoreboard.header")));
		header.setMaxLength(GolemConfig.Scoreboard.MAX_TEXT);
		header.setValue(s.header);
		header.setHint(Component.translatable("golemaddons.scoreboard.header.hint"));
		header.setTooltip(Tooltip.create(Component.translatable("golemaddons.scoreboard.header.tooltip")));
		header.setResponder(v -> s.header = v);
		rowY += ROW + 4;
		GolemEditBox footer = addRenderableWidget(new GolemEditBox(font, ix, rowY, inner, ROW, Component.translatable("golemaddons.scoreboard.footer")));
		footer.setMaxLength(GolemConfig.Scoreboard.MAX_TEXT);
		footer.setValue(s.footer);
		footer.setHint(Component.translatable("golemaddons.scoreboard.footer.hint"));
		footer.setTooltip(Tooltip.create(Component.translatable("golemaddons.scoreboard.footer.tooltip")));
		footer.setResponder(v -> s.footer = v);
		rowY += ROW + 2;
		codesHintX = ix;
		codesHintY = rowY;
		rowY += 12;
		addRenderableWidget(GolemCycle.builder((String v) -> Component.translatable("golemaddons.scoreboard.align." + v.toLowerCase(Locale.ROOT)), s.align)
				.withValues(List.of("LEFT", "CENTER", "RIGHT"))
				.create(ix, rowY, half, ROW, Component.translatable("golemaddons.scoreboard.align"), (b, v) -> s.align = v));
		toggle(rx, rowY, half, "golemaddons.scoreboard.shadow", s.textShadow, v -> s.textShadow = v);
		rowY += ROW + 6;
		slider(ix, rowY, half, "golemaddons.scoreboard.padding", 1, 12, 1, 5, () -> s.padding, v -> s.padding = (int) v, "%.0f px");
		rowY = slider(rx, rowY, half, "golemaddons.scoreboard.spacing", 0, 6, 1, 1, () -> s.lineSpacing, v -> s.lineSpacing = (int) v, "%.0f px");
		slider(ix, rowY, half, "golemaddons.scoreboard.scale", GolemConfig.HudPosition.MIN_SCALE, GolemConfig.HudPosition.MAX_SCALE, 0.05, 1,
				() -> s.hud.scale, v -> s.hud.scale = (float) v, "%.2fx");
	}

	// ---- Shared ----------------------------------------------------------------------------------------------

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

	private void editPosition() {
		minecraft.setScreen(new HudEditScreen(self(), Component.translatable("golemaddons.scoreboard.move"), s().hud,
				new GolemConfig.Scoreboard().hud, f -> ScoreboardRenderer.size(f, model(), s()),
				(g, x, y, scale) -> ScoreboardRenderer.draw(g, font, x, y, scale, model(), s(), ScoreboardFeature.seconds())));
	}

	// ---- Preview ---------------------------------------------------------------------------------------------

	/** The board with the settings being edited: built from the live sidebar, or from sample lines. */
	private ScoreboardRenderer.Model model() {
		long now = Util.getMillis();
		if (previewModel != null && now - previewBuiltAt < 100) return previewModel;
		previewBuiltAt = now;
		ScoreboardRenderer.Model m = null;
		try {
			m = minecraft.level == null ? null : ScoreboardFeature.build(minecraft, s());
		} catch (RuntimeException ignored) {
			// fall back to the sample
		}
		previewIsSample = m == null;
		if (m == null) m = sample();
		previewModel = m;
		return m;
	}

	private ScoreboardRenderer.Model sample() {
		String[] raw = {"§fName: §dqtTornado", "§fPurse: §64,249,512,747", "§fGems: §a675", "§fCarrot Buff: §61d 3h",
				"§fPet: §7[Lvl 100] §6Ender Dragon", "§fBooster: §9Whale Booster", "", "§7⏣ §eDragon's Den", "", "§dRiftborn Seraph",
				"§fXP: §d0§7/§d13,750", "", "§ddragsim.co"};
		List<String> plain = new ArrayList<>();
		List<Component> lines = new ArrayList<>();
		for (String r : raw) {
			lines.add(Component.literal(r));
			plain.add(com.golemaddons.boss.Sidebar.clean(r));
		}
		return ScoreboardFeature.build(minecraft, s(), Component.literal("§d§lDragSim"), plain, lines);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		if (codesHintY >= 0) {
			String hint = Component.translatable(tab == Tab.LINES ? "golemaddons.scoreboard.placeholders" : "golemaddons.scoreboard.codes").getString();
			int room = Math.round((width - codesHintX - PANEL_PAD - 8) / 0.8f);
			if (font.width(hint) > room) hint = font.plainSubstrByWidth(hint, room - font.width("…")) + "…";
			graphics.pose().pushMatrix();
			graphics.pose().translate(codesHintX, codesHintY + 1);
			graphics.pose().scale(0.8f, 0.8f);
			graphics.text(font, hint, 0, 0, UiText.DARK_GRAY);
			graphics.pose().popMatrix();
		}
		Panel p = previewPanel;
		int dy = Math.max(0, Math.min(scrollOffset(), height - FOOTER_HEIGHT - 8 - (p.y() + p.h())));
		drawPanel(graphics, new Panel(p.x(), p.y() + dy, p.w(), p.h(), p.label()), appear());
		previewX0 = p.x() + 4;
		previewY0 = p.y() + dy + PANEL_LABEL + 2;
		previewX1 = p.x() + p.w() - 4;
		previewY1 = p.y() + dy + p.h() - 14;
		ScoreboardRenderer.Model m;
		try {
			m = model();
		} catch (RuntimeException e) {
			return;
		}
		int[] size = ScoreboardRenderer.size(font, m, s());
		float fit = Math.min((previewX1 - previewX0) / (float) size[0], (previewY1 - previewY0) / (float) size[1]);
		float scale = Math.max(0.3f, Math.min(s().hud.scale, fit));
		int w = Math.round(size[0] * scale);
		int h = Math.round(size[1] * scale);
		int x = (previewX0 + previewX1 - w) / 2;
		int y = (previewY0 + previewY1 - h) / 2;
		graphics.enableScissor(previewX0, previewY0, previewX1, previewY1);
		// A dim "world" behind it, so opacity and colours read as they will in game.
		graphics.fillGradient(previewX0, previewY0, previewX1, previewY1, 0x5045607A, 0x50303A28);
		ScoreboardRenderer.draw(graphics, font, x, y, scale, m, s(), ScoreboardFeature.seconds());
		graphics.disableScissor();
		Component note = Component.translatable(previewIsSample ? "golemaddons.scoreboard.preview.sample" : "golemaddons.scoreboard.preview.live");
		graphics.centeredText(font, note, (previewX0 + previewX1) / 2, previewY1 + 3, UiText.DARK_GRAY);
	}
}
