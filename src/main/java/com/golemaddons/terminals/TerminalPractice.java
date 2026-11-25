package com.golemaddons.terminals;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * {@code /gatestterms [type|all]}: practice terminals that run entirely on this client. Each one opens as a real
 * chest screen with the real title, so the solver, GUI, blocking and hover click are exactly what a Floor 7 terminal
 * gets. Clicks are answered after the practice ping, like a server would. Nothing is ever sent to the server.
 */
public final class TerminalPractice {
	/** Far from any id a server hands out in normal play; never assigned to the player's open menu anyway. */
	private static final int CONTAINER_ID = 97;
	private static TerminalPractice current;

	private record Click(int slot, boolean right, long at) {
	}

	private final Deque<TerminalType> queue;
	private final int total;
	private final Random random = new Random();
	private final List<Click> wire = new ArrayList<>();
	private TerminalPuzzle puzzle;
	private SimpleContainer container;
	private PracticeScreen screen;
	private long startedAt;
	private long sequenceStartedAt;
	private long sequenceTime;
	private int sequenceMisclicks;
	private long nextOpenAt = -1;
	private String lastResult = "";

	private TerminalPractice(List<TerminalType> types) {
		this.queue = new ArrayDeque<>(types);
		this.total = types.size();
	}

	static void registerCommand() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(literal("gatestterms")
				.executes(ctx -> start("all"))
				.then(argument("terminal", StringArgumentType.word())
						.suggests((ctx, builder) -> {
							builder.suggest("all");
							for (TerminalType t : TerminalType.values()) builder.suggest(t.name().toLowerCase(Locale.ROOT));
							return builder.buildFuture();
						})
						.executes(ctx -> start(StringArgumentType.getString(ctx, "terminal"))))));
	}

	private static int start(String which) {
		List<TerminalType> types;
		if (which.equalsIgnoreCase("all")) {
			types = new ArrayList<>(Arrays.asList(TerminalType.values()));
			Collections.shuffle(types);
		} else {
			TerminalType type;
			try {
				type = TerminalType.valueOf(which.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				message(Component.translatable("golemaddons.terminals.practice.unknown", which).withStyle(ChatFormatting.RED));
				return 0;
			}
			types = List.of(type);
		}
		Minecraft mc = Minecraft.getInstance();
		// The chat screen that ran the command closes after this returns: open on the next tick.
		mc.schedule(() -> start(types));
		return 1;
	}

	/** Starts a practice run of these terminals, in order. Public for the in-game test. */
	public static void start(List<TerminalType> types) {
		current = new TerminalPractice(types);
		current.sequenceStartedAt = Util.getMillis();
		current.openNext();
	}

	public static TerminalPractice current() {
		return current;
	}

	public TerminalPuzzle puzzle() {
		return puzzle;
	}

	private void openNext() {
		Minecraft mc = Minecraft.getInstance();
		TerminalType type = queue.poll();
		if (type == null || mc.player == null) {
			finishSequence();
			return;
		}
		puzzle = TerminalPuzzle.create(type, random, TerminalStacks.pool());
		container = new SimpleContainer(type.slots());
		for (int i = 0; i < type.slots(); i++) container.setItem(i, TerminalStacks.stack(puzzle.get(i)));
		MenuType<ChestMenu> menuType = switch (type.rows) {
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
		Inventory inventory = mc.player.getInventory();
		ChestMenu menu = new ChestMenu(menuType, CONTAINER_ID, inventory, container, type.rows);
		wire.clear();
		nextOpenAt = -1;
		startedAt = Util.getMillis();
		screen = new PracticeScreen(menu, inventory, Component.literal(puzzle.title()), this);
		mc.setScreen(screen);
	}

	/** A click from the GUI: answered after the practice ping. */
	void click(int slot, boolean right, long now) {
		wire.add(new Click(slot, right, now + TerminalFeature.settings().practicePingMs));
	}

	/** Called every frame while the practice screen is open: delivers due answers and moves Melody along. */
	void update(long now) {
		if (puzzle == null) return;
		TerminalBoard before = puzzle.board();
		for (Iterator<Click> it = wire.iterator(); it.hasNext(); ) {
			Click click = it.next();
			if (click.at() > now) continue;
			it.remove();
			puzzle.click(click.slot(), click.right());
			sync(before);
			before = puzzle.board();
			TerminalFeature.practiceSlot(screen, click.slot());
		}
		if (puzzle.advance(now)) sync(before);
		if (puzzle.solved() && nextOpenAt < 0) {
			long time = now - startedAt;
			sequenceTime += time;
			sequenceMisclicks += puzzle.misclicks();
			lastResult = String.format(Locale.ROOT, "%.2fs", time / 1000.0);
			message(Component.translatable("golemaddons.terminals.practice.solved",
					Component.translatable(puzzle.type().key).withStyle(ChatFormatting.AQUA),
					Component.literal(lastResult).withStyle(ChatFormatting.GREEN),
					Component.literal(Integer.toString(puzzle.misclicks())).withStyle(puzzle.misclicks() == 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
			nextOpenAt = now + 400;
		}
		if (nextOpenAt >= 0 && now >= nextOpenAt) {
			nextOpenAt = Long.MAX_VALUE;
			Minecraft.getInstance().schedule(this::openNext);
		}
	}

	private void sync(TerminalBoard before) {
		TerminalBoard after = puzzle.board();
		for (int i = 0; i < after.size(); i++) {
			if (!after.get(i).equals(before.get(i))) container.setItem(i, TerminalStacks.stack(after.get(i)));
		}
	}

	private void finishSequence() {
		Minecraft mc = Minecraft.getInstance();
		if (total > 1 && queue.isEmpty() && puzzle != null) {
			message(Component.translatable("golemaddons.terminals.practice.all",
					Component.literal(String.format(Locale.ROOT, "%.2fs", sequenceTime / 1000.0)).withStyle(ChatFormatting.GREEN),
					Component.literal(String.format(Locale.ROOT, "%.2fs", (Util.getMillis() - sequenceStartedAt) / 1000.0)).withStyle(ChatFormatting.GRAY),
					Component.literal(Integer.toString(sequenceMisclicks)).withStyle(sequenceMisclicks == 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
		}
		current = null;
		if (mc.screen instanceof PracticeScreen) mc.setScreen(null);
	}

	/** Progress and timer in the corner, so a practice run reads as one. */
	void drawOverlay(GuiGraphicsExtractor g, Font font, int width, int height) {
		if (puzzle == null) return;
		long elapsed = (nextOpenAt >= 0 ? 0 : Util.getMillis() - startedAt);
		int done = total - queue.size();
		Component line = Component.translatable("golemaddons.terminals.practice.overlay", done, total,
				nextOpenAt >= 0 ? lastResult : String.format(Locale.ROOT, "%.1fs", elapsed / 1000.0),
				TerminalFeature.settings().practicePingMs);
		g.text(font, line, 6, height - 12, 0xFFB0B0B0);
	}

	private static void message(Component text) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.getChat().addClientSystemMessage(Component.literal("[GolemAddons] ").withStyle(ChatFormatting.GRAY).append(text));
	}

	/** The chest screen a practice terminal opens in. Closing it never tells the server about a container. */
	public static final class PracticeScreen extends ContainerScreen {
		private final TerminalPractice practice;

		PracticeScreen(ChestMenu menu, Inventory inventory, Component title, TerminalPractice practice) {
			super(menu, inventory, title);
			this.practice = practice;
		}

		public TerminalPractice practice() {
			return practice;
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			practice.update(Util.getMillis());
			super.extractRenderState(graphics, mouseX, mouseY, a);
		}

		@Override
		public void onClose() {
			if (current == practice) current = null;
			minecraft.setScreen(null);
		}
	}
}
