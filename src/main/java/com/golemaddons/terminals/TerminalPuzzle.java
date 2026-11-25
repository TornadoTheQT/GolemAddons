package com.golemaddons.terminals;

import com.golemaddons.terminals.TerminalBoard.Item;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A practice terminal that behaves like the real one: it lays out a random board and answers clicks the way the
 * server does. Pure (no Minecraft classes): {@code /gatestterms} shows it in a chest screen, and the tests use it to
 * check that following the solver always finishes a terminal without a single wrong click.
 */
public final class TerminalPuzzle {
	/** An item the generator may place: its id (no namespace) and display name. */
	public record PoolItem(String id, String name) {
	}

	static final List<String> DYE_COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
	static final List<String> COLOR_FAMILIES = List.of("wool", "concrete", "stained_glass", "terracotta", "dye", "carpet",
			"concrete_powder", "candle", "banner", "bed", "shulker_box", "glazed_terracotta");

	/** How long the Melody pane stays in each column. */
	public static final long MELODY_STEP_MS = 330;
	/** Melody has three tracks, rows 1-3. */
	static final int TRACKS = 3;
	private static final Item FILLER = new Item("black_stained_glass_pane", "", 1, false);

	private final TerminalType type;
	private final String title;
	private final Item[] slots;
	private final Random random;
	private int misclicks;
	private int clicks;
	// Click in order
	private int nextNumber = 1;
	// Melody
	private int liveRow;
	private int[] targets;
	private int mover;
	private int direction = 1;
	private long nextMove = -1;

	private TerminalPuzzle(TerminalType type, String title, Random random) {
		this.type = type;
		this.title = title;
		this.random = random;
		this.slots = new Item[type.slots()];
		java.util.Arrays.fill(slots, FILLER);
	}

	public static TerminalPuzzle create(TerminalType type, Random random, List<PoolItem> pool) {
		return switch (type) {
			case ORDER -> order(random);
			case PANES -> panes(random);
			case STARTS_WITH -> startsWith(random, pool);
			case COLORS -> colors(random, pool);
			case RUBIX -> rubix(random);
			case MELODY -> melody(random);
		};
	}

	public TerminalType type() {
		return type;
	}

	public String title() {
		return title;
	}

	public TerminalBoard board() {
		return new TerminalBoard(slots);
	}

	public Item get(int slot) {
		return slots[slot];
	}

	public int misclicks() {
		return misclicks;
	}

	public int clicks() {
		return clicks;
	}

	// ---- Generation ------------------------------------------------------------------------------------------

	private static TerminalPuzzle order(Random random) {
		TerminalPuzzle p = new TerminalPuzzle(TerminalType.ORDER, "Click in order!", random);
		List<Integer> grid = shuffled(TerminalSolver.grid(TerminalType.ORDER), random);
		for (int i = 0; i < grid.size(); i++) p.slots[grid.get(i)] = pane("red", i + 1);
		return p;
	}

	private static TerminalPuzzle panes(Random random) {
		TerminalPuzzle p = new TerminalPuzzle(TerminalType.PANES, "Correct all the panes!", random);
		// Fifteen panes, five by three in the middle, like the real one.
		List<Integer> cells = new ArrayList<>();
		for (int row = 1; row <= 3; row++) for (int column = 2; column <= 6; column++) cells.add(row * 9 + column);
		int red = 0;
		for (int slot : cells) {
			boolean isRed = random.nextInt(100) < 40;
			p.slots[slot] = pane(isRed ? "red" : "lime", 1);
			if (isRed) red++;
		}
		if (red < 3) {
			for (int i = 0; i < 3; i++) p.slots[cells.get(i * 5 + random.nextInt(5))] = pane("red", 1);
		}
		return p;
	}

