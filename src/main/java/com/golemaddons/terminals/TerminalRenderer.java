package com.golemaddons.terminals;

import com.golemaddons.config.GolemConfig;
import java.util.function.IntFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the compact terminal GUI (also used by the settings preview) and the highlights on a normal chest, in the
 * player's colours.
 */
public final class TerminalRenderer {
	private static final long FLASH_MS = 350;

	private TerminalRenderer() {
	}

	/** What one frame needs: the board, what to click, and where the cursor and the last refused click are. */
	/**
	 * {@code fade} (0..1) darkens the wrong items in Starts With and Select All, and {@code saturation} (0..1) washes
	 * their colour out; 0 and 1 leave them as they are.
	 */
	public record Frame(String title, TerminalBoard board, IntFunction<ItemStack> stacks, TerminalSolution view, int hovered,
			int blockedSlot, long blockedAge, float fade, float saturation) {
		public static Frame of(String title, TerminalBoard board, IntFunction<ItemStack> stacks, TerminalSolution view, int hovered,
				int blockedSlot, long blockedAge, GolemConfig.Terminals cfg) {
			return new Frame(title, board, stacks, view, hovered, blockedSlot, blockedAge, cfg.fadeOthers ? cfg.fadePercent / 100f : 0f,
					cfg.fadeOthers ? cfg.othersSaturation / 100f : 1f);
		}

		boolean dims() {
			return fade > 0 || saturation < 1;
		}
	}

	/**
	 * Dims a wrong item: a grey wash takes out colour (items can't be recoloured as they're drawn, so this mixes
	 * them towards their own grey), then a dark layer takes out brightness.
	 */
	private static void dim(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, Frame f, int darkColor) {
		if (f.saturation() < 1) g.fill(x0, y0, x1, y1, alpha(0xFF8A8A8A, (1f - f.saturation()) * 0.8f));
		if (f.fade() > 0) g.fill(x0, y0, x1, y1, alpha(darkColor | 0xFF000000, f.fade()));
	}

	public static void drawCompact(GuiGraphicsExtractor g, Font font, TerminalLayout l, Frame f, GolemConfig.Terminals.Colors c) {
		String title = TerminalType.plain(f.title());
		Component status = f.view() == null ? Component.empty() : status(f.view());
		// Wide enough for the title and status, even over a narrow grid like Same Colour's.
		int pw = Math.max(l.panelW(), Math.max(font.width(title), font.width(status)) + 16);
		int px = l.x() + l.gridWidth() / 2 - pw / 2;
		int py = l.panelY();
		int ph = l.panelH();
		g.fill(px + 2, py + 3, px + pw + 2, py + ph + 3, 0x40000000);
		roundRect(g, px, py, pw, ph, c.background);
		int bar = Math.max(2, pw / 3);
		g.fill(px + 2, py, px + bar, py + 1, alpha(c.solution, 0.9f));
		g.fill(px + bar, py, px + 2 * bar, py + 1, alpha(c.third, 0.7f));
		g.fill(px + 1, py + 1, px + 2, py + ph - 1, alpha(c.solution, 0.6f));

		g.centeredText(font, title, px + pw / 2, py + 5, c.text);

		TerminalType type = l.type();
		for (int slot : TerminalSolver.grid(type)) {
			drawCell(g, font, l, f, c, type, slot);
		}

		TerminalSolution view = f.view();
		if (view != null) {
			g.centeredText(font, status, px + pw / 2, l.y() + l.gridHeight() + TerminalLayout.PAD - 1,
					view.ready() ? c.solution : alpha(c.text, 0.55f));
		}
	}

