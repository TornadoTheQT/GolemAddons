package com.golemaddons.terminals;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.mixin.AbstractContainerScreenAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.lwjgl.glfw.GLFW;

/**
 * Terminal solvers on the client: recognises a terminal chest, draws the compact GUI or highlights, turns clicks
 * into correct ones (and refuses wrong ones), and runs hover click.
 *
 * <p>Every click is a normal click on a slot, sent the way vanilla sends one, but without vanilla's local guess at
 * the result. That guess lifts the pane onto the cursor until the server answers, which made panes flicker and could
 * drop a carried pane into another slot.
 */
public final class TerminalFeature {
	private static volatile GolemConfig.Terminals settings = new GolemConfig.Terminals();
	private static TerminalSession session;
	private static AbstractContainerScreen<?> sessionScreen;

	private TerminalFeature() {
	}

	public static void register(ConfigManager config) {
		config.addListener(c -> settings = c.terminals.copy());
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?> container && container.getMenu() instanceof ChestMenu) hook(container);
		});
		TerminalPractice.registerCommand();
	}

	public static GolemConfig.Terminals settings() {
		return settings;
	}

	private static void hook(AbstractContainerScreen<?> screen) {
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> onClick(screen, event));
		// A terminal has no use for a release or a drag; vanilla would place a carried item on release.
		ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> type(screen) == null);
		ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> type(screen) == null);
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
			if (type(screen) == null || event.key() == GLFW.GLFW_KEY_ESCAPE) return true;
			if (Minecraft.getInstance().options.keyInventory.matches(event)) {
				screen.onClose();
				return false;
			}
			// No drop, swap or hotbar keys on the hidden chest.
			return false;
		});
		ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> afterExtract(screen, g, mx, my));
		ScreenEvents.remove(screen).register(s -> {
			if (s == sessionScreen) {
				session = null;
				sessionScreen = null;
			}
		});
	}

	/** The terminal this screen shows, or null when it isn't one (or the solvers are off). */
	public static TerminalType type(AbstractContainerScreen<?> screen) {
		GolemConfig.Terminals s = settings;
		if (s == null || !s.enabled || !(screen.getMenu() instanceof ChestMenu menu)) return null;
		TerminalType type = TerminalType.of(screen.getTitle().getString());
		if (type == null || menu.getContainer().getContainerSize() != type.slots()) return null;
		Minecraft mc = Minecraft.getInstance();
		boolean practice = screen instanceof TerminalPractice.PracticeScreen;
		if (mc.player == null || mc.screen != screen || (!practice && mc.player.containerMenu != menu)) return null;
		return type;
	}

	/** True when the compact GUI replaces this chest; the mixins then skip vanilla's drawing. */
	public static boolean customGui(Screen screen) {
		return screen instanceof AbstractContainerScreen<?> c && settings.customGui && type(c) != null;
	}

	private static TerminalSession session(AbstractContainerScreen<?> screen) {
		int id = screen.getMenu().containerId;
		String title = screen.getTitle().getString();
		if (session == null || sessionScreen != screen || session.containerId() != id || !session.title().equals(title)) {
			session = new TerminalSession(id, title, Util.getMillis());
			sessionScreen = screen;
		}
		return session;
	}

	private static TerminalBoard board(AbstractContainerScreen<?> screen, TerminalType type) {
		return TerminalStacks.board(screen.getMenu(), type.slots());
	}

	private static TerminalLayout layout(AbstractContainerScreen<?> screen, TerminalType type) {
		GolemConfig.Terminals s = settings;
		return TerminalLayout.of(type, 0, 0, screen.width, screen.height, s.scale, s.gap);
	}

	/** The chest slot under the cursor, in whichever view is showing; -1 for none. */
	private static int slotUnder(AbstractContainerScreen<?> screen, TerminalType type, double mx, double my) {
		if (settings.customGui) return layout(screen, type).slotAt(mx, my);
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
		for (int i = 0; i < type.slots(); i++) {
			var slot = screen.getMenu().getSlot(i);
			int x = a.golemaddons$getLeftPos() + slot.x;
			int y = a.golemaddons$getTopPos() + slot.y;
			if (mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17) return i;
		}
		return -1;
	}

	private static boolean onClick(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
		TerminalType type = type(screen);
		if (type == null) return true;
		int slot = slotUnder(screen, type, event.x(), event.y());
		// Normal chest view: clicks outside the chest (your own inventory) behave as usual.
		if (slot < 0) return !settings.customGui;
		if (event.button() > 1) return false;
		long now = Util.getMillis();
		TerminalSession s = session(screen);
		TerminalSolution view = s.view(board(screen, type), now);
		Boolean button = TerminalSession.buttonFor(view, slot);
		if (button != null) {
			// Same Color: either button turns the pane the short way round.
			send(screen, s, slot, button, now);
		} else if (settings.blockWrongClicks) {
			s.blocked(slot, now);
		} else {
			send(screen, s, slot, event.button() == 1, now);
		}
		return false;
	}

	private static void afterExtract(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, int mx, int my) {
		TerminalType type = type(screen);
		if (type == null) return;
		GolemConfig.Terminals cfg = settings;
		long now = Util.getMillis();
		TerminalSession s = session(screen);
		TerminalBoard board = board(screen, type);
		TerminalSolution view = s.view(board, now);
		int hovered = slotUnder(screen, type, mx, my);

		TerminalSession.HoverTiming timing = new TerminalSession.HoverTiming(cfg.firstClickDelayMs, cfg.hoverDelayMs, cfg.clickDelayMs);
		if (cfg.autoTerms) {
			TerminalSolution.Step step = s.autoClick(view, now, timing);
			if (step != null) {
				send(screen, s, step.slot(), step.right(), now);
				view = s.view(board, now);
			}
		} else if (cfg.hoverClick) {
			Boolean button = s.hoverClick(view, hovered, now, timing);
			if (button != null) {
				send(screen, s, hovered, button, now);
				view = s.view(board, now);
			}
		}

		TerminalRenderer.Frame frame = TerminalRenderer.Frame.of(screen.getTitle().getString(), board,
				i -> screen.getMenu().getSlot(i).getItem(), view, hovered, s.blockedSlot(), now - s.blockedAt(), cfg);
		var font = Minecraft.getInstance().font;
		if (cfg.customGui) {
			TerminalRenderer.drawCompact(g, font, layout(screen, type), frame, cfg.colors);
		} else {
			AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
			TerminalRenderer.drawHighlights(g, font, a.golemaddons$getLeftPos(), a.golemaddons$getTopPos(), screen.getMenu(), frame, cfg.colors);
		}
		if (screen instanceof TerminalPractice.PracticeScreen practice) practice.practice().drawOverlay(g, font, screen.width, screen.height);
	}

	private static void send(AbstractContainerScreen<?> screen, TerminalSession s, int slot, boolean right, long now) {
		s.sent(slot, right, now);
		if (screen instanceof TerminalPractice.PracticeScreen practice) {
			practice.practice().click(slot, right, now);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		var connection = mc.getConnection();
		if (connection == null) return;
		var menu = screen.getMenu();
		connection.send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), (short) slot, (byte) (right ? 1 : 0),
				ContainerInput.PICKUP, new Int2ObjectOpenHashMap<>(), HashedStack.create(menu.getCarried(), connection.decoratedHashOpsGenenerator())));
	}

	/** For the in-game test: GUI coordinates of the middle of the next tile to click, or null when there is none yet. */
	public static double[] nextTarget() {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return null;
		TerminalType type = type(screen);
		if (type == null) return null;
		TerminalSolution view = session(screen).view(board(screen, type), Util.getMillis());
		if (view == null || view.steps().isEmpty()) return null;
		int slot = view.steps().getFirst().slot();
		if (settings.customGui) {
			TerminalLayout l = layout(screen, type);
			return new double[] {l.cellX(slot) + l.cell() / 2.0, l.cellY(slot) + l.cell() / 2.0};
		}
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
		var s = screen.getMenu().getSlot(slot);
		return new double[] {a.golemaddons$getLeftPos() + s.x + 8, a.golemaddons$getTopPos() + s.y + 8};
	}

	/** The server sent one slot of the open container (from the packet listener mixin). */
	public static void serverSlot(int containerId, int slot) {
		if (session != null && !(sessionScreen instanceof TerminalPractice.PracticeScreen) && session.containerId() == containerId) {
			session.serverSlot(slot, Util.getMillis());
		}
	}

	/** The server sent the whole open container. */
	public static void serverContent(int containerId) {
		if (session != null && !(sessionScreen instanceof TerminalPractice.PracticeScreen) && session.containerId() == containerId) {
			session.serverContent();
		}
	}

	/** The practice terminal answered a click: the same bookkeeping as a server slot update. */
	static void practiceSlot(AbstractContainerScreen<?> screen, int slot) {
		if (session != null && sessionScreen == screen) session.serverSlot(slot, Util.getMillis());
	}
}
