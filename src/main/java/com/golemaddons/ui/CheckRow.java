package com.golemaddons.ui;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * One checklist line: a colored name on the left and a checkbox on the right. The whole row is the click target,
 * and it works with Tab plus Space or Enter like any button.
 */
final class CheckRow extends AbstractButton {
	static final int BOX = 10;
	/** Space around the label and box inside the row. */
	static final int PAD = 5;
	/** Minimum gap between the end of the label and the box. */
	static final int LABEL_GAP = 10;

	private static final Component CHECK = Component.literal("✔");
	private static final int CHECK_FILL = 0xFF2E9E5B;
	private static final int UNCHECKED_LABEL_ALPHA = 0x99;

	private final String label;
	private final int rgb;
	private final Consumer<Boolean> onChange;
	private boolean checked;

	CheckRow(int x, int y, int w, int h, String label, int rgb, boolean checked, Consumer<Boolean> onChange) {
		super(x, y, w, h, Component.literal(label));
		this.label = label;
		this.rgb = rgb & 0xFFFFFF;
		this.checked = checked;
		this.onChange = onChange;
	}

	/** Row width that fits the label, the gap and the box. */
	static int widthFor(Font font, String label) {
		return PAD + font.width(label) + LABEL_GAP + BOX + PAD;
	}

	boolean checked() {
		return checked;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		checked = !checked;
		onChange.accept(checked);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Font font = Minecraft.getInstance().font;
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		boolean live = active;

		if (live && isHovered()) {
			graphics.fill(x, y, x + w, y + h, 0x26FFFFFF);
		}
		if (live && isFocused()) {
			graphics.outline(x, y, w, h, 0xC0FFFFFF);
		}

		int labelAlpha = !live ? 0x55 : checked ? 0xFF : UNCHECKED_LABEL_ALPHA;
		graphics.text(font, getMessage(), x + PAD, y + (h - 8) / 2 + 1, (labelAlpha << 24) | rgb, live && checked);

		int bx = x + w - PAD - BOX;
		int by = y + (h - BOX) / 2;
		int border = !live ? 0x50FFFFFF : checked ? 0xFF55DD88 : 0xA0FFFFFF;
		graphics.fill(bx, by, bx + BOX, by + BOX, 0xA0000000);
		if (checked) {
			graphics.fill(bx + 1, by + 1, bx + BOX - 1, by + BOX - 1, live ? CHECK_FILL : 0x60308050);
			graphics.centeredText(font, CHECK,bx + BOX / 2 + 1, by + 1, live ? UiText.WHITE : 0x80FFFFFF);
		}
		graphics.outline(bx, by, BOX, BOX, border);
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		return Component.translatable("golemaddons.dragon.narration", label,
				Component.translatable(checked ? "golemaddons.dragon.state.sell" : "golemaddons.dragon.state.keep"));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
