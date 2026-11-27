package com.golemaddons.ui;

import com.golemaddons.config.GolemConfig;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Moves and resizes a HUD: drag it anywhere, scroll over it (or press + / -) to change its size, R puts it back.
 * Edits the given position in place; the parent screen's Save keeps it.
 */
final class HudEditScreen extends Screen {
	private final Screen parent;
	private final GolemConfig.HudPosition pos;
	private final GolemConfig.HudPosition original;
	private final GolemConfig.HudPosition defaults;
	/** Size of the HUD at scale 1. */
	private final Function<Font, int[]> size;
	/** Draws the HUD at (x, y) and scale. */
	private final HudDrawer drawer;
	private boolean dragging;
	private double grabX;
	private double grabY;

	interface HudDrawer {
		void draw(GuiGraphicsExtractor g, int x, int y, float scale);
	}

	HudEditScreen(Screen parent, Component title, GolemConfig.HudPosition pos, GolemConfig.HudPosition defaults, Function<Font, int[]> size,
			HudDrawer drawer) {
		super(title);
		this.parent = parent;
		this.pos = pos;
		this.original = pos.copy();
		this.defaults = defaults;
		this.size = size;
		this.drawer = drawer;
	}

	@Override
	protected void init() {
		int w = 90;
		int y = height - 28;
		addRenderableWidget(GolemButton.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - w - 4, y, w, 20).build());
		addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.hud.reset"), b -> reset()).bounds(width / 2 + 4, y, w, 20).build());
		addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.hud.undo"), b -> {
			pos.x = original.x;
			pos.y = original.y;
			pos.scale = original.scale;
		}).bounds(width / 2 + w + 12, y, 60, 20).build());
	}

	private void reset() {
		pos.x = defaults.x;
		pos.y = defaults.y;
		pos.scale = defaults.scale;
	}

	private int[] box() {
		int[] s = size.apply(font);
		int w = Math.round(s[0] * pos.scale);
		int h = Math.round(s[1] * pos.scale);
		int x = Math.round(pos.x * Math.max(0, width - w));
		int y = Math.round(pos.y * Math.max(0, height - h));
		return new int[] {x, y, w, h};
	}

	private boolean over(double mx, double my) {
		int[] b = box();
		return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() == 0 && over(event.x(), event.y())) {
			int[] b = box();
			dragging = true;
			grabX = event.x() - b[0];
			grabY = event.y() - b[1];
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!dragging) return super.mouseDragged(event, dx, dy);
		int[] b = box();
		double free = Math.max(1, width - b[2]);
		double freeY = Math.max(1, height - b[3]);
		pos.x = (float) Math.clamp((event.x() - grabX) / free, 0, 1);
		pos.y = (float) Math.clamp((event.y() - grabY) / freeY, 0, 1);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scale(scrollY > 0 ? 0.1f : -0.1f);
		return true;
	}

	private void scale(float step) {
		pos.scale = Math.clamp(Math.round((pos.scale + step) * 10f) / 10f, GolemConfig.HudPosition.MIN_SCALE, GolemConfig.HudPosition.MAX_SCALE);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		switch (event.key()) {
			case org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD -> scale(0.1f);
			case org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT -> scale(-0.1f);
			case org.lwjgl.glfw.GLFW.GLFW_KEY_R -> reset();
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// A faint grid, so placing it feels deliberate.
		for (int x = 0; x < width; x += 20) g.fill(x, 0, x + 1, height, 0x14FFFFFF);
		for (int y = 0; y < height; y += 20) g.fill(0, y, width, y + 1, 0x14FFFFFF);
		g.fill(width / 2, 0, width / 2 + 1, height, 0x30FFFFFF);
		g.fill(0, height / 2, width, height / 2 + 1, 0x30FFFFFF);
		int[] b = box();
		drawer.draw(g, b[0], b[1], pos.scale);
		g.outline(b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, dragging || over(mouseX, mouseY) ? UiText.ACCENT_2 : 0x80FFFFFF);
		g.centeredText(font, Component.translatable("golemaddons.hud.help", String.format(java.util.Locale.ROOT, "%.1fx", pos.scale)),
				width / 2, 8, UiText.WHITE);
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
