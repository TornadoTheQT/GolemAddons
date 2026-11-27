package com.golemaddons.ui;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.config.GolemConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base for the settings sub-screens. Edits a copy of the config; Save writes it, Cancel or Escape throws it away.
 *
 * <p>Gives every sub-screen the same look: a header bar, content grouped into labelled panels that are centered
 * on screen, and a footer of buttons sized to their labels.
 *
 * <p>When its parent is a {@link SectionScreen}, the screen is shown inside that screen's content pane instead:
 * it shares the section's working copy (so every page of a section saves together), draws no header of its own,
 * scrolls when it is taller than the pane, and keeps its footer buttons pinned to the bottom.
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
	/** The section this page is embedded in, or null when it is a screen of its own. */
	final SectionScreen host;
	private final List<AbstractWidget> footer = new ArrayList<>();
	private boolean saveFailed;
	/** Panels fade and slide in once when the screen opens (not again on rebuilds). */
	private long openedAt = net.minecraft.util.Util.getMillis();

	protected EditorScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
		this.host = parent instanceof SectionScreen s ? s : null;
		this.working = host != null ? host.working() : GolemAddonsClient.config().get().copy();
	}

	@Override
	protected final void init() {
		panels.clear();
		footer.clear();
		build();
		if (host != null) host.childBuilt(this);
	}

	/** Add widgets and panels. Called on every init (open, resize, rebuild). */
	protected abstract void build();

	boolean embedded() {
		return host != null;
	}

	/** The screen to come back to from a dialog opened here: the section when embedded, else this screen. */
	protected Screen self() {
		return host != null ? host : this;
	}

	/** Where this page's (0, 0) is on the real screen. Pictures in picture (3D previews) are placed in real coordinates. */
	protected int screenOffsetX() {
		return host == null ? 0 : host.childOffsetX();
	}

	protected int screenOffsetY() {
		return host == null ? 0 : host.childOffsetY();
	}

	/** Height of what is visible: the section's pane when embedded (the page itself may be taller and scroll). */
	protected int viewHeight() {
		return host != null ? host.paneHeight() : height;
	}

	/** How far the page is scrolled (0 when not embedded). */
	protected int scrollOffset() {
		return host != null ? host.scrollPx() : 0;
	}

	/** Replays the panels' entrance (the section does this when switching to the page). */
	void replayIntro() {
		openedAt = net.minecraft.util.Util.getMillis();
	}

	/** Lowest point of the content (panels and widgets, not the footer). */
	int contentBottom() {
		int bottom = 0;
		for (Panel p : panels) bottom = Math.max(bottom, p.y() + p.h());
		for (var child : children()) {
			if (child instanceof AbstractWidget w && !footer.contains(w)) bottom = Math.max(bottom, w.getY() + w.getHeight());
		}
		return bottom;
	}

	/** Lays the page out again at a different height (the section makes long pages taller and scrolls them). */
	void relayout(int newHeight) {
		this.height = newHeight;
		rebuildWidgets();
	}

	/** Keeps the footer buttons at the bottom of the section's pane, wherever the page is scrolled to. */
	void pinFooter(int paneHeight, int scroll) {
		for (AbstractWidget w : footer) w.setY(paneHeight - 27 + scroll);
	}

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
		var buttons = new AbstractWidget[builders.length];
		for (int i = 0; i < builders.length; i++) buttons[i] = builders[i].build();
		addFooter(buttons);
	}

	/** Same, for buttons (or toggles) a screen keeps a reference to. */
	protected void addFooter(AbstractWidget... buttons) {
		int total = 0;
		for (var button : buttons) {
			button.setWidth(buttonWidth(button.getMessage(), 90));
			total += button.getWidth();
		}
		total += GAP * 2 * (buttons.length - 1);
		int x = (width - total) / 2;
		for (var button : buttons) {
			button.setPosition(x, height - 27);
			if (host != null) {
				// Drawn after the footer strip, on top of scrolled content.
				addWidget(button);
				footer.add(button);
			} else {
				addRenderableWidget(button);
			}
			x += button.getWidth() + GAP * 2;
		}
	}

	protected GolemButton.Builder saveButton() {
		return GolemButton.builder(Component.translatable("golemaddons.config.save"), b -> save());
	}

	protected GolemButton.Builder cancelButton() {
		return GolemButton.builder(Component.translatable(host != null ? "golemaddons.config.revert" : "golemaddons.config.cancel"), b -> cancel());
	}

	protected void save() {
		if (host != null) {
			saveFailed = !host.saveAll();
			return;
		}
		if (GolemAddonsClient.config().save(working.copy())) {
			minecraft.setScreen(parent);
		} else {
			saveFailed = true;
		}
	}

	/** Cancel: a screen of its own closes without saving; a section page puts the whole section back as saved. */
	protected void cancel() {
		if (host != null) host.revert();
		else onClose();
	}

	@Override
	public void onClose() {
		if (host != null) host.onClose();
		else minecraft.setScreen(parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (host == null) {
			super.extractBackground(graphics, mouseX, mouseY, delta);
			graphics.fillGradient(0, 0, width, HEADER_HEIGHT, 0xD0120E22, 0xB0000000);
			graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, UiText.withAlpha(UiText.ACCENT, 0.8f));
			graphics.fill(0, HEADER_HEIGHT - 1, width / 3, HEADER_HEIGHT, UiText.withAlpha(UiText.ACCENT_2, 0.6f));
			graphics.text(font, Component.literal("GolemAddons").withColor(UiText.ACCENT & 0xFFFFFF), 8, 8, UiText.WHITE);
			graphics.centeredText(font, title, width / 2, 8, UiText.WHITE);
			graphics.fill(0, height - FOOTER_HEIGHT, width, height, 0x70000000);
		}
		for (int i = 0; i < panels.size(); i++) drawPanel(graphics, panels.get(i), UiAnim.appear(openedAt + 50L * i));
	}

	/** Draws one panel box (also for panels a screen draws itself, like a preview that stays in view). */
	protected void drawPanel(GuiGraphicsExtractor graphics, Panel panel, float appear) {
		int x = panel.x();
		int y = panel.y() + Math.round((1f - appear) * 6f);
		graphics.fillGradient(x, y, x + panel.w(), y + panel.h(), UiText.withAlpha(0xB0161226, appear), UiText.withAlpha(0xA0080710, appear));
		graphics.outline(x, y, panel.w(), panel.h(), UiText.withAlpha(0x38FFFFFF, appear));
		graphics.fill(x, y, x + 2, y + panel.h(), UiText.withAlpha(UiText.ACCENT, 0.8f * appear));
		graphics.fill(x + 2, y, x + Math.round(panel.w() * 0.35f * appear), y + 1, UiText.withAlpha(UiText.ACCENT, 0.5f * appear));
		graphics.text(font, panel.label(), x + PANEL_PAD + 2, y + 4, UiText.withAlpha(UiText.WHITE, appear));
	}

	/** Entrance progress of the page's panels (0..1). */
	protected float appear() {
		return UiAnim.appear(openedAt);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		if (host != null && !footer.isEmpty()) {
			int top = host.paneHeight() - FOOTER_HEIGHT + host.scrollPx();
			graphics.fillGradient(0, top, width, top + FOOTER_HEIGHT, 0xC0080710, 0xE0080710);
			graphics.fill(0, top, width, top + 1, 0x30FFFFFF);
			for (AbstractWidget w : footer) w.extractRenderState(graphics, mouseX, mouseY, delta);
		}
		if (saveFailed) {
			int y = host != null ? host.paneHeight() - FOOTER_HEIGHT - 12 + host.scrollPx() : height - FOOTER_HEIGHT - 12;
			graphics.centeredText(font, Component.translatable("golemaddons.config.save_failed"), width / 2, y, UiText.RED);
		}
	}
}
