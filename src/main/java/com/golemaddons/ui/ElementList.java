package com.golemaddons.ui;

import com.golemaddons.config.GolemConfig;
import com.golemaddons.scoreboard.ScoreboardElement;
import com.golemaddons.scoreboard.ScoreboardFeature;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The scoreboard's lines as a reorderable list: drag a row (or Shift + arrow keys) to move it, click its box (or
 * Space) to turn it on or off. Edits the list in place. Scrolls smoothly when there are more rows than fit.
 */
final class ElementList extends AbstractWidget {
	static final int ROW = 16;

	private final List<GolemConfig.Scoreboard.Element> elements;
	private final IntConsumer onSelect;
	private int selected;
	private float scroll;
	private float scrollTarget;
	private int dragging = -1;
	private double pressY;
	private double dragY;
	private boolean moved;
	private final long[] lastFrame = new long[1];

	ElementList(int x, int y, int w, int h, List<GolemConfig.Scoreboard.Element> elements, int selected, IntConsumer onSelect) {
		super(x, y, w, h, Component.translatable("golemaddons.scoreboard.lines"));
		this.elements = elements;
		this.selected = Math.clamp(selected, -1, elements.size() - 1);
		this.onSelect = onSelect;
	}

	int selected() {
		return selected;
	}

	private int maxScroll() {
		return Math.max(0, elements.size() * ROW - getHeight());
	}

	private int rowAt(double my) {
		int i = (int) Math.floor((my - getY() + scroll) / ROW);
		return i >= 0 && i < elements.size() ? i : -1;
	}

	static Component label(GolemConfig.Scoreboard.Element e) {
		ScoreboardElement type = ScoreboardElement.of(e.type);
		if (type == ScoreboardElement.CUSTOM) {
			return e.text.isBlank() ? Component.translatable("golemaddons.scoreboard.custom.empty").withColor(UiText.DARK_GRAY & 0xFFFFFF)
					: ScoreboardFeature.custom(e.text);
		}
		if (type == ScoreboardElement.BLANK) return Component.translatable("golemaddons.scoreboard.element.blank").withColor(UiText.DARK_GRAY & 0xFFFFFF);
		return type == null ? Component.literal("?") : Component.translatable(type.key());
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float dt = UiAnim.tick(lastFrame);
		scrollTarget = Math.clamp(scrollTarget, 0, maxScroll());
		scroll = Math.abs(scrollTarget - scroll) < 0.3f ? scrollTarget : UiAnim.approach(scroll, scrollTarget, dt, 18f);
		Font font = Minecraft.getInstance().font;
		int x = getX();
		int y = getY();
		int w = getWidth();
		g.fill(x, y, x + w, y + getHeight(), 0x50000000);
		g.enableScissor(x, y, x + w, y + getHeight());
		int dropAt = dragging >= 0 && moved ? dropIndex() : -1;
		for (int i = 0; i < elements.size(); i++) {
			int ry = y + i * ROW - Math.round(scroll);
			if (i == dragging && moved) continue;
			if (ry + ROW < y || ry > y + getHeight()) continue;
			drawRow(g, font, elements.get(i), x, ry, w, i == selected, isHovered() && rowAt(mouseY) == i && dragging < 0, mouseX);
		}
		if (dropAt >= 0) {
			int ly = y + dropAt * ROW - Math.round(scroll);
			g.fill(x + 2, ly - 1, x + w - 2, ly + 1, UiText.ACCENT_2);
		}
		g.disableScissor();
		if (dragging >= 0 && moved) {
			int ry = (int) Math.round(dragY - (pressY - (y + dragging * ROW - scroll)));
			g.fill(x - 2, ry - 1, x + w + 2, ry + ROW + 1, 0xC0241E3A);
			drawRow(g, font, elements.get(dragging), x, ry, w, true, true, mouseX);
		}
		if (maxScroll() > 0) {
			int track = getHeight();
			int thumb = Math.max(10, track * track / (elements.size() * ROW));
			int ty = y + Math.round((track - thumb) * scroll / maxScroll());
			g.fill(x + w - 2, ty, x + w, ty + thumb, UiText.withAlpha(UiText.ACCENT, 0.8f));
		}
	}

