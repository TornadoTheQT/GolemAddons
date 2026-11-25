package com.golemaddons.ui;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.config.GolemConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base for the settings sub-screens. Edits a copy of the config; Save writes it, Cancel or Escape throws it away.
 *
 * <p>Gives every sub-screen the same look: a header bar, content grouped into labelled panels that are centered
 * on screen, and a footer of buttons sized to their labels.
 */
abstract class EditorScreen extends Screen {
	static final int HEADER_HEIGHT = 24;
	static final int FOOTER_HEIGHT = 34;
	static final int PANEL_PAD = 6;
	static final int PANEL_LABEL = 12;
	static final int PANEL_GAP = 8;
	static final int GAP = 2;

	/** A labelled box drawn behind a group of widgets. */
	record Panel(int x, int y, int w, int h, Component label) {
	}

	protected final Screen parent;
	protected final GolemConfig working;
	protected final List<Panel> panels = new ArrayList<>();
	private boolean saveFailed;
	/** Panels fade and slide in once when the screen opens (not again on rebuilds). */
	private final long openedAt = net.minecraft.util.Util.getMillis();

	protected EditorScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
		this.working = GolemAddonsClient.config().get().copy();
	}

	@Override
	protected final void init() {
		panels.clear();
		build();
	}

	/** Add widgets and panels. Called on every init (open, resize, rebuild). */
	protected abstract void build();

	/** Widest content a screen should use: leaves a margin and caps line length on huge screens. */
	protected int maxContentWidth(int preferred) {
		return Math.max(120, Math.min(width - 24, preferred));
	}

	/** Top y that vertically centers a block of the given height between header and footer. */
	protected int centeredTop(int blockHeight) {
		int available = height - HEADER_HEIGHT - FOOTER_HEIGHT;
		return HEADER_HEIGHT + Math.max(6, (available - blockHeight) / 2);
	}

	/** Width a button needs to show its label without scrolling. */
	protected int buttonWidth(Component label, int min) {
		return Math.max(min, font.width(label) + 16);
	}

	/** One row of buttons along the bottom, each sized to its label and the row centered. */
	protected void addFooter(GolemButton.Builder... builders) {
		var buttons = new net.minecraft.client.gui.components.AbstractWidget[builders.length];
		for (int i = 0; i < builders.length; i++) buttons[i] = builders[i].build();
		addFooter(buttons);
	}

	/** Same, for buttons (or toggles) a screen keeps a reference to. */
	protected void addFooter(net.minecraft.client.gui.components.AbstractWidget... buttons) {
		int total = 0;
		for (var button : buttons) {
			button.setWidth(buttonWidth(button.getMessage(), 90));
			total += button.getWidth();
		}
		total += GAP * 2 * (buttons.length - 1);
		int x = (width - total) / 2;
		for (var button : buttons) {
			button.setPosition(x, height - 27);
			addRenderableWidget(button);
			x += button.getWidth() + GAP * 2;
		}
	}

	protected GolemButton.Builder saveButton() {
		return GolemButton.builder(Component.translatable("golemaddons.config.save"), b -> save());
	}

	protected GolemButton.Builder cancelButton() {
		return GolemButton.builder(Component.translatable("golemaddons.config.cancel"), b -> onClose());
	}

	protected void save() {
		if (GolemAddonsClient.config().save(working.copy())) {
			minecraft.setScreen(parent);
		} else {
			saveFailed = true;
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		graphics.fillGradient(0, 0, width, HEADER_HEIGHT, 0xD0120E22, 0xB0000000);
		graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, UiText.withAlpha(UiText.ACCENT, 0.8f));
		graphics.fill(0, HEADER_HEIGHT - 1, width / 3, HEADER_HEIGHT, UiText.withAlpha(UiText.ACCENT_2, 0.6f));
		graphics.text(font, Component.literal("GolemAddons").withColor(UiText.ACCENT & 0xFFFFFF), 8, 8, UiText.WHITE);
		graphics.centeredText(font, title, width / 2, 8, UiText.WHITE);
		graphics.fill(0, height - FOOTER_HEIGHT, width, height, 0x70000000);
		for (int i = 0; i < panels.size(); i++) {
			Panel panel = panels.get(i);
			float appear = UiAnim.appear(openedAt + 50L * i);
			int x = panel.x();
			int y = panel.y() + Math.round((1f - appear) * 6f);
			graphics.fillGradient(x, y, x + panel.w(), y + panel.h(), UiText.withAlpha(0xB0161226, appear), UiText.withAlpha(0xA0080710, appear));
			graphics.outline(x, y, panel.w(), panel.h(), UiText.withAlpha(0x38FFFFFF, appear));
			graphics.fill(x, y, x + 2, y + panel.h(), UiText.withAlpha(UiText.ACCENT, 0.8f * appear));
			graphics.fill(x + 2, y, x + Math.round(panel.w() * 0.35f * appear), y + 1, UiText.withAlpha(UiText.ACCENT, 0.5f * appear));
			graphics.text(font, panel.label(), x + PANEL_PAD + 2, y + 4, UiText.withAlpha(UiText.WHITE, appear));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		if (saveFailed) {
			graphics.centeredText(font, Component.translatable("golemaddons.config.save_failed"), width / 2,
					height - FOOTER_HEIGHT - 12, UiText.RED);
		}
	}
}