	private static TerminalPuzzle startsWith(Random random, List<PoolItem> pool) {
		Map<Character, List<PoolItem>> byLetter = new HashMap<>();
		for (PoolItem item : pool) {
			String name = item.name().toLowerCase(Locale.ROOT);
			if (!name.isEmpty() && Character.isLetter(name.charAt(0))) byLetter.computeIfAbsent(name.charAt(0), c -> new ArrayList<>()).add(item);
		}
		List<Character> letters = new ArrayList<>(byLetter.keySet().stream().filter(c -> byLetter.get(c).size() >= 8).sorted().toList());
		if (letters.isEmpty()) throw new IllegalArgumentException("item pool too small");
		char letter = letters.get(random.nextInt(letters.size()));
		List<PoolItem> matches = new ArrayList<>(byLetter.get(letter));
		List<PoolItem> others = new ArrayList<>(pool.stream().filter(i -> !i.name().toLowerCase(Locale.ROOT).startsWith(String.valueOf(letter))).toList());
		Collections.shuffle(matches, random);
		Collections.shuffle(others, random);
		TerminalPuzzle p = new TerminalPuzzle(TerminalType.STARTS_WITH, "What starts with: '" + Character.toUpperCase(letter) + "'?", random);
		p.fillChoices(TerminalSolver.grid(TerminalType.STARTS_WITH), matches, others, 3 + random.nextInt(5));
		return p;
	}

	private static TerminalPuzzle colors(Random random, List<PoolItem> pool) {
		Map<String, PoolItem> byId = new HashMap<>();
		for (PoolItem item : pool) byId.put(item.id(), item);
		List<String> colors = new ArrayList<>(DYE_COLORS);
		Collections.shuffle(colors, random);
		for (String color : colors) {
			List<PoolItem> matches = new ArrayList<>();
			List<PoolItem> others = new ArrayList<>();
			for (String c : DYE_COLORS) {
				for (String family : COLOR_FAMILIES) {
					PoolItem item = byId.get(c + "_" + family);
					if (item != null) (c.equals(color) ? matches : others).add(item);
				}
			}
			if (matches.size() < 4) continue;
			Collections.shuffle(matches, random);
			Collections.shuffle(others, random);
			String shown = color.replace('_', ' ').toUpperCase(Locale.ROOT);
			TerminalPuzzle p = new TerminalPuzzle(TerminalType.COLORS, "Select all the " + shown + " items!", random);
			p.fillChoices(TerminalSolver.grid(TerminalType.COLORS), matches, others, 3 + random.nextInt(6));
			return p;
		}
		throw new IllegalArgumentException("item pool too small");
	}

	private void fillChoices(int[] grid, List<PoolItem> matches, List<PoolItem> others, int wanted) {
		int count = Math.min(wanted, matches.size());
		List<Integer> cells = shuffled(grid, random);
		int m = 0;
		int o = 0;
		for (int i = 0; i < cells.size(); i++) {
			PoolItem item = i < count ? matches.get(m++) : others.get(o++ % others.size());
			slots[cells.get(i)] = new Item(item.id(), item.name(), 1, false);
		}
	}

	private static TerminalPuzzle rubix(Random random) {
		TerminalPuzzle p = new TerminalPuzzle(TerminalType.RUBIX, "Change all to same color!", random);
		do {
			for (int slot : TerminalSolver.RUBIX_SLOTS) p.slots[slot] = pane(TerminalSolver.CYCLE.get(random.nextInt(5)), 1);
		} while (p.solved());
		return p;
	}

	private static TerminalPuzzle melody(Random random) {
		TerminalPuzzle p = new TerminalPuzzle(TerminalType.MELODY, "Click the button on time!", random);
		p.targets = new int[TRACKS];
		for (int i = 0; i < TRACKS; i++) p.targets[i] = 1 + random.nextInt(5);
		p.liveRow = 1;
		p.mover = 1;
		p.drawMelody();
		return p;
	}

	// ---- Clicks ----------------------------------------------------------------------------------------------

	/** Answers one click like the server. Returns true when it was a correct click. */
	public boolean click(int slot, boolean right) {
		if (slot < 0 || slot >= slots.length || solved()) return false;
		clicks++;
		boolean ok = switch (type) {
			case ORDER -> clickOrder(slot);
			case PANES -> clickPanes(slot);
			case STARTS_WITH, COLORS -> clickPick(slot);
			case RUBIX -> clickRubix(slot, right);
			case MELODY -> clickMelody(slot);
		};
		if (!ok) misclicks++;
		return ok;
	}

	private boolean clickOrder(int slot) {
		Item item = slots[slot];
		if (!item.is("red_stained_glass_pane") || item.count() != nextNumber) return false;
		slots[slot] = pane("lime", item.count());
		nextNumber++;
		return true;
	}

