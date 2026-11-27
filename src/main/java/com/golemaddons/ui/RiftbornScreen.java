package com.golemaddons.ui;

import com.golemaddons.config.GolemConfig;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Riftborn Seraph helpers: anchor outlines (look and search range) and auto attunement. */
final class RiftbornScreen extends EditorScreen {
	private static final int ROW = 20;
	private static int colorIndex;

	private GolemEditBox hex;
	private int hintY;

	RiftbornScreen(Screen parent) {
		super(Component.translatable("golemaddons.riftborn.title"), parent);
	}

	@Override
	protected void build() {
		GolemConfig.Riftborn r = working.riftborn;
		int panelW = maxContentWidth(420);
		int inner = panelW - 2 * PANEL_PAD - 2;
		int half = (inner - GAP * 2) / 2;
		int px = (width - panelW) / 2;
		int ix = px + PANEL_PAD + 2;
		int rx = ix + inner - half;
		int anchorsH = PANEL_LABEL + 2 + (ROW + 4) + (GolemSlider.HEIGHT + 3) + 14 + 6 + ROW + PANEL_PAD;
		int attuneH = PANEL_LABEL + 2 + (ROW + 4) + GolemSlider.HEIGHT + PANEL_PAD;
		int y = centeredTop(anchorsH + PANEL_GAP + attuneH + 12);

		panels.add(new Panel(px, y, panelW, anchorsH, Component.translatable("golemaddons.riftborn.panel.anchors")));
		int rowY = y + PANEL_LABEL + 2;
		toggle(ix, rowY, half, "golemaddons.riftborn.anchors", r.anchors, v -> r.anchors = v);
		toggle(rx, rowY, half, "golemaddons.riftborn.through_walls", r.anchorsThroughWalls, v -> r.anchorsThroughWalls = v);
		rowY += ROW + 4;
		slider(ix, rowY, half, "golemaddons.riftborn.radius", 4, GolemConfig.Riftborn.MAX_RADIUS, 1, 32, () -> r.anchorRadius,
				v -> r.anchorRadius = (int) v, "%.0f blocks");
		rowY = slider(rx, rowY, half, "golemaddons.riftborn.line_width", 1, 10, 0.5, 3, () -> r.anchorLineWidth,
				v -> r.anchorLineWidth = (float) v, "%.1f");
		addRenderableWidget(new ColorSwatchRow(ix, rowY, half, 14, List.of(Component.translatable("golemaddons.riftborn.color.outline"),
				Component.translatable("golemaddons.riftborn.color.fill")), i -> i == 0 ? r.anchorColor : r.anchorFill, colorIndex, i -> {
					colorIndex = i;
					hex.setValue(TerminalsScreen.hexOf(color()));
				}));
		rowY += 14 + 6;
		hex = addRenderableWidget(new GolemEditBox(font, ix, rowY, half, ROW, Component.translatable("golemaddons.terminals.hex")));
		hex.setMaxLength(9);
		hex.setValue(TerminalsScreen.hexOf(color()));
		hex.setTooltip(Tooltip.create(Component.translatable("golemaddons.terminals.hex.tooltip")));
		hex.setResponder(text -> {
			Integer v = TerminalsScreen.parseHex(text);
			if (v == null) return;
			if (colorIndex == 0) r.anchorColor = v;
			else r.anchorFill = v;
		});
		addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.terminals.reset_colors"), b -> {
			GolemConfig.Riftborn d = new GolemConfig.Riftborn();
			r.anchorColor = d.anchorColor;
			r.anchorFill = d.anchorFill;
			hex.setValue(TerminalsScreen.hexOf(color()));
		}).bounds(rx, rowY, half, ROW).build());
		y += anchorsH + PANEL_GAP;

		panels.add(new Panel(px, y, panelW, attuneH, Component.translatable("golemaddons.riftborn.panel.attunement")));
		rowY = y + PANEL_LABEL + 2;
		toggle(ix, rowY, half, "golemaddons.riftborn.auto_attunement", r.autoAttunement, v -> r.autoAttunement = v);
		toggle(rx, rowY, half, "golemaddons.riftborn.switch_dagger", r.switchDagger, v -> r.switchDagger = v);
		rowY += ROW + 4;
		slider(ix, rowY, inner, "golemaddons.riftborn.margin", 0, 300, 10, 60, () -> r.attunementMarginMs, v -> r.attunementMarginMs = (int) v,
				"+%.0f ms");
		hintY = y + attuneH + 4;
		addFooter(saveButton(), cancelButton());
	}

	private int color() {
		return colorIndex == 0 ? working.riftborn.anchorColor : working.riftborn.anchorFill;
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
		graphics.centeredText(font, Component.translatable("golemaddons.riftborn.hint"), width / 2, hintY, UiText.DARK_GRAY);
	}
}
