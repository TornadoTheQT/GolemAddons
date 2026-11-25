package com.golemaddons.terminals;

import com.golemaddons.terminals.TerminalSolution.Status;
import com.golemaddons.terminals.TerminalSolution.Step;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Works out the clicks for a terminal from a snapshot of its chest. Pure: no Minecraft classes, no state.
 *
 * <p>{@code selected} lists slots the server has confirmed as clicked. It only matters for Starts With and Select
 * All, where a picked item normally gains a glint; an item that always glints (a Nether Star, say) can only be told
 * apart that way.
 */
public final class TerminalSolver {
	/** The Change All to Same Color cycle. A left click moves a pane one step forward, a right click one back. */
	static final List<String> CYCLE = List.of("red", "orange", "yellow", "green", "blue");
	static final int[] RUBIX_SLOTS = {12, 13, 14, 21, 22, 23, 30, 31, 32};

	/** Items that glint without being enchanted, so a glint on them does not mean "already picked". */
	static final Set<String> ALWAYS_GLINT = Set.of("enchanted_golden_apple", "experience_bottle", "enchanted_book",
			"nether_star", "end_crystal", "debug_stick", "written_book");

	/** Name prefixes per colour, including the old item names (Rose Red, Lapis Lazuli, Ink Sac...). */
	static final Map<String, List<String>> COLOR_NAMES = Map.ofEntries(
			Map.entry("white", List.of("white", "bone meal", "wool")),
			Map.entry("orange", List.of("orange")),
			Map.entry("magenta", List.of("magenta")),
			Map.entry("light blue", List.of("light blue")),
			Map.entry("yellow", List.of("yellow", "dandelion yellow")),
			Map.entry("lime", List.of("lime")),
			Map.entry("pink", List.of("pink")),
			Map.entry("gray", List.of("gray", "grey")),
			Map.entry("light gray", List.of("light gray", "light grey", "silver")),
			Map.entry("cyan", List.of("cyan")),
			Map.entry("purple", List.of("purple")),
			Map.entry("blue", List.of("blue", "lapis lazuli")),
			Map.entry("brown", List.of("brown", "cocoa beans")),
			Map.entry("green", List.of("green", "cactus green")),
			Map.entry("red", List.of("red", "rose red")),
			Map.entry("black", List.of("black", "ink sac")));

	private TerminalSolver() {
	}

	public static TerminalSolution solve(String title, TerminalBoard board, Set<Integer> selected) {
		TerminalType type = TerminalType.of(title);
		if (type == null) return null;
		if (board.size() != type.slots()) return new TerminalSolution(type, List.of(), Status.UNRECOGNISED, "");
		Set<Integer> picked = selected == null ? Set.of() : selected;
		return switch (type) {
			case ORDER -> order(board);
			case PANES -> panes(board);
			case STARTS_WITH -> pick(type, board, picked, name -> name.startsWith(TerminalType.letter(title)), null);
			case COLORS -> colors(title, board, picked);
			case RUBIX -> rubix(board);
			case MELODY -> melody(board);
		};
	}

	// ---- Click in order --------------------------------------------------------------------------------------

	private static TerminalSolution order(TerminalBoard board) {
		List<TerminalBoard.Item> red = new ArrayList<>();
		List<Integer> redSlots = new ArrayList<>();
		int lime = 0;
		// Wider than the ten panes the GUI shows, in case a server lays them out seven wide.
		for (int slot : area(1, 2, 1, 7)) {
			TerminalBoard.Item item = board.get(slot);
			if (item.is("red_stained_glass_pane")) {
				red.add(item);
				redSlots.add(slot);
			} else if (item.is("lime_stained_glass_pane")) {
				lime++;
			}
		}
		if (red.isEmpty()) return lime > 0 ? done(TerminalType.ORDER) : TerminalSolution.waiting(TerminalType.ORDER);
		Set<Integer> numbers = new HashSet<>();
		for (TerminalBoard.Item item : red) {
			// Two panes with the same number means the chest is mid-update; wait for the next packet.
			if (!numbers.add(item.count())) return TerminalSolution.waiting(TerminalType.ORDER);
		}
		List<Integer> order = new ArrayList<>(redSlots);
		order.sort(Comparator.comparingInt(slot -> board.get(slot).count()));
		List<Step> steps = order.stream().map(slot -> new Step(slot, 1, false)).toList();
		return new TerminalSolution(TerminalType.ORDER, steps, Status.NEXT, Integer.toString(board.get(order.getFirst()).count()));
	}

