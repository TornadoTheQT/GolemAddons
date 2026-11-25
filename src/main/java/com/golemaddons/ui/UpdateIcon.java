package com.golemaddons.ui;

import com.golemaddons.update.Release;
import com.golemaddons.update.UpdateChecker;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The hub's small refresh button. Left click checks for an update, or installs it when one is waiting; it spins
 * while working. Right click opens the update settings. Drawn on the {@link HubCanvas}.
 */
final class UpdateIcon extends AbstractButton {
	/** Icon grid size, in canvas units; the icon is a ring with a gap and an arrowhead, one unit per pixel. */
	private static final int GRID = 9;
	private static final int[][] PIXELS = refreshPixels();

	private final float scale;
	private final float unitSize;
	private final Runnable openSettings;
	private final long[] lastFrame = new long[1];
	private float hover;
	private float angle;
	private long pressedAt;

	UpdateIcon(HubCanvas canvas, float ux, float uy, float size, Runnable openSettings) {
		super(Math.round(canvas.x(ux)), Math.round(canvas.y(uy)), canvas.size(size), canvas.size(size),
				Component.translatable("golemaddons.hub.update.button"));
		this.scale = canvas.scale();
		this.unitSize = size;
		this.openSettings = openSettings;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		pressedAt = Util.getMillis();
		UpdateChecker checker = UpdateChecker.instance();
		Release newer = checker.newerRelease();
		switch (checker.state()) {
			case CHECKING, DOWNLOADING, STAGED -> { }
			default -> {
				if (newer != null && newer.sha256() != null && checker.installUnavailable() == null) {
					checker.install();
				} else {
					checker.check(true);
				}
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (active && visible && event.button() == 1 && isMouseOver(event.x(), event.y())) {
			openSettings.run();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** A circular arrow: a ring with its right side open, ending in an arrowhead that points down (clockwise). */
	private static int[][] refreshPixels() {
		String[] rows = {
				"..####...",
				".#....#..",
				"#......#.",
				"#.....###",
				"#......#.",
				"#........",
				".#.....#.",
				"..#####..",
				"........."};
		java.util.List<int[]> out = new java.util.ArrayList<>();
		for (int y = 0; y < rows.length; y++) {
			for (int x = 0; x < rows[y].length(); x++) {
				if (rows[y].charAt(x) == '#') out.add(new int[] {x, y});
			}
		}
		return out.toArray(new int[0][]);
	}

	private static boolean busy() {
		UpdateChecker.State s = UpdateChecker.instance().state();
		return s == UpdateChecker.State.CHECKING || s == UpdateChecker.State.DOWNLOADING;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float dt = UiAnim.tick(lastFrame);
		hover = UiAnim.approach(hover, isHoveredOrFocused() ? 1f : 0f, dt, 14f);
		// Spins while busy; otherwise eases back to rest, with a quick turn when clicked.
		float kick = Math.max(0f, 1f - (Util.getMillis() - pressedAt) / 400f);
		if (busy()) {
			angle += dt * 6.5f;
		} else {
			angle = UiAnim.approach(angle % (float) (Math.PI * 2), 0f, dt, 10f) + kick * dt * 10f;
		}
		boolean ready = UpdateChecker.instance().newerRelease() != null;
		int color = ready ? UiText.GREEN : UiText.lerpColor(UiText.GRAY, UiText.WHITE, hover);

		float s = unitSize;
		graphics.pose().pushMatrix();
		graphics.pose().translate(getX(), getY());
		graphics.pose().scale(scale, scale);
		int is = Math.round(s);
		graphics.fill(0, 0, is, is, UiText.withAlpha(0xC0141220, 0.6f + 0.4f * hover));
		graphics.outline(0, 0, is, is, UiText.withAlpha(ready ? UiText.GREEN : UiText.lerpColor(0x40FFFFFF, UiText.ACCENT_2, hover), 0.9f));
		graphics.pose().translate(s / 2f, s / 2f);
		graphics.pose().rotate(angle);
		graphics.pose().translate(-GRID / 2f, -GRID / 2f);
		for (int[] px : PIXELS) graphics.fill(px[0], px[1], px[0] + 1, px[1] + 1, color);
		graphics.pose().popMatrix();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