	private boolean clickPanes(int slot) {
		Item item = slots[slot];
		if (item.is("red_stained_glass_pane")) {
			slots[slot] = pane("lime", 1);
			return true;
		}
		if (item.is("lime_stained_glass_pane")) slots[slot] = pane("red", 1);
		return false;
	}

	private boolean clickPick(int slot) {
		Item item = slots[slot];
		if (item.name().isBlank() || item.glint() || !matches(item)) return false;
		slots[slot] = new Item(item.id(), item.name(), item.count(), true);
		return true;
	}

	private boolean matches(Item item) {
		String name = item.name().toLowerCase(Locale.ROOT);
		if (type == TerminalType.STARTS_WITH) return name.startsWith(TerminalType.letter(title));
		String color = TerminalType.color(title).replace(' ', '_');
		return item.id().startsWith(color + "_");
	}

	private boolean clickRubix(int slot, boolean right) {
		if (java.util.Arrays.stream(TerminalSolver.RUBIX_SLOTS).noneMatch(s -> s == slot)) return false;
		int color = TerminalSolver.cycleIndex(slots[slot]);
		int next = Math.floorMod(color + (right ? -1 : 1), TerminalSolver.CYCLE.size());
		slots[slot] = pane(TerminalSolver.CYCLE.get(next), 1);
		return true;
	}

	private boolean clickMelody(int slot) {
		if (slot != liveRow * 9 + 7 || mover != targets[liveRow - 1]) return false;
		liveRow++;
		mover = 1;
		direction = 1;
		nextMove = -1; // the new row starts its own beat
		drawMelody();
		return true;
	}

	/** Moves the Melody pane along; returns true when the board changed. Other terminals never change by themselves. */
	public boolean advance(long nowMs) {
		if (type != TerminalType.MELODY || solved()) return false;
		if (nextMove < 0) {
			nextMove = nowMs + MELODY_STEP_MS;
			return false;
		}
		boolean moved = false;
		while (nowMs >= nextMove) {
			if (mover + direction < 1 || mover + direction > 5) direction = -direction;
			mover += direction;
			nextMove += MELODY_STEP_MS;
			moved = true;
		}
		if (moved) drawMelody();
		return moved;
	}

	private void drawMelody() {
		java.util.Arrays.fill(slots, FILLER);
		boolean finished = liveRow > TRACKS;
		if (!finished) {
			// Marker above and below the three tracks.
			slots[targets[liveRow - 1]] = pane("magenta", 1);
			slots[(TRACKS + 1) * 9 + targets[liveRow - 1]] = pane("magenta", 1);
		}
		for (int row = 1; row <= TRACKS; row++) {
			for (int column = 1; column <= 5; column++) {
				String color = row < liveRow ? "lime" : row > liveRow ? "white" : column == mover ? "lime" : "red";
				// Unnamed, like on the real terminal: the GUI must not take them for background.
				slots[row * 9 + column] = new Item(color + "_stained_glass_pane", "", 1, false);
			}
			String button = row == liveRow ? "lime_terracotta" : row < liveRow ? "green_terracotta" : "red_terracotta";
			slots[row * 9 + 7] = new Item(button, "", 1, false);
		}
	}

	public boolean solved() {
		return switch (type) {
			case ORDER -> nextNumber > TerminalSolver.grid(TerminalType.ORDER).length; // ten panes
			case PANES -> java.util.Arrays.stream(slots).noneMatch(i -> i.is("red_stained_glass_pane"));
			case STARTS_WITH, COLORS -> java.util.Arrays.stream(slots).noneMatch(i -> !i.name().isBlank() && !i.glint() && matches(i));
			case RUBIX -> java.util.Arrays.stream(TerminalSolver.RUBIX_SLOTS).map(s -> TerminalSolver.cycleIndex(slots[s])).distinct().count() == 1;
			case MELODY -> liveRow > TRACKS;
		};
	}

	private static Item pane(String color, int count) {
		String name = color.substring(0, 1).toUpperCase(Locale.ROOT) + color.substring(1) + " Stained Glass Pane";
		return new Item(color + "_stained_glass_pane", name, count, false);
	}

	private static List<Integer> shuffled(int[] slots, Random random) {
		List<Integer> list = new ArrayList<>();
		for (int s : slots) list.add(s);
		Collections.shuffle(list, random);
		return list;
	}
}
