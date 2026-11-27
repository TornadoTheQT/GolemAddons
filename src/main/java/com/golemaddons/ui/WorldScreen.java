package com.golemaddons.ui;

import com.golemaddons.config.GolemConfig;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** World visuals: debuff screen effects, the Rift's purple haze and dragon hitboxes. All render-only. */
final class WorldScreen extends EditorScreen {
	private static final int ROW = 20;
	private int hintY;

	WorldScreen(Screen parent) {
		super(Component.translatable("golemaddons.world.title"), parent);
	}

	@Override
	protected void build() {
		GolemConfig.Render r = working.render;
		int panelW = maxContentWidth(420);
		int inner = panelW - 2 * PANEL_PAD - 2;
		int half = (inner - GAP * 2) / 2;
		int px = (width - panelW) / 2;
		int ix = px + PANEL_PAD + 2;
		int rx = ix + inner - half;
		int hazeH = PANEL_LABEL + 2 + 2 * (ROW + 4) + PANEL_PAD;
		int hitH = PANEL_LABEL + 2 + ROW + PANEL_PAD;
		int debuffH = PANEL_LABEL + 2 + 4 * (ROW + 4) + PANEL_PAD;
		int y = centeredTop(debuffH + PANEL_GAP + hazeH + PANEL_GAP + hitH + 12);

		panels.add(new Panel(px, y, panelW, debuffH, Component.translatable("golemaddons.world.panel.debuffs")));
		int dy = y + PANEL_LABEL + 2;
		toggle(ix, dy, inner, "golemaddons.world.debuffs", r.disableDebuffs, v -> r.disableDebuffs = v);
		dy += ROW + 4;
		toggle(ix, dy, half, "golemaddons.world.debuff.blindness", r.noBlindness, v -> r.noBlindness = v);
		toggle(rx, dy, half, "golemaddons.world.debuff.darkness", r.noDarkness, v -> r.noDarkness = v);
		dy += ROW + 4;
		toggle(ix, dy, half, "golemaddons.world.debuff.nausea", r.noNausea, v -> r.noNausea = v);
		toggle(rx, dy, half, "golemaddons.world.debuff.pumpkin", r.noPumpkin, v -> r.noPumpkin = v);
		dy += ROW + 4;
		toggle(ix, dy, half, "golemaddons.world.debuff.freezing", r.noFreezing, v -> r.noFreezing = v);
		toggle(rx, dy, half, "golemaddons.world.debuff.fire", r.noFireOverlay, v -> r.noFireOverlay = v);
		y += debuffH + PANEL_GAP;

		panels.add(new Panel(px, y, panelW, hazeH, Component.translatable("golemaddons.world.panel.haze")));
		int rowY = y + PANEL_LABEL + 2;
		toggle(ix, rowY, half, "golemaddons.world.clear_haze", r.clearRiftHaze, v -> r.clearRiftHaze = v);
		toggle(rx, rowY, half, "golemaddons.world.normal_sky", r.riftNormalSky, v -> r.riftNormalSky = v);
		rowY += ROW + 4;
		toggle(ix, rowY, inner, "golemaddons.world.everywhere", r.clearHazeEverywhere, v -> r.clearHazeEverywhere = v);
		y += hazeH + PANEL_GAP;

		panels.add(new Panel(px, y, panelW, hitH, Component.translatable("golemaddons.world.panel.dragons")));
		toggle(ix, y + PANEL_LABEL + 2, inner, "golemaddons.world.hitboxes", r.dragonHitboxes, v -> r.dragonHitboxes = v);
		hintY = y + hitH + 4;
		addFooter(saveButton(), cancelButton());
	}

	private void toggle(int x, int y, int w, String key, boolean value, Consumer<Boolean> onChange) {
		addRenderableWidget(GolemCycle.onOffBuilder(value)
				.withTooltip(v -> Tooltip.create(Component.translatable(key + ".tooltip")))
				.create(x, y, w, ROW, Component.translatable(key), (b, v) -> onChange.accept(v)));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, Component.translatable("golemaddons.world.hint"), width / 2, hintY, UiText.DARK_GRAY);
	}
}
