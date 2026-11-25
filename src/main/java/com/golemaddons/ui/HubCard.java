package com.golemaddons.ui;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * A clickable tile on the hub, drawn on the {@link HubCanvas} so it looks the same at every GUI scale.
 * A real button: works with the mouse, Tab and Enter, and screen readers. It slides in when the hub opens
 * and lifts and highlights when hovered or focused. With no icon it renders as a plain styled button.
 */
final class HubCard extends AbstractButton {
	private static final long ENTRANCE_MS = 420;
	private static final long STAGGER_MS = 90;

	record Status(Component text, int color) {
	}

	private final float unitWidth;
	private final float unitHeight;
	private final float scale;
	private final ItemStack icon;
	private final Component description;
	private final Supplier<Status> status;
	private final int accent;
	private final int index;
	private final long openedAt;
	private final Runnable action;

	private float hover;
	private long lastFrame;

	/** A feature card. */
	HubCard(HubCanvas canvas, float ux, float uy, float uw, float uh, Component title, Component description, ItemStack icon,
			int accent, int index, long openedAt, Supplier<Status> status, Runnable action) {
		super(Math.round(canvas.x(ux)), Math.round(canvas.y(uy)), canvas.size(uw), canvas.size(uh), title);
		this.unitWidth = uw;
		this.unitHeight = uh;
		this.scale = canvas.scale();
		this.icon = icon;
		this.description = description;
		this.status = status;
		this.accent = accent;
		this.index = index;
		this.openedAt = openedAt;
		this.action = action;
	}

	/** A plain styled button (no icon, status or description). */
	static HubCard button(HubCanvas canvas, float ux, float uy, float uw, float uh, Component label, int accent, int index,
			long openedAt, Runnable action) {
		return new HubCard(canvas, ux, uy, uw, uh, label, Component.empty(), ItemStack.EMPTY, accent, index, openedAt, null, action);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.run();
	}

	private static float easeOutCubic(float t) {
		float u = 1f - Math.clamp(t, 0f, 1f);
		return 1f - u * u * u;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float appear = easeOutCubic((now - openedAt - index * STAGGER_MS) / (float) ENTRANCE_MS);
		if (appear <= 0.01f) {
			return;
		}
		float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		hover += ((isHoveredOrFocused() ? 1f : 0f) - hover) * Math.min(1f, dt * 12f);

		Font font = Minecraft.getInstance().font;
		float w = unitWidth;
		float h = unitHeight;
		int iw = Math.round(w);
		int ih = Math.round(h);

		graphics.pose().pushMatrix();
		graphics.pose().translate(getX(), getY());
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(0f, (1f - appear) * 18f - hover * 3f);

		int bg = UiText.lerpColor(0xC8100E18, 0xE81E1832, hover);
		graphics.fill(0, 0, iw, ih, UiText.withAlpha(bg, appear));
		graphics.outline(0, 0, iw, ih, UiText.withAlpha(UiText.lerpColor(0x38FFFFFF, accent, hover), appear));
		int barWidth = Math.round(w * (0.2f + 0.8f * hover));
		graphics.fill(0, 0, barWidth, 3, UiText.withAlpha(accent, appear));

		int white = UiText.withAlpha(UiText.WHITE, appear);
		if (status == null) {
			// Plain button: centered label.
			graphics.centeredText(font, getMessage(), iw / 2, (ih - 8) / 2 + 1, white);
		} else {
			float bob = (float) Math.sin(now / 420.0 + index * 1.3) * (1.5f + hover * 1.5f);
			if (appear > 0.6f) {
				graphics.pose().pushMatrix();
				graphics.pose().translate(w / 2f - 24f, 14f + bob);
				graphics.pose().scale(3f, 3f);
				graphics.item(icon, 0, 0);
				graphics.pose().popMatrix();
			}

			// Title and status shrink to fit narrow cards instead of running off the edge.
			float titleScale = Math.min(1.5f, (w - 10f) / Math.max(1, font.width(getMessage())));
			graphics.pose().pushMatrix();
			graphics.pose().translate(w / 2f, 74f);
			graphics.pose().scale(titleScale, titleScale);
			graphics.centeredText(font, getMessage(), 0, 0, white);
			graphics.pose().popMatrix();

			Status current = status.get();
			float statusScale = Math.min(1f, (w - 8f) / Math.max(1, font.width(current.text())));
			graphics.pose().pushMatrix();
			graphics.pose().translate(w / 2f, 94f);
			graphics.pose().scale(statusScale, statusScale);
			graphics.centeredText(font, current.text(), 0, 0, UiText.withAlpha(current.color(), appear));
			graphics.pose().popMatrix();

			List<FormattedCharSequence> lines = font.split(description, iw - 14);
			for (int i = 0; i < Math.min(3, lines.size()); i++) {
				graphics.centeredText(font, lines.get(i), iw / 2, 114 + i * 11, UiText.withAlpha(UiText.GRAY, appear));
			}

			if (hover > 0.05f) {
				Component open = Component.translatable("golemaddons.hub.open");
				int slide = Math.round((1f - hover) * 6f);
				graphics.centeredText(font, open, iw / 2 + slide, ih - 16, UiText.withAlpha(accent, hover * appear));
			}
		}
		graphics.pose().popMatrix();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
