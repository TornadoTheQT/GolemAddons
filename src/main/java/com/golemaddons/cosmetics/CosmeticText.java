package com.golemaddons.cosmetics;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Turns parsed custom names into Components. */
public final class CosmeticText {
	/** Vanilla's 16 colors in code order (&0 .. &f). */
	private static final ChatFormatting[] COLORS = {
			ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
			ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
			ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
			ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE};

	private CosmeticText() {
	}

	/** Builds the name directly from styled runs; never goes through legacy section-sign strings. */
	public static Component build(List<CosmeticName.Run> runs) {
		MutableComponent out = Component.empty();
		for (CosmeticName.Run run : runs) {
			Style style = Style.EMPTY;
			if (run.color() >= 0) style = style.withColor(COLORS[run.color()]);
			if (run.bold()) style = style.withBold(true);
			if (run.italic()) style = style.withItalic(true);
			if (run.underlined()) style = style.withUnderlined(true);
			if (run.strikethrough()) style = style.withStrikethrough(true);
			if (run.obfuscated()) style = style.withObfuscated(true);
			out.append(Component.literal(run.text()).setStyle(style));
		}
		return out;
	}
}
