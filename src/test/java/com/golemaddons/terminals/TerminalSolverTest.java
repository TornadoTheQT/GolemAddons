package com.golemaddons.terminals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.golemaddons.terminals.TerminalBoard.Item;
import com.golemaddons.terminals.TerminalSolution.Status;
import com.golemaddons.terminals.TerminalSolution.Step;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TerminalSolverTest {
	/** Every dyed item, named the way the game names them ("Light Blue Wool"). */
	static List<TerminalPuzzle.PoolItem> pool() {
		List<TerminalPuzzle.PoolItem> pool = new ArrayList<>();
		for (String color : TerminalPuzzle.DYE_COLORS) {
			for (String family : TerminalPuzzle.COLOR_FAMILIES) {
				String id = color + "_" + family;
				StringBuilder name = new StringBuilder();
				for (String word : id.split("_")) name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
				pool.add(new TerminalPuzzle.PoolItem(id, name.toString()));
			}
		}
		for (String extra : List.of("Nether Star", "Netherite Ingot", "Name Tag", "Note Block", "Nautilus Shell", "Nether Brick", "Nether Wart",
				"Apple", "Arrow", "Anvil", "Axolotl Bucket", "Amethyst Shard", "Acacia Log", "Allium", "Andesite")) {
			pool.add(new TerminalPuzzle.PoolItem(extra.toLowerCase(Locale.ROOT).replace(' ', '_'), extra));
		}
		return pool;
	}

	/**
	 * Follows the solver on a practice terminal the way the GUI does: always the first correct step, the server
	 * confirming each click. It must finish every board without a single wrong click.
	 */
	@ParameterizedTest
	@EnumSource(TerminalType.class)
	void followingTheSolverFinishesEveryBoardWithoutMistakes(TerminalType type) {
		for (int seed = 0; seed < 300; seed++) {
			TerminalPuzzle puzzle = TerminalPuzzle.create(type, new Random(seed), pool());
			assertEquals(type, TerminalType.of(puzzle.title()), puzzle.title());
			Set<Integer> confirmed = new HashSet<>();
			long now = 0;
			for (int guard = 0; !puzzle.solved(); guard++) {
				assertTrue(guard < 2000, type + " seed " + seed + " never finished");
				TerminalSolution s = TerminalSolver.solve(puzzle.title(), puzzle.board(), confirmed);
				assertNotNull(s);
				assertTrue(s.ready(), type + " seed " + seed + ": " + s.status());
				if (s.steps().isEmpty()) {
					assertEquals(TerminalType.MELODY, type, "only Melody may have nothing to click before it is done");
					now += 37;
					puzzle.advance(now);
					continue;
				}
				Step step = s.steps().getFirst();
				assertTrue(puzzle.click(step.slot(), step.right()), type + " seed " + seed + " wrong click on " + step.slot());
				confirmed.add(step.slot());
			}
			assertEquals(0, puzzle.misclicks());
			assertEquals(Status.DONE, TerminalSolver.solve(puzzle.title(), puzzle.board(), confirmed).status(), type + " seed " + seed);
		}
	}

	/**
	 * Clicking as fast as hover click allows, with every answer arriving a ping later: the pending clicks taken off
	 * the hints must never lead to a wrong or duplicate click.
	 */
	@ParameterizedTest
	@EnumSource(value = TerminalType.class, names = "MELODY", mode = EnumSource.Mode.EXCLUDE)
	void fastClicksWithPingNeverDoubleClick(TerminalType type) {
		for (int seed = 0; seed < 200; seed++) {
			TerminalPuzzle puzzle = TerminalPuzzle.create(type, new Random(seed), pool());
			TerminalSession session = new TerminalSession(5, puzzle.title(), 0);
			record InFlight(int slot, boolean right, long arrives) {
			}
			Deque<InFlight> wire = new ArrayDeque<>();
			long ping = 30 + seed % 170;
			for (long now = 0; !puzzle.solved() || !wire.isEmpty(); now += 10) {
				assertTrue(now < 60_000, type + " seed " + seed + " never finished");
				while (!wire.isEmpty() && wire.peek().arrives() <= now) {
					InFlight click = wire.poll();
					assertTrue(puzzle.click(click.slot(), click.right()), type + " seed " + seed + " wrong click on " + click.slot());
					session.serverSlot(click.slot(), now);
				}
				TerminalSolution view = session.view(puzzle.board(), now);
				if (view.steps().isEmpty()) continue;
				Step next = view.steps().getFirst();
				// One click every 20 ms, far quicker than a round trip.
				if (now % 20 == 0) {
					session.sent(next.slot(), next.right(), now);
					wire.add(new InFlight(next.slot(), next.right(), now + ping));
				}
			}
			assertEquals(0, puzzle.misclicks(), type + " seed " + seed);
		}
	}

	/** Melody is judged when the click arrives: the session only offers a click that will still land in time. */
	@Test
	void melodyClicksArriveWhileThePaneIsStillLinedUp() {
		for (int seed = 0; seed < 150; seed++) {
			TerminalSession.resetPing();
			TerminalPuzzle puzzle = TerminalPuzzle.create(TerminalType.MELODY, new Random(seed), pool());
			TerminalSession session = new TerminalSession(5, puzzle.title(), 0);
			long ping = 30 + seed % 121;
			long arrives = -1;
			for (long now = 0; !puzzle.solved(); now += 10) {
				assertTrue(now < 60_000, "seed " + seed + " never finished");
				puzzle.advance(now);
				if (arrives >= 0 && now >= arrives) {
					assertTrue(puzzle.click(4 * 9 + 7 - (4 - liveRow(puzzle)) * 9, false), "seed " + seed + " ping " + ping + " arrived too late");
					session.serverSlot(0, now);
					arrives = -1;
				}
				TerminalSolution view = session.view(puzzle.board(), now);
				if (arrives < 0 && !view.steps().isEmpty()) {
					session.sent(view.steps().getFirst().slot(), false, now);
					arrives = now + ping;
				}
			}
			assertEquals(0, puzzle.misclicks());
		}
	}

	/** The row whose button is lit (the live track of a practice Melody). */
	private static int liveRow(TerminalPuzzle puzzle) {
		for (int row = 1; row <= 4; row++) {
			if (puzzle.get(row * 9 + 7).is("lime_terracotta")) return row;
		}
		throw new AssertionError("no live row");
	}

	@Test
	void melodyWaitsForTheServerAfterAClick() {
		TerminalPuzzle puzzle = TerminalPuzzle.create(TerminalType.MELODY, new Random(3), pool());
		TerminalSession session = new TerminalSession(1, puzzle.title(), 0);
		long now = 0;
		TerminalSolution view = session.view(puzzle.board(), now);
		while (view.steps().isEmpty()) {
			now += 10;
			puzzle.advance(now);
			view = session.view(puzzle.board(), now);
		}
		session.sent(view.steps().getFirst().slot(), false, now);
		assertTrue(session.view(puzzle.board(), now).steps().isEmpty(), "a second click before the answer would be judged too");
		session.serverSlot(0, now);
		assertFalse(session.view(puzzle.board(), now).steps().isEmpty());
	}

	@Test
	void anUnansweredClickComesBack() {
		TerminalPuzzle puzzle = TerminalPuzzle.create(TerminalType.PANES, new Random(1), pool());
		TerminalSession session = new TerminalSession(1, puzzle.title(), 0);
		Step first = session.view(puzzle.board(), 0).steps().getFirst();
		session.sent(first.slot(), false, 0);
		assertNull(session.view(puzzle.board(), 10).stepAt(first.slot()));
		assertNotNull(session.view(puzzle.board(), TerminalSession.ANSWER_TIMEOUT_MS + 1).stepAt(first.slot()));
	}

	@Test
	void hoverClickFollowsItsTiming() {
		TerminalPuzzle puzzle = TerminalPuzzle.create(TerminalType.PANES, new Random(2), pool());
		TerminalSession session = new TerminalSession(1, puzzle.title(), 0);
		TerminalSession.HoverTiming timing = new TerminalSession.HoverTiming(300, 50, 100);
		int slot = session.view(puzzle.board(), 0).steps().getFirst().slot();
		assertNull(session.hoverClick(session.view(puzzle.board(), 0), slot, 0, timing), "first-click delay");
		assertNull(session.hoverClick(session.view(puzzle.board(), 250), slot, 250, timing), "still inside the first-click delay");
		assertEquals(Boolean.FALSE, session.hoverClick(session.view(puzzle.board(), 350), slot, 350, timing));
		session.sent(slot, false, 350);
		int other = session.view(puzzle.board(), 360).steps().getFirst().slot();
		assertNull(session.hoverClick(session.view(puzzle.board(), 360), other, 360, timing), "moved: hover delay starts again");
		assertNull(session.hoverClick(session.view(puzzle.board(), 420), other, 420, timing), "click delay");
		assertEquals(Boolean.FALSE, session.hoverClick(session.view(puzzle.board(), 450), other, 450, timing));
		assertNull(session.hoverClick(session.view(puzzle.board(), 600), 0, 600, timing), "filler is never clicked");
	}

	@Test
	void autoTermsSolveEveryTerminalAtTheSetPace() {
		TerminalSession.HoverTiming timing = new TerminalSession.HoverTiming(300, 0, 120);
		for (TerminalType type : TerminalType.values()) {
			for (int seed = 0; seed < 50; seed++) {
				TerminalSession.resetPing();
				TerminalPuzzle puzzle = TerminalPuzzle.create(type, new Random(seed), pool());
				TerminalSession session = new TerminalSession(1, puzzle.title(), 0);
				record InFlight(int slot, boolean right, long arrives) {
				}
				Deque<InFlight> wire = new ArrayDeque<>();
				long lastClick = Long.MIN_VALUE / 2;
				for (long now = 0; !puzzle.solved() || !wire.isEmpty(); now += 10) {
					assertTrue(now < 60_000, type + " seed " + seed);
					puzzle.advance(now);
					while (!wire.isEmpty() && wire.peek().arrives() <= now) {
						InFlight c = wire.poll();
						assertTrue(puzzle.click(c.slot(), c.right()), type + " seed " + seed);
						session.serverSlot(c.slot(), now);
					}
					Step step = session.autoClick(session.view(puzzle.board(), now), now, timing);
					if (step == null) continue;
					assertTrue(now >= 300, "clicked inside the first-click delay");
					assertTrue(now - lastClick >= 120, "clicked faster than the click delay");
					lastClick = now;
					session.sent(step.slot(), step.right(), now);
					wire.add(new InFlight(step.slot(), step.right(), now + 100));
				}
				assertEquals(0, puzzle.misclicks(), type + " seed " + seed);
			}
		}
	}

	@Test
	void orderOnlyAcceptsTheNextNumber() {
		TerminalPuzzle puzzle = TerminalPuzzle.create(TerminalType.ORDER, new Random(4), pool());
		TerminalSolution s = TerminalSolver.solve(puzzle.title(), puzzle.board(), Set.of());
		assertEquals("1", s.arg());
		assertTrue(TerminalSession.correct(s, s.steps().get(0).slot(), false));
		assertFalse(TerminalSession.correct(s, s.steps().get(1).slot(), false));
		assertEquals(1, puzzle.board().get(s.steps().get(0).slot()).count());
		assertEquals(10, s.steps().size());
	}

	@Test
	void orderWaitsWhileTwoPanesShareANumber() {
		Item[] slots = filler(TerminalType.ORDER);
		slots[10] = pane("red", 1);
		slots[11] = pane("red", 1);
		assertEquals(Status.WAITING, TerminalSolver.solve("Click in order!", new TerminalBoard(slots), Set.of()).status());
	}

	@Test
	void rubixPicksTheFewestClicksAndTheRightButton() {
		Item[] slots = filler(TerminalType.RUBIX);
		String[] colors = {"red", "red", "red", "red", "blue", "orange", "red", "red", "lime"};
		for (int i = 0; i < 9; i++) slots[TerminalSolver.RUBIX_SLOTS[i]] = pane(colors[i], 1);
		TerminalSolution s = TerminalSolver.solve("Change all to same color!", new TerminalBoard(slots), Set.of());
		assertEquals("red", s.arg());
		assertEquals(new Step(22, 1, false), s.stepAt(22), "blue is one left click from red");
		assertEquals(new Step(23, 1, true), s.stepAt(23), "orange is one right click from red");
		assertEquals(new Step(32, 2, false), s.stepAt(32), "lime counts as green: two left clicks");
		assertTrue(TerminalSession.correct(s, 23, true));
		assertFalse(TerminalSession.correct(s, 23, false), "the wrong button would turn it further away");
	}

	@Test
	void startsWithSkipsPickedItemsButNotItemsThatAlwaysGlint() {
		Item[] slots = filler(TerminalType.STARTS_WITH);
		slots[10] = new Item("nether_star", "Nether Star", 1, true);
		slots[11] = new Item("netherite_ingot", "Netherite Ingot", 1, true);
		slots[12] = new Item("name_tag", "Name Tag", 1, false);
		slots[13] = new Item("apple", "Apple", 1, false);
		String title = "§aWhat starts with: ‘N’?";
		TerminalSolution s = TerminalSolver.solve(title, new TerminalBoard(slots), Set.of());
		assertEquals(List.of(10, 12), s.steps().stream().map(Step::slot).toList());
		TerminalSolution after = TerminalSolver.solve(title, new TerminalBoard(slots), Set.of(10));
		assertEquals(List.of(12), after.steps().stream().map(Step::slot).toList());
	}

	@Test
	void colorsUnderstandsOldNamesAndDoesNotConfuseLightBlueWithBlue() {
		Item[] slots = filler(TerminalType.COLORS);
		slots[10] = new Item("lapis_lazuli", "Lapis Lazuli", 1, false);
		slots[11] = new Item("light_blue_wool", "Light Blue Wool", 1, false);
		slots[12] = new Item("blue_wool", "Blue Wool", 1, false);
		slots[13] = new Item("black_wool", "Black Wool", 1, false);
		TerminalSolution s = TerminalSolver.solve("Select all the BLUE items!", new TerminalBoard(slots), Set.of());
		assertEquals(List.of(10, 12), s.steps().stream().map(Step::slot).toList());
		TerminalSolution silver = TerminalSolver.solve("Select all the SILVER items!", new TerminalBoard(slots), Set.of());
		assertEquals(Status.DONE, silver.status());
	}

	@Test
	void melodyFindsTheTrackInOtherLayoutsToo() {
		// Three tracks, markers above and below, buttons in column 7 - the button shows red for every row.
		Item[] slots = filler(TerminalType.MELODY);
		slots[3] = pane("magenta", 1);
		slots[4 * 9 + 3] = pane("magenta", 1);
		for (int row = 1; row <= 3; row++) {
			for (int column = 1; column <= 5; column++) slots[row * 9 + column] = pane(row == 1 ? "lime" : row == 2 ? "red" : "white", 1);
			slots[row * 9 + 7] = new Item("red_terracotta", "Click!", 1, false);
		}
		slots[2 * 9 + 3] = pane("lime", 1);
		TerminalSolution s = TerminalSolver.solve("Click the button on time!", new TerminalBoard(slots), Set.of());
		assertEquals(Status.CLICK_NOW, s.status());
		assertEquals(2 * 9 + 7, s.steps().getFirst().slot());
		slots[2 * 9 + 3] = pane("red", 1);
		slots[2 * 9 + 4] = pane("lime", 1);
		assertEquals(Status.WAIT_ALIGN, TerminalSolver.solve("Click the button on time!", new TerminalBoard(slots), Set.of()).status());
	}

	@Test
	void titlesAreRecognisedAndOthersIgnored() {
		assertEquals(TerminalType.COLORS, TerminalType.of("Select all the LIGHT BLUE items!"));
		assertEquals("light blue", TerminalType.color("Select all the LIGHT BLUE items!"));
		assertEquals(TerminalType.STARTS_WITH, TerminalType.of("What starts with: 'Q'?"));
		assertNull(TerminalType.of("Large Chest"));
		assertNull(TerminalSolver.solve("Chest", new TerminalBoard(new Item[27]), Set.of()));
		assertEquals(Status.UNRECOGNISED, TerminalSolver.solve("Click in order!", new TerminalBoard(new Item[27]), Set.of()).status(),
				"wrong chest size");
	}

	@Test
	void layoutMapsEveryCellAndNoGap() {
		for (TerminalType type : TerminalType.values()) {
			for (int gap : new int[] {0, 2, 5}) {
				TerminalLayout l = TerminalLayout.of(type, 0, 0, 900, 500, 1.5f, gap);
				for (int slot : TerminalSolver.grid(type)) {
					int x = l.cellX(slot);
					int y = l.cellY(slot);
					assertEquals(slot, l.slotAt(x, y));
					assertEquals(slot, l.slotAt(x + l.cell() - 0.01, y + l.cell() - 0.01));
					if (gap > 0) {
						assertEquals(-1, l.slotAt(x + l.cell(), y));
						assertEquals(-1, l.slotAt(x, y + l.cell()));
					}
				}
				assertEquals(-1, l.slotAt(l.x() - 0.5, l.y()));
				assertEquals(-1, l.slotAt(l.x() + l.gridWidth(), l.y()));
				assertEquals(-1, l.slotAt(l.x(), l.y() + l.gridHeight()));
			}
		}
	}

	@Test
	void layoutShrinksToFitSmallWindows() {
		TerminalLayout l = TerminalLayout.of(TerminalType.MELODY, 0, 0, 320, 240, 3f, 8);
		assertTrue(l.panelW() <= 320 && l.panelH() <= 240, l.toString());
		assertTrue(l.panelX() >= 0 && l.panelY() >= 0, l.toString());
	}

	private static Item[] filler(TerminalType type) {
		Item[] slots = new Item[type.slots()];
		Arrays.fill(slots, new Item("black_stained_glass_pane", " ", 1, false));
		return slots;
	}

	private static Item pane(String color, int count) {
		return new Item(color + "_stained_glass_pane", color + " Stained Glass Pane", count, false);
	}
}
