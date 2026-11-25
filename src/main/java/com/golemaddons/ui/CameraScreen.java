package com.golemaddons.ui;

import com.golemaddons.config.GolemConfig;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Camera customizer: skip the front view, third-person distance, clip through blocks. */
final class CameraScreen extends EditorScreen {
	private static final int ROW = 20;

	private GolemSlider distance;
	private int hintY;

	CameraScreen(Screen parent) {
		super(Component.translatable("golemaddons.camera.title"), parent);
	}

	@Override
	protected void build() {
		GolemConfig.Camera c = working.camera;
		int panelW = maxContentWidth(360);
		int inner = panelW - 2 * PANEL_PAD - 2;
		int px = (width - panelW) / 2;
		int ix = px + PANEL_PAD + 2;

		int mainH = PANEL_LABEL + ROW + PANEL_PAD + 2;
		int viewH = PANEL_LABEL + 2 + 3 * (ROW + 4) + 2 + GolemSlider.HEIGHT + PANEL_PAD;
		int y = centeredTop(mainH + viewH + PANEL_GAP + 14);

		panels.add(new Panel(px, y, panelW, mainH, Component.translatable("golemaddons.camera.panel.main")));
		addRenderableWidget(GolemCycle.onOffBuilder(c.enabled).create(ix, y + PANEL_LABEL + 2, inner, ROW,
				Component.translatable("golemaddons.camera.enabled"), (b, v) -> {
					c.enabled = v;
					rebuildWidgets();
				}));
		y += mainH + PANEL_GAP;

		panels.add(new Panel(px, y, panelW, viewH, Component.translatable("golemaddons.camera.panel.view")));
		int rowY = y + PANEL_LABEL + 2;
		toggle(ix, rowY, inner, "golemaddons.camera.no_front", c.disableFrontView, v -> c.disableFrontView = v);
		rowY += ROW + 4;
		toggle(ix, rowY, inner, "golemaddons.camera.clip", c.clipThroughBlocks, v -> c.clipThroughBlocks = v);
		rowY += ROW + 4;
		toggle(ix, rowY, inner, "golemaddons.camera.custom_distance", c.customDistance, v -> {
			c.customDistance = v;
			distance.active = c.enabled && v;
		});
		rowY += ROW + 6;
		distance = addRenderableWidget(new GolemSlider(ix, rowY, inner, Component.translatable("golemaddons.camera.distance"),
				GolemConfig.Camera.MIN_DISTANCE, GolemConfig.Camera.MAX_DISTANCE, 0.25, GolemConfig.Camera.VANILLA_DISTANCE,
				() -> c.distance, v -> c.distance = (float) v, v -> String.format(Locale.ROOT, "%.2f blocks", v), UiText.ACCENT_2));
		distance.active = c.enabled && c.customDistance;
		hintY = y + viewH + 6;

		addFooter(saveButton(), cancelButton());
	}

	private void toggle(int x, int y, int w, String key, boolean value, java.util.function.Consumer<Boolean> onChange) {
		GolemCycle<Boolean> button = addRenderableWidget(GolemCycle.onOffBuilder(value)
				.withTooltip(v -> Tooltip.create(Component.translatable(key + ".tooltip")))
				.create(x, y, w, ROW, Component.translatable(key), (b, v) -> onChange.accept(v)));
		button.active = working.camera.enabled;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, Component.translatable("golemaddons.camera.hint"), width / 2, hintY, UiText.GRAY);
	}
}