	private void drawRow(GuiGraphicsExtractor g, Font font, GolemConfig.Scoreboard.Element e, int x, int y, int w, boolean selected, boolean hover,
			int mouseX) {
		if (selected) g.fill(x, y, x + w, y + ROW, 0x309D7BFF);
		else if (hover) g.fill(x, y, x + w, y + ROW, 0x18FFFFFF);
		// Grip
		for (int i = 0; i < 3; i++) g.fill(x + 4, y + 5 + i * 2, x + 10, y + 6 + i * 2, hover || selected ? 0xC0FFFFFF : 0x60FFFFFF);
		// Checkbox
		int bx = x + 14;
		int by = y + 3;
		g.fill(bx, by, bx + 10, by + 10, 0xFF1A1726);
		g.outline(bx, by, 10, 10, e.enabled ? UiText.ACCENT_2 : 0x80FFFFFF);
		if (e.enabled) g.fill(bx + 2, by + 2, bx + 8, by + 8, UiText.ACCENT_2);
		Component label = label(e);
		int maxW = w - 32;
		String plain = label.getString();
		int color = e.enabled ? UiText.WHITE : UiText.GRAY;
		if (font.width(label) <= maxW) g.text(font, label, x + 29, y + 4, color);
		else g.text(font, font.plainSubstrByWidth(plain, maxW - 6) + "…", x + 29, y + 4, color);
	}

	private int dropIndex() {
		int i = (int) Math.round((dragY - getY() + scroll) / ROW);
		return Math.clamp(i, 0, elements.size());
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		int i = rowAt(event.y());
		if (i < 0) return;
		select(i);
		if (event.x() >= getX() + 12 && event.x() < getX() + 27) {
			elements.get(i).enabled = !elements.get(i).enabled;
			return;
		}
		dragging = i;
		pressY = event.y();
		dragY = event.y();
		moved = false;
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		if (dragging < 0) return;
		dragY = event.y();
		if (Math.abs(dragY - pressY) > 3) moved = true;
		// Scroll while dragging near the edges.
		if (dragY < getY() + 6) scrollTarget -= 4;
		else if (dragY > getY() + getHeight() - 6) scrollTarget += 4;
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		if (dragging >= 0 && moved) move(dragging, dropIndex());
		dragging = -1;
		moved = false;
	}

	/** Moves the element at {@code from} so it lands before the row that was at {@code to}. */
	private void move(int from, int to) {
		if (from < 0 || from >= elements.size()) return;
		int target = to > from ? to - 1 : to;
		target = Math.clamp(target, 0, elements.size() - 1);
		if (target == from) return;
		GolemConfig.Scoreboard.Element e = elements.remove(from);
		elements.add(target, e);
		select(target);
	}

	private void select(int i) {
		selected = i;
		onSelect.accept(i);
		int top = i * ROW;
		if (top < scrollTarget) scrollTarget = top;
		else if (top + ROW > scrollTarget + getHeight()) scrollTarget = top + ROW - getHeight();
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (maxScroll() == 0 || !isMouseOver(x, y)) return false;
		scrollTarget = Math.clamp(scrollTarget - (float) scrollY * ROW * 2, 0, maxScroll());
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (elements.isEmpty()) return false;
		boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
		int s = Math.max(0, selected);
		switch (event.key()) {
			case GLFW.GLFW_KEY_UP -> {
				if (shift) move(s, s - 1);
				else select(Math.max(0, s - 1));
			}
			case GLFW.GLFW_KEY_DOWN -> {
				if (shift) move(s, s + 2);
				else select(Math.min(elements.size() - 1, s + 1));
			}
			case GLFW.GLFW_KEY_SPACE -> elements.get(s).enabled = !elements.get(s).enabled;
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getMessage());
		if (selected >= 0 && selected < elements.size()) output.add(NarratedElementType.HINT, label(elements.get(selected)));
	}
}