	// ---- Correct all the panes -------------------------------------------------------------------------------

	private static TerminalSolution panes(TerminalBoard board) {
		List<Step> steps = new ArrayList<>();
		boolean any = false;
		for (int slot : grid(TerminalType.PANES)) {
			TerminalBoard.Item item = board.get(slot);
			if (item.is("red_stained_glass_pane")) {
				steps.add(new Step(slot, 1, false));
				any = true;
			} else if (item.is("lime_stained_glass_pane")) {
				any = true;
			}
		}
		if (!any) return TerminalSolution.waiting(TerminalType.PANES);
		return steps.isEmpty() ? done(TerminalType.PANES) : new TerminalSolution(TerminalType.PANES, steps, Status.MAKE_GREEN, "");
	}

	// ---- What starts with / Select all the colour ------------------------------------------------------------

	private static TerminalSolution colors(String title, TerminalBoard board, Set<Integer> picked) {
		String color = TerminalType.color(title);
		if ("silver".equals(color)) color = "light gray";
		if ("grey".equals(color)) color = "gray";
		if ("light grey".equals(color)) color = "light gray";
		List<String> names = COLOR_NAMES.get(color);
		if (names == null) return new TerminalSolution(TerminalType.COLORS, List.of(), Status.UNRECOGNISED, "");
		String idPrefix = color.replace(' ', '_') + "_";
		return pick(TerminalType.COLORS, board, picked, name -> names.stream().anyMatch(name::startsWith), idPrefix);
	}

	private static TerminalSolution pick(TerminalType type, TerminalBoard board, Set<Integer> picked,
			java.util.function.Predicate<String> nameMatches, String idPrefix) {
		List<Step> steps = new ArrayList<>();
		int items = 0;
		for (int slot : grid(type)) {
			TerminalBoard.Item item = board.get(slot);
			String name = item.name().toLowerCase(Locale.ROOT);
			// Filler panes are named " " (blank); everything else in the grid is a choice.
			if (item.isEmpty() || name.isBlank()) continue;
			items++;
			if (picked.contains(slot)) continue;
			if (item.glint() && !ALWAYS_GLINT.contains(item.id())) continue;
			boolean match = nameMatches.test(name) || (idPrefix != null && item.id().startsWith(idPrefix));
			if (match) steps.add(new Step(slot, 1, false));
		}
		if (items == 0) return TerminalSolution.waiting(type);
		return steps.isEmpty() ? done(type) : new TerminalSolution(type, steps, Status.SELECT, "");
	}

	// ---- Change all to same colour ---------------------------------------------------------------------------

	private static TerminalSolution rubix(TerminalBoard board) {
		int[] colors = new int[RUBIX_SLOTS.length];
		for (int i = 0; i < RUBIX_SLOTS.length; i++) {
			colors[i] = cycleIndex(board.get(RUBIX_SLOTS[i]));
			if (colors[i] < 0) return TerminalSolution.waiting(TerminalType.RUBIX);
		}
		int best = 0;
		int bestCost = Integer.MAX_VALUE;
		for (int target = 0; target < CYCLE.size(); target++) {
			int cost = 0;
			for (int c : colors) cost += Math.abs(turns(c, target));
			if (cost < bestCost) {
				bestCost = cost;
				best = target;
			}
		}
		List<Step> steps = new ArrayList<>();
		for (int i = 0; i < RUBIX_SLOTS.length; i++) {
			int t = turns(colors[i], best);
			if (t != 0) steps.add(new Step(RUBIX_SLOTS[i], Math.abs(t), t < 0));
		}
		return steps.isEmpty() ? done(TerminalType.RUBIX)
				: new TerminalSolution(TerminalType.RUBIX, steps, Status.SAME_COLOR, CYCLE.get(best));
	}

	/** Position in {@link #CYCLE}, or -1. Lime counts as green: some servers use lime panes for that step. */
	static int cycleIndex(TerminalBoard.Item item) {
		if (item.count() != 1 || !item.id().endsWith("_stained_glass_pane")) return -1;
		String color = item.id().substring(0, item.id().length() - "_stained_glass_pane".length());
		if (color.equals("lime")) color = "green";
		return CYCLE.indexOf(color);
	}

