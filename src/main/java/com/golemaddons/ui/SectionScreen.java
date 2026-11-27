package com.golemaddons.ui;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.mixin.GuiGraphicsAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * One section of the menu (Selling, QoL, Combat, Visuals): its pages listed down the left, the chosen page's
 * settings on the right. Switching pages is one click, the pane scrolls smoothly when a page is taller than the
 * window, and every page edits one shared copy of the config, saved when the section closes (or with Save).
 *
 * <p>Pages are ordinary {@link EditorScreen}s laid out inside the pane: this screen moves the drawing and the
 * mouse into the pane's coordinates, so pages don't need to know where they are shown.
 */
final class SectionScreen extends Screen implements PaneHost {
	private static final int HEADER = EditorScreen.HEADER_HEIGHT;
	private static final int TAB_H = 30;
	private static final float SCROLL_STEP = 32f;
	/** Last page opened in each section, so coming back lands where you were. */
	private static final Map<String, Integer> LAST_PAGE = new HashMap<>();

	/** One page. {@code open} builds it with this screen as its parent. */
	record Tile(String key, ItemStack icon, int accent, Supplier<HubCard.Status> status, Function<Screen, Screen> open) {
	}

	private final Screen parent;
	private final String id;
	private final int accent;
	private final List<Tile> tiles;
	private final EditorScreen[] pages;
	private final HubBackdrop backdrop = new HubBackdrop();
	private final long openedAt = Util.getMillis();
	private GolemConfig working = GolemAddonsClient.config().get().copy();
	private int selected = -1;
	private EditorScreen page;
	private int sidebarW;
	/** Narrow windows: the sidebar shows icons only, leaving the room to the page. */
	private boolean compact;
	private boolean relayouting;
	private float scroll;
	private float scrollTarget;
	private int maxScroll;
	private long switchedAt;
	private float indicatorY = -1;
	private final long[] lastFrame = new long[1];
	private long savedAt;
	private boolean draggingBar;

	SectionScreen(Screen parent, String id, Component title, int accent, List<Tile> tiles) {
		super(title);
		this.parent = parent;
		this.id = id;
		this.accent = accent;
		this.tiles = tiles;
		this.pages = new EditorScreen[tiles.size()];
	}

	@Override
	public Screen page() {
		return page;
	}

	@Override
	public int pageOffsetX() {
		return childOffsetX();
	}

	@Override
	public int pageOffsetY() {
		return childOffsetY();
	}

	GolemConfig working() {
		return working;
	}

	int paneHeight() {
		return height;
	}

	int scrollPx() {
		return Math.round(scroll);
	}

	int childOffsetX() {
		return sidebarW + slide();
	}

	int childOffsetY() {
		return -scrollPx();
	}

	private int tabsTop() {
		return compact ? HEADER + 6 : HEADER + 22;
	}

	/** The page slides in a few pixels when it is switched to. */
	private int slide() {
		return Math.round((1f - UiAnim.easeOutCubic((Util.getMillis() - switchedAt) / 240f)) * 14f);
	}

