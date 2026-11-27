package com.golemaddons.ui;

import com.golemaddons.config.GolemConfig;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Slayer chat cleaner: which groups of repeated boss messages to hide, plus the player's own filters. */
final class ChatCleanerScreen extends EditorScreen {
	private static final int ROW = 20;
	private int hintY;

	ChatCleanerScreen(Screen parent) {
		super(Component.translatable("golemaddons.chatcleaner.title"), parent);
	}

	@Override
	protected void build() {
		GolemConfig.ChatCleaner c = working.chatCleaner;
		int panelW = maxContentWidth(420);
		int inner = panelW - 2 * PANEL_PAD - 2;
		int half = (inner - GAP * 2) / 2;
		int px = (width - panelW) / 2;
		int ix = px + PANEL_PAD + 2;
		int rx = ix + inner - half;
		int groupsH = PANEL_LABEL + 2 + 4 * (ROW + 4) + PANEL_PAD;
		int customH = PANEL_LABEL + 2 + ROW + 14 + PANEL_PAD;
		int y = centeredTop(groupsH + PANEL_GAP + customH + 12);

		panels.add(new Panel(px, y, panelW, groupsH, Component.translatable("golemaddons.chatcleaner.panel.groups")));
		int rowY = y + PANEL_LABEL + 2;
		toggle(ix, rowY, inner, "golemaddons.chatcleaner.enabled", c.enabled, v -> c.enabled = v);
		rowY += ROW + 4;
		toggle(ix, rowY, half, "golemaddons.chatcleaner.hitshield", c.hitshield, v -> c.hitshield = v);
		toggle(rx, rowY, half, "golemaddons.chatcleaner.mechanics", c.mechanics, v -> c.mechanics = v);
		rowY += ROW + 4;
		toggle(ix, rowY, half, "golemaddons.chatcleaner.split", c.split, v -> c.split = v);
		toggle(rx, rowY, half, "golemaddons.chatcleaner.anchors", c.anchors, v -> c.anchors = v);
		rowY += ROW + 4;
		toggle(ix, rowY, half, "golemaddons.chatcleaner.soulcry", c.soulcry, v -> c.soulcry = v);
		y += groupsH + PANEL_GAP;

		panels.add(new Panel(px, y, panelW, customH, Component.translatable("golemaddons.chatcleaner.panel.custom")));
		GolemEditBox box = addRenderableWidget(new GolemEditBox(font, ix, y + PANEL_LABEL + 2, inner, ROW,
				Component.translatable("golemaddons.chatcleaner.custom")));
		box.setMaxLength(512);
		box.setValue(c.customFilters);
		box.setHint(Component.translatable("golemaddons.chatcleaner.custom.hint"));
		box.setTooltip(Tooltip.create(Component.translatable("golemaddons.chatcleaner.custom.tooltip")));
		box.setResponder(v -> c.customFilters = v);
		hintY = y + PANEL_LABEL + 2 + ROW + 3;
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
		graphics.centeredText(font, Component.translatable("golemaddons.chatcleaner.hint"), width / 2, hintY, UiText.DARK_GRAY);
	}
}