	/** Shortest number of steps from one colour to another: positive is left clicks, negative right clicks. */
	static int turns(int from, int to) {
		int forward = Math.floorMod(to - from, CYCLE.size());
		return forward <= CYCLE.size() / 2 ? forward : forward - CYCLE.size();
	}

	// ---- Click the button on time ----------------------------------------------------------------------------

	/**
	 * Rows with a terracotta button in column 7 are tracks; a magenta pane in any other row marks the target column.
	 * The live track is the one whose button is lime, or failing that the only track with a single lime pane moving
	 * along it. The answer is that track's button, once the moving pane sits in the marked column.
	 */
	private static TerminalSolution melody(TerminalBoard board) {
		List<Integer> tracks = new ArrayList<>();
		for (int row = 0; row < TerminalType.MELODY.rows; row++) {
			if (board.get(row, 7).id().endsWith("_terracotta")) tracks.add(row);
		}
		int marker = -1;
		for (int row = 0; row < TerminalType.MELODY.rows; row++) {
			if (tracks.contains(row)) continue;
			for (int column = 0; column < 7; column++) {
				if (!board.get(row, column).is("magenta_stained_glass_pane")) continue;
				if (marker >= 0 && marker != column) return TerminalSolution.waiting(TerminalType.MELODY);
				marker = column;
			}
		}
		if (tracks.isEmpty()) return TerminalSolution.waiting(TerminalType.MELODY);

		int live = -1;
		for (int row : tracks) {
			if (board.get(row, 7).is("lime_terracotta")) {
				if (live >= 0) {
					live = -2;
					break;
				}
				live = row;
			}
		}
		if (live < 0) {
			live = -1;
			for (int row : tracks) {
				int moving = movingColumn(board, row);
				if (moving < 0) continue;
				if (live >= 0) return TerminalSolution.waiting(TerminalType.MELODY);
				live = row;
			}
		}
		if (live < 0) return done(TerminalType.MELODY);
		int moving = movingColumn(board, live);
		if (moving < 0 || marker < 0) return TerminalSolution.waiting(TerminalType.MELODY);
		// The arg carries the moving pane's slot, so the session can time how long it stays in each column.
		String mover = Integer.toString(live * 9 + moving);
		if (moving != marker) return new TerminalSolution(TerminalType.MELODY, List.of(), Status.WAIT_ALIGN, mover);
		return new TerminalSolution(TerminalType.MELODY, List.of(new Step(live * 9 + 7, 1, false)), Status.CLICK_NOW, mover);
	}

	/** The column of the single lime pane in a track's columns 0..6 that isn't fully lime yet, or -1. */
	private static int movingColumn(TerminalBoard board, int row) {
		int found = -1;
		int panes = 0;
		int lime = 0;
		for (int column = 0; column < 7; column++) {
			TerminalBoard.Item item = board.get(row, column);
			if (!item.id().endsWith("_stained_glass_pane") || item.name().isBlank() && item.is("black_stained_glass_pane")) continue;
			panes++;
			if (item.is("lime_stained_glass_pane")) {
				lime++;
				found = column;
			}
		}
		return lime == 1 && panes > 1 ? found : -1;
	}

	// ---- Shared ----------------------------------------------------------------------------------------------

	static int[] area(int firstRow, int rows, int firstColumn, int columns) {
		int[] out = new int[rows * columns];
		int i = 0;
		for (int row = firstRow; row < firstRow + rows; row++) {
			for (int column = firstColumn; column < firstColumn + columns; column++) out[i++] = row * 9 + column;
		}
		return out;
	}

	/** The slots of a terminal's puzzle area, row by row. */
	static int[] grid(TerminalType type) {
		int[] out = new int[type.gridRows * type.gridColumns];
		int i = 0;
		for (int row = type.gridRow; row < type.gridRow + type.gridRows; row++) {
			for (int column = type.gridColumn; column < type.gridColumn + type.gridColumns; column++) out[i++] = row * 9 + column;
		}
		return out;
	}

	private static TerminalSolution done(TerminalType type) {
		return new TerminalSolution(type, List.of(), Status.DONE, "");
	}
}
