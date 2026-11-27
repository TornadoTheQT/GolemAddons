package com.golemaddons.ui;

import com.golemaddons.combat.BoneSwap;
import com.golemaddons.config.GolemConfig;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Bone swap: the key, which items count, the timing, and where it ends. */
final class BoneSwapScreen extends EditorScreen {
	private static final int ROW = 20;

	private GolemButton keyButton;
	private boolean listening;
	private int hintY;

	BoneSwapScreen(Screen parent) {
		super(Component.translatable("golemaddons.boneswap.title"), parent);
	}

	@Override
	protected void build() {
		GolemConfig.BoneSwap b = working.boneSwap;
		int panelW = maxContentWidth(400);
		int inner = panelW - 2 * PANEL_PAD - 2;
		int half = (inner - GAP * 2) / 2;
		int px = (width - panelW) / 2;
		int ix = px + PANEL_PAD + 2;
		int rx = ix + inner - half;
		int h = PANEL_LABEL + 2 + (ROW + 4) * 2 + (GolemSlider.HEIGHT + 3) + ROW + PANEL_PAD;
		int y = centeredTop(h + 14);
		panels.add(new Panel(px, y, panelW, h, Component.translatable("golemaddons.boneswap.panel")));
		int rowY = y + PANEL_LABEL + 2;
		addRenderableWidget(GolemCycle.onOffBuilder(b.enabled).create(ix, rowY, half, ROW, Component.translatable("golemaddons.boneswap.enabled"),
				(w, v) -> b.enabled = v));
		keyButton = addRenderableWidget(GolemButton.builder(Component.empty(), w -> {
			listening = !listening;
			updateKey();
		}).tooltip(Tooltip.create(Component.translatable("golemaddons.boneswap.key.tooltip"))).bounds(rx, rowY, half, ROW).build());
		updateKey();
		rowY += ROW + 4;
		GolemEditBox name = addRenderableWidget(new GolemEditBox(font, ix, rowY, half, ROW, Component.translatable("golemaddons.boneswap.item")));
		name.setMaxLength(48);
		name.setValue(b.itemName);
		name.setTooltip(Tooltip.create(Component.translatable("golemaddons.boneswap.item.tooltip")));
		name.setResponder(v -> b.itemName = v);
		List<Integer> ends = new ArrayList<>();
		for (int i = 0; i <= 9; i++) ends.add(i);
		addRenderableWidget(GolemCycle.builder((Integer i) -> i == 0 ? Component.translatable("golemaddons.boneswap.end.back")
				: Component.translatable("golemaddons.boneswap.end.slot", i), b.endSlot).withValues(ends)
				.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.boneswap.end.tooltip")))
				.create(rx, rowY, half, ROW, Component.translatable("golemaddons.boneswap.end"), (w, v) -> b.endSlot = v));
		rowY += ROW + 4;
		slider(ix, rowY, half, "golemaddons.boneswap.delay", 0, 500, 5, 60, () -> b.delayMs, v -> b.delayMs = (int) v, "%.0f ms");
		slider(rx, rowY, half, "golemaddons.boneswap.variation", 0, 200, 5, 20, () -> b.variationMs, v -> b.variationMs = (int) v, "±%.0f ms");
		hintY = y + h + 4;
		addFooter(saveButton(), cancelButton());
	}

	private void updateKey() {
		KeyMapping key = BoneSwap.instance().key();
		keyButton.setMessage(listening ? Component.translatable("golemaddons.quick.key.listening").withColor(UiText.YELLOW & 0xFFFFFF)
				: Component.translatable("golemaddons.quick.key", key.getTranslatedKeyMessage()));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening) {
			KeyMapping key = BoneSwap.instance().key();
			key.setKey(event.key() == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
			KeyMapping.resetMapping();
			minecraft.options.save();
			listening = false;
			updateKey();
			return true;
		}
		return super.keyPressed(event);
	}

	private void slider(int x, int y, int w, String key, double min, double max, double step, double def,
			java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set, String format) {
		GolemSlider s = addRenderableWidget(new GolemSlider(x, y, w, Component.translatable(key), min, max, step, def, get, set,
				v -> String.format(Locale.ROOT, format, v), UiText.ACCENT_2));
		s.setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, Component.translatable("golemaddons.boneswap.hint"), width / 2, hintY, UiText.DARK_GRAY);
	}
}
