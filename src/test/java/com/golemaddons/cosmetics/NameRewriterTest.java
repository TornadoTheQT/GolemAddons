package com.golemaddons.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.StringDecomposer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NameRewriterTest {
	private final NameRewriter rewriter = new NameRewriter();
	private final Component custom = Component.literal("Zach").withStyle(ChatFormatting.GOLD);

	@BeforeEach
	void setUp() {
		rewriter.update(Map.of("qtTornado", custom));
	}

	private String shown(Component c) {
		return StringDecomposer.getPlainText(c);
	}

	private String rewritten(Component c) {
		return shown(rewriter.rewrite(c));
	}

	@Test
	void removesRankAndReplacesName() {
		assertEquals("Zach: hello", rewritten(Component.literal("[DRAGON] qtTornado: hello")));
		assertEquals("Zach", rewritten(Component.literal("[MVP+]qtTornado")));
	}

	@Test
	void withoutRankOnlyTheNameIsReplaced() {
		assertEquals("Zach joined the game", rewritten(Component.literal("qtTornado joined the game")));
		assertEquals("Welcome, Zach!", rewritten(Component.literal("Welcome, qtTornado!")));
	}

	@Test
	void onlyTheRankDirectlyBeforeTheNameIsRemoved() {
		assertEquals("[120] Zach: hi", rewritten(Component.literal("[120] [DRAGON] qtTornado: hi")));
		assertEquals("[DRAGON] says Zach", rewritten(Component.literal("[DRAGON] says qtTornado")));
	}

	@Test
	void wholeWordAndExactCaseOnly() {
		Component longer = Component.literal("[DRAGON] qtTornado2: hi");
		assertSame(longer, rewriter.rewrite(longer));
		Component prefixed = Component.literal("xqtTornado");
		assertSame(prefixed, rewriter.rewrite(prefixed));
		Component lower = Component.literal("qttornado");
		assertSame(lower, rewriter.rewrite(lower));
	}

	@Test
	void legacyColorCodesAndSplitComponentsStillMatch() {
		assertEquals("Zach: hi", rewritten(Component.literal("§6[DRAGON] §fqtTornado§7: hi")));
		MutableComponent split = Component.literal("[DRA").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("GON] qt").withStyle(ChatFormatting.RED))
				.append(Component.literal("Tornado").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(": hi").withStyle(ChatFormatting.GRAY));
		Component out = rewriter.rewrite(split);
		assertEquals("Zach: hi", shown(out));
		// The text after the name keeps its color.
		Style[] after = new Style[1];
		StringDecomposer.iterateFormatted(out, Style.EMPTY, (i, style, cp) -> {
			if (cp == ':') after[0] = style;
			return true;
		});
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), after[0].getColor());
	}

	@Test
	void customColorsWinButUncoloredPartsKeepTheUsernameStyle() {
		rewriter.update(Map.of("qtTornado", Component.literal("Plain")));
		Component out = rewriter.rewrite(Component.literal("qtTornado").withStyle(ChatFormatting.AQUA));
		Style[] first = new Style[1];
		StringDecomposer.iterateFormatted(out, Style.EMPTY, (i, style, cp) -> {
			first[0] = first[0] == null ? style : first[0];
			return true;
		});
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), first[0].getColor());

		rewriter.update(Map.of("qtTornado", custom));
		Component gold = rewriter.rewrite(Component.literal("qtTornado").withStyle(ChatFormatting.AQUA));
		Style[] g = new Style[1];
		StringDecomposer.iterateFormatted(gold, Style.EMPTY, (i, style, cp) -> {
			g[0] = g[0] == null ? style : g[0];
			return true;
		});
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), g[0].getColor());
	}

	@Test
	void severalNamesInOneLine() {
		rewriter.update(Map.of("qtTornado", custom, "Steve", Component.literal("Bob")));
		assertEquals("Zach and Bob", rewritten(Component.literal("[DRAGON] qtTornado and [OLD] Steve")));
	}

	@Test
	void identicalInputsAreMemoized() {
		Component a = rewriter.rewrite(Component.literal("[DRAGON] qtTornado"));
		Component b = rewriter.rewrite(Component.literal("[DRAGON] qtTornado"));
		assertSame(a, b);
	}

	@Test
	void noNamesMeansNoChangeAndInvalidUsernamesAreIgnored() {
		rewriter.clear();
		Component line = Component.literal("[DRAGON] qtTornado");
		assertSame(line, rewriter.rewrite(line));
		rewriter.update(Map.of("bad name", custom, "§cX", custom));
		assertTrue(rewriter.isEmpty());
		assertSame(line, rewriter.rewrite(line));
	}

	@Test
	void hoveringACustomNameShowsTheRealUsername() {
		Component out = rewriter.rewrite(Component.literal("[DRAGON] qtTornado: hi"));
		net.minecraft.network.chat.HoverEvent[] hover = new net.minecraft.network.chat.HoverEvent[1];
		StringDecomposer.iterateFormatted(out, Style.EMPTY, (i, style, cp) -> {
			if (cp == 'Z') hover[0] = style.getHoverEvent();
			return true;
		});
		assertTrue(hover[0] instanceof net.minecraft.network.chat.HoverEvent.ShowText show
				&& show.value().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
				&& java.util.Arrays.asList(t.getArgs()).contains("qtTornado"), String.valueOf(hover[0]));
	}
}