	@Override
	protected void init() {
		compact = width < 560;
		sidebarW = compact ? 32 : Math.clamp(Math.round(width * 0.22f), 112, 160);
		int y = tabsTop();
		for (int i = 0; i < tiles.size(); i++) {
			PageTab tab = addRenderableWidget(new PageTab(i, 4, y, sidebarW - 8, TAB_H - 2));
			if (compact) tab.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(tiles.get(i).key())));
			y += TAB_H;
		}
		addRenderableWidget(GolemButton.builder(Component.translatable(compact ? "golemaddons.section.back_short" : "golemaddons.section.back"),
				b -> onClose()).bounds(compact ? 4 : 8, height - 26, compact ? sidebarW - 8 : sidebarW - 16, 20).build());
		if (selected < 0) {
			select(Math.clamp(LAST_PAGE.getOrDefault(id, 0), 0, tiles.size() - 1), false);
		} else if (page != null) {
			layoutPage();
		}
	}

	private void select(int index, boolean animate) {
		if (index == selected && page != null) return;
		Tile tile = tiles.get(index);
		EditorScreen next = pages[index];
		if (next == null) {
			Screen s;
			try {
				s = tile.open().apply(this);
			} catch (RuntimeException e) {
				GolemAddonsClient.LOGGER.warn("Couldn't open {}", tile.key(), e);
				return;
			}
			if (!(s instanceof EditorScreen e)) {
				if (s != null) minecraft.setScreen(s); // a page that needs the whole screen
				return;
			}
			next = e;
			pages[index] = next;
		}
		selected = index;
		LAST_PAGE.put(id, index);
		page = next;
		scroll = scrollTarget = 0;
		switchedAt = animate ? Util.getMillis() : 0;
		page.replayIntro();
		layoutPage();
	}

	private void layoutPage() {
		page.init(width - sidebarW, height);
	}

	/** Called by a page after each layout: makes long pages taller (to scroll) and short ones fit. */
	void childBuilt(EditorScreen built) {
		if (built != page || relayouting) return;
		relayouting = true;
		try {
			if (built.height != height) built.relayout(height);
			int needed = built.contentBottom() + EditorScreen.FOOTER_HEIGHT + 8;
			if (needed > height) built.relayout(Math.min(needed, height * 4));
		} finally {
			relayouting = false;
		}
		maxScroll = Math.max(0, built.height - height);
		scrollTarget = Math.clamp(scrollTarget, 0, maxScroll);
		scroll = Math.clamp(scroll, 0, maxScroll);
		built.pinFooter(height, scrollPx());
	}

	boolean saveAll() {
		boolean ok = GolemAddonsClient.config().save(working.copy());
		if (ok) savedAt = Util.getMillis();
		return ok;
	}

	/** Throws away unsaved changes in every page of the section. */
	void revert() {
		working = GolemAddonsClient.config().get().copy();
		java.util.Arrays.fill(pages, null);
		int keep = selected;
		selected = -1;
		page = null;
		select(keep, true);
	}

	@Override
	public void onClose() {
		saveAll();
		minecraft.setScreen(parent);
	}

	@Override
	public void tick() {
		if (page != null) page.tick();
	}

	// ---- Input: anything over the pane goes to the page, in its coordinates -------------------------------------

	private boolean overPane(double x, double y) {
		return x >= sidebarW && y >= HEADER;
	}

	private MouseButtonEvent toPage(MouseButtonEvent e) {
		return new MouseButtonEvent(e.x() - childOffsetX(), e.y() - childOffsetY(), e.buttonInfo());
	}

	private boolean overScrollbar(double x, double y) {
		return maxScroll > 0 && x >= width - 7 && y >= HEADER;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (overScrollbar(event.x(), event.y()) && event.button() == 0) {
			draggingBar = true;
			dragBar(event.y());
			return true;
		}
		if (page != null && overPane(event.x(), event.y())) {
			setFocused(null);
			return page.mouseClicked(toPage(event), doubleClick);
		}
		if (page != null) page.setFocused(null);
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingBar = false;
		boolean handled = page != null && page.mouseReleased(toPage(event));
		return super.mouseReleased(event) || handled;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingBar) {
			dragBar(event.y());
			return true;
		}
		if (page != null && page.isDragging()) return page.mouseDragged(toPage(event), dx, dy);
		return super.mouseDragged(event, dx, dy);
	}

	private void dragBar(double y) {
		float track = Math.max(1, height - HEADER - 8);
		scrollTarget = Math.clamp((float) ((y - HEADER - 4) / track) * (maxScroll + height) - height / 2f, 0, maxScroll);
	}

	@Override
	public void mouseMoved(double x, double y) {
		if (page != null) page.mouseMoved(x - childOffsetX(), y - childOffsetY());
		super.mouseMoved(x, y);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (page != null && overPane(x, y)) {
			double px = x - childOffsetX();
			double py = y - childOffsetY();
			// Cycle buttons change value on scroll; on a page that scrolls, the wheel scrolls the page instead.
			boolean cycleUnder = page.getChildAt(px, py).filter(c -> c instanceof GolemCycle<?>).isPresent();
			if ((maxScroll == 0 || !cycleUnder) && page.mouseScrolled(px, py, scrollX, scrollY)) return true;
			if (maxScroll > 0) {
				scrollTarget = Math.clamp(scrollTarget - (float) scrollY * SCROLL_STEP, 0, maxScroll);
				return true;
			}
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		if (page != null && page.keyPressed(event)) return true;
		switch (event.key()) {
			case GLFW.GLFW_KEY_PAGE_DOWN -> scrollTarget = Math.clamp(scrollTarget + height * 0.8f, 0, maxScroll);
			case GLFW.GLFW_KEY_PAGE_UP -> scrollTarget = Math.clamp(scrollTarget - height * 0.8f, 0, maxScroll);
			case GLFW.GLFW_KEY_HOME -> scrollTarget = 0;
			case GLFW.GLFW_KEY_END -> scrollTarget = maxScroll;
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		return (page != null && page.keyReleased(event)) || super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return (page != null && page.charTyped(event)) || super.charTyped(event);
	}

	// ---- Drawing ---------------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		backdrop.extract(graphics, width, height, 1f, (Util.getMillis() - openedAt) / 1000f);
		// Sidebar
		graphics.fillGradient(0, HEADER, sidebarW, height, 0xC8100D1C, 0xD0080710);
		graphics.fill(sidebarW - 1, HEADER, sidebarW, height, 0x30FFFFFF);
		// Header
		graphics.fillGradient(0, 0, width, HEADER, 0xD0120E22, 0xB0000000);
		graphics.fill(0, HEADER - 1, width, HEADER, UiText.withAlpha(UiText.ACCENT, 0.8f));
		graphics.fill(0, HEADER - 1, width / 3, HEADER, UiText.withAlpha(accent, 0.7f));
		graphics.text(font, Component.literal("GolemAddons").withColor(UiText.ACCENT & 0xFFFFFF), 8, 8, UiText.WHITE);
		Component crumbs = page == null ? title
				: Component.empty().append(title).append(Component.literal("  ›  ").withColor(UiText.DARK_GRAY & 0xFFFFFF)).append(page.getTitle());
		graphics.centeredText(font, crumbs, sidebarW + (width - sidebarW) / 2, 8, UiText.WHITE);
		float saved = 1f - Math.clamp((Util.getMillis() - savedAt - 1400) / 500f, 0f, 1f);
		if (savedAt > 0 && saved > 0.02f) {
			Component s = Component.translatable("golemaddons.section.saved");
			graphics.text(font, s, width - 10 - font.width(s), 8, UiText.withAlpha(UiText.GREEN, saved));
		}
		if (!compact) graphics.text(font, title, 10, HEADER + 8, UiText.lerpColor(0xFFEDE9FF, accent, 0.4f));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		float dt = UiAnim.tick(lastFrame);
		scroll = Math.abs(scrollTarget - scroll) < 0.25f ? scrollTarget : UiAnim.approach(scroll, scrollTarget, dt, 16f);

		// Selected-page marker glides between tabs.
		if (selected >= 0) {
			float target = tabsTop() + selected * TAB_H;
			indicatorY = indicatorY < 0 ? target : UiAnim.approach(indicatorY, target, dt, 18f);
			int iy = Math.round(indicatorY);
			graphics.fill(4, iy, sidebarW - 4, iy + TAB_H - 2, UiText.withAlpha(tiles.get(selected).accent(), 0.16f));
			graphics.fill(4, iy, 6, iy + TAB_H - 2, tiles.get(selected).accent());
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (page == null) return;
		page.pinFooter(height, scrollPx());
		int ox = childOffsetX();
		int oy = childOffsetY();
		boolean inside = overPane(mouseX, mouseY) && !draggingBar;
		int pmx = inside ? mouseX - ox : -10000;
		int pmy = inside ? mouseY - oy : -10000;
		GuiGraphicsAccessor deferred = (GuiGraphicsAccessor) graphics;
		Runnable before = deferred.golemaddons$deferredTooltip();
		graphics.enableScissor(sidebarW, HEADER, width, height);
		graphics.pose().pushMatrix();
		graphics.pose().translate(ox, oy);
		try {
			page.extractBackground(graphics, pmx, pmy, delta);
			page.extractRenderState(graphics, pmx, pmy, delta);
		} finally {
			graphics.pose().popMatrix();
			graphics.disableScissor();
		}
		Runnable after = deferred.golemaddons$deferredTooltip();
		if (after != null && after != before) {
			deferred.golemaddons$setDeferredTooltip(() -> {
				graphics.pose().pushMatrix();
				graphics.pose().translate(ox, oy);
				after.run();
				graphics.pose().popMatrix();
			});
		}

		if (maxScroll > 0) {
			int top = HEADER + 4;
			int track = height - top - 4;
			int thumb = Math.max(18, Math.round(track * height / (float) (height + maxScroll)));
			int ty = top + Math.round((track - thumb) * (scroll / maxScroll));
			boolean hot = draggingBar || overScrollbar(mouseX, mouseY);
			graphics.fill(width - 5, top, width - 2, top + track, 0x30FFFFFF);
			graphics.fill(width - 5, ty, width - 2, ty + thumb, hot ? UiText.ACCENT_2 : UiText.withAlpha(UiText.ACCENT, 0.85f));
		}
	}

	/** A page in the sidebar: icon, name and its one-line status. */
	private final class PageTab extends AbstractButton {
		private final int index;
		private float hover;
		private final long[] last = new long[1];

		PageTab(int index, int x, int y, int w, int h) {
			super(x, y, w, h, Component.translatable(tiles.get(index).key()));
			this.index = index;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			select(index, true);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			Tile tile = tiles.get(index);
			float dt = UiAnim.tick(last);
			hover = UiAnim.approach(hover, isHoveredOrFocused() ? 1f : 0f, dt, 14f);
			float appear = UiAnim.appear(openedAt + 40L * index);
			int x = getX() + Math.round((1f - appear) * -10f);
			int y = getY();
			if (hover > 0.02f) g.fill(x, y, x + width, y + height, UiText.withAlpha(0x22FFFFFF, hover));
			if (compact) {
				g.item(tile.icon(), x + (width - 16) / 2, y + (height - 16) / 2);
				return;
			}
			g.item(tile.icon(), x + 6, y + (height - 16) / 2);
			int tx = x + 27;
			boolean on = index == selected;
			int nameColor = UiText.withAlpha(on ? UiText.WHITE : UiText.lerpColor(0xFFC8C4D8, UiText.WHITE, hover), appear);
			g.text(font, fit(getMessage().getString(), width - 31), tx, y + 5, nameColor);
			HubCard.Status status = safeStatus(tile);
			if (status != null) {
				g.pose().pushMatrix();
				g.pose().translate(tx, y + 16);
				g.pose().scale(0.8f, 0.8f);
				g.text(font, fit(status.text().getString(), Math.round((width - 31) / 0.8f)), 0, 0, UiText.withAlpha(status.color(), appear));
				g.pose().popMatrix();
			}
		}

		private String fit(String s, int w) {
			return font.width(s) <= w ? s : font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))) + "…";
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	private static HubCard.Status safeStatus(Tile tile) {
		try {
			return tile.status().get();
		} catch (RuntimeException e) {
			return null;
		}
	}
}