	private static void drawCell(GuiGraphicsExtractor g, Font font, TerminalLayout l, Frame f, GolemConfig.Terminals.Colors c,
			TerminalType type, int slot) {
		int x = l.cellX(slot);
		int y = l.cellY(slot);
		int s = l.cell();
		TerminalBoard.Item item = f.board().get(slot);
		// Melody's track panes and buttons are often unnamed on real servers: there only the black and grey
		// background panes are filler. Elsewhere an unnamed item is the background.
		boolean filler = type == TerminalType.MELODY ? isBackground(item) : item.isEmpty() || item.name().isBlank();
		g.fill(x, y, x + s, y + s, filler ? alpha(c.tile, 0.4f) : c.tile);

		TerminalSolution view = f.view();
		int index = view == null ? -1 : view.indexOf(slot);
		// Click in Order lights up the next three; later numbers stay plain so the next one stands out.
		boolean lit = index >= 0 && !(type == TerminalType.ORDER && index > 2);
		int color = 0;
		if (lit) {
			color = type == TerminalType.ORDER ? (index == 0 ? c.solution : index == 1 ? c.second : c.third) : c.solution;
			if (type == TerminalType.RUBIX && view.steps().get(index).right()) color = c.second;
			g.fill(x, y, x + s, y + s, alpha(color, 0.5f));
		}

		if (!filler) {
			switch (type) {
				case RUBIX, MELODY -> {
					int rgb = TerminalStacks.paneRgb(item);
					if (rgb >= 0) {
						int inset = Math.max(2, s / 5);
						g.fill(x + inset, y + inset, x + s - inset, y + s - inset, 0xFF000000 | rgb);
					} else {
						item(g, f.stacks().apply(slot), x, y, s);
					}
				}
				case STARTS_WITH, COLORS -> {
					item(g, f.stacks().apply(slot), x, y, s);
					if (item.glint()) {
						g.fill(x, y, x + s, y + s, alpha(c.tile, 0.65f));
					} else if (index < 0 && f.dims() && view != null && view.ready()) {
						dim(g, x, y, x + s, y + s, f, c.tile);
					}
				}
				default -> {
				}
			}
		}

		if (lit) g.outline(x, y, s, s, color);
		if (index >= 0) {
			String label = switch (type) {
				case ORDER -> Integer.toString(item.count());
				case RUBIX -> (view.steps().get(index).right() ? "R" : "L") + view.steps().get(index).clicks();
				default -> "";
			};
			if (!label.isEmpty()) g.centeredText(font, label, x + s / 2 + 1, y + (s - 8) / 2 + 1, lit ? c.text : alpha(c.text, 0.55f));
		}
		if (slot == f.hovered()) {
			g.fill(x, y, x + s, y + s, alpha(c.text, 0.14f));
			g.outline(x - 1, y - 1, s + 2, s + 2, alpha(c.text, 0.9f));
		}
		if (slot == f.blockedSlot() && f.blockedAge() >= 0 && f.blockedAge() < FLASH_MS) {
			g.fill(x, y, x + s, y + s, alpha(c.blocked, 0.65f * (1f - f.blockedAge() / (float) FLASH_MS)));
		}
	}

	/** Highlights on the normal chest, for players who keep the vanilla look. */
	public static void drawHighlights(GuiGraphicsExtractor g, Font font, int left, int top, net.minecraft.world.inventory.AbstractContainerMenu menu,
			Frame f, GolemConfig.Terminals.Colors c) {
		TerminalSolution view = f.view();
		if (view == null) return;
		if (f.dims() && view.ready() && (view.type() == TerminalType.STARTS_WITH || view.type() == TerminalType.COLORS)) {
			for (int slot : TerminalSolver.grid(view.type())) {
				TerminalBoard.Item item = f.board().get(slot);
				if (item.isEmpty() || item.name().isBlank() || view.indexOf(slot) >= 0) continue;
				var s = menu.getSlot(slot);
				dim(g, left + s.x, top + s.y, left + s.x + 16, top + s.y + 16, f, 0xFF101010);
			}
		}
		for (int i = 0; i < view.steps().size(); i++) {
			TerminalSolution.Step step = view.steps().get(i);
			var slot = menu.getSlot(step.slot());
			int x = left + slot.x;
			int y = top + slot.y;
			int color = view.type() == TerminalType.ORDER ? (i == 0 ? c.solution : i == 1 ? c.second : c.third)
					: view.type() == TerminalType.RUBIX && step.right() ? c.second : c.solution;
			if (view.type() == TerminalType.ORDER && i > 2) continue;
			g.fill(x, y, x + 16, y + 16, alpha(color, 0.5f));
			g.outline(x - 1, y - 1, 18, 18, color);
			if (view.type() == TerminalType.RUBIX) {
				g.centeredText(font, (step.right() ? "R" : "L") + step.clicks(), x + 9, y + 4, c.text);
			}
		}
		if (f.blockedSlot() >= 0 && f.blockedSlot() < menu.slots.size() && f.blockedAge() < FLASH_MS) {
			var slot = menu.getSlot(f.blockedSlot());
			g.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16,
					alpha(c.blocked, 0.65f * (1f - f.blockedAge() / (float) FLASH_MS)));
		}
		g.text(font, status(view), left, Math.max(2, top - 11), view.ready() ? c.solution : alpha(c.text, 0.6f));
	}

	public static Component status(TerminalSolution view) {
		String arg = view.arg();
		if (view.status() == TerminalSolution.Status.SAME_COLOR && !arg.isEmpty()) {
			return Component.translatable(view.status().key(), Component.translatable("golemaddons.terminals.color." + arg));
		}
		return Component.translatable(view.status().key(), arg);
	}

	/** The panes a chest is padded with. */
	private static boolean isBackground(TerminalBoard.Item item) {
		return item.isEmpty() || item.is("black_stained_glass_pane") || item.is("gray_stained_glass_pane");
	}

	private static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int s) {
		if (stack == null || stack.isEmpty()) return;
		float k = Math.max(0.5f, (s - 2) / 16f);
		g.pose().pushMatrix();
		g.pose().translate(x + (s - 16 * k) / 2f, y + (s - 16 * k) / 2f);
		g.pose().scale(k, k);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	private static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x + 1, y, x + w - 1, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	static int alpha(int argb, float a) {
		int base = argb >>> 24;
		return (Mth.clamp(Math.round(base * a), 0, 255) << 24) | (argb & 0xFFFFFF);
	}
}
