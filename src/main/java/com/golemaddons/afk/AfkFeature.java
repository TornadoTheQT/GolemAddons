package com.golemaddons.afk;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

/**
 * The AFK helper. Running /afk starts a session: a HUD counts the dragons it rewards, and if the AFK menu closes
 * without you closing it, or the connection drops, it reconnects and runs /afk again. Closing the menu yourself
 * (Escape or the inventory key, or clicking something in it) ends the session, as does leaving the disconnect screen.
 */
public final class AfkFeature {
	private static final AfkFeature INSTANCE = new AfkFeature();
	/** A click in the menu this recently means the menu closing was the player's doing. */
	private static final long USER_ACTION_MS = 3000;
	private static final List<String> DRAGONS = List.of("Old Dragon", "Protector Dragon", "Strong Dragon", "Unstable Dragon",
			"Wise Dragon", "Young Dragon", "Superior Dragon");

	private volatile GolemConfig.Afk settings = new GolemConfig.Afk();
	private final AfkTracker tracker = new AfkTracker();
	private AfkSession session;
	private ServerData server;
	private long userActionAt = Long.MIN_VALUE / 2;
	private long menuSeenAt = Long.MIN_VALUE / 2;
	private boolean ignoreNextAfkCommand;

	private AfkFeature() {
	}

	public static AfkFeature instance() {
		return INSTANCE;
	}

	public void register(ConfigManager config) {
		config.addListener(c -> settings = c.afk.copy());
		ClientSendMessageEvents.COMMAND.register(command -> {
			if (!command.strip().equalsIgnoreCase("afk")) return;
			if (ignoreNextAfkCommand) {
				ignoreNextAfkCommand = false;
				return;
			}
			if (settings.enabled && !running()) start();
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> hook(screen));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(GolemAddonsClient.MOD_ID, "afk_tracker"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.screen == null && !mc.options.hideGui) drawHud(g, mc);
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(literal("gaafk")
				.then(literal("stop").executes(c -> {
					stop("golemaddons.afk.stopped.command");
					return 1;
				}))
				.then(literal("reset").executes(c -> {
					tracker.clear();
					message(Component.translatable("golemaddons.afk.reset"));
					return 1;
				}))
				.then(literal("status").executes(c -> {
					message(statusLine(Util.getMillis()));
					return 1;
				}))
				.then(literal("test")
						.then(literal("disconnect").executes(c -> test(this::testDisconnect)))
						.then(literal("lobby").executes(c -> test(this::testLobby)))
						.then(literal("reward").executes(c -> test(() -> testReward(null)))
								.then(argument("dragon", StringArgumentType.greedyString())
										.executes(c -> test(() -> testReward(StringArgumentType.getString(c, "dragon")))))))));
	}

	public boolean running() {
		return session != null && session.state() != AfkSession.State.STOPPED;
	}

	public AfkTracker tracker() {
		return tracker;
	}

	public AfkSession session() {
		return session;
	}

	private void start() {
		Minecraft mc = Minecraft.getInstance();
		long now = Util.getMillis();
		tracker.clear();
		session = new AfkSession(now);
		ServerData current = mc.getCurrentServer();
		server = current == null ? null : copy(current);
		message(Component.translatable(server == null ? "golemaddons.afk.started.local" : "golemaddons.afk.started").withStyle(ChatFormatting.GREEN));
	}

	private static ServerData copy(ServerData s) {
		ServerData c = new ServerData(s.name, s.ip, s.type());
		c.copyFrom(s);
		return c;
	}

	public void stop(String reasonKey) {
		if (!running()) return;
		session.stop();
		message(Component.translatable(reasonKey, tracker.total()).withStyle(ChatFormatting.YELLOW));
	}

	// ---- Each tick -------------------------------------------------------------------------------------------

	private void tick(Minecraft mc) {
		long now = Util.getMillis();
		boolean menuOpen = mc.screen instanceof AbstractContainerScreen<?> s && AfkTracker.isAfkMenu(s.getTitle().getString());
		if (menuOpen) {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
			if (now - menuSeenAt > 1500) tracker.menuOpened();
			menuSeenAt = now;
			if (running() || settings.hudAlways) {
				String got = tracker.observe(now, screen.getTitle().getString(), slots(screen));
				if (got != null && running()) message(Component.translatable("golemaddons.afk.reward", got, tracker.total()).withStyle(ChatFormatting.AQUA));
			}
		}
		if (!running()) return;

		GolemConfig.Afk s = settings;
		if (!s.enabled) {
			session.stop();
			return;
		}
		// The menu went away because of something the player did: they're awake, the session is over.
		if (!menuOpen && session.state() == AfkSession.State.AFKING && now - userActionAt < USER_ACTION_MS) {
			stop("golemaddons.afk.stopped.closed");
			return;
		}
		boolean inWorld = mc.level != null && mc.player != null && mc.getConnection() != null;
		boolean disconnected = mc.screen instanceof DisconnectedScreen;
		boolean connecting = mc.screen instanceof ConnectScreen;
		AfkSession.Timing timing = new AfkSession.Timing(s.reopenDelayS * 1000L, s.reconnectDelayS * 1000L, s.autoReopen,
				s.autoReconnect && server != null, s.hubCommand);
		AfkSession.State before = session.state();
		AfkSession.Action action = session.tick(now, new AfkSession.View(inWorld, menuOpen, disconnected, connecting), timing);
		if (session.state() == AfkSession.State.STOPPED && before != AfkSession.State.STOPPED) {
			message(Component.translatable("golemaddons.afk.stopped.left", tracker.total()).withStyle(ChatFormatting.YELLOW));
		}
		if (action instanceof AfkSession.Action.Command c && mc.getConnection() != null) {
			if (c.command().equalsIgnoreCase("afk")) ignoreNextAfkCommand = true;
			message(Component.translatable("golemaddons.afk.running", "/" + c.command()).withStyle(ChatFormatting.GRAY));
			mc.getConnection().sendCommand(c.command());
		} else if (action instanceof AfkSession.Action.Reconnect) {
			message(Component.translatable("golemaddons.afk.reconnecting", server.ip));
			ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(server.ip), server, false, null);
		}
	}

	private static List<AfkTracker.Slot> slots(AbstractContainerScreen<?> screen) {
		List<AfkTracker.Slot> out = new ArrayList<>();
		int size = screen.getMenu() instanceof ChestMenu chest ? chest.getContainer().getContainerSize() : screen.getMenu().slots.size();
		for (int i = 0; i < size && i < screen.getMenu().slots.size(); i++) {
			ItemStack stack = screen.getMenu().getSlot(i).getItem();
			if (stack.isEmpty()) continue;
			List<String> lore = new ArrayList<>();
			ItemLore itemLore = stack.get(DataComponents.LORE);
			if (itemLore != null) for (Component line : itemLore.lines()) lore.add(line.getString());
			out.add(new AfkTracker.Slot(stack.getHoverName().getString(), lore));
		}
		return out;
	}

	// ---- Screens ---------------------------------------------------------------------------------------------

	private void hook(Screen screen) {
		if (screen instanceof AbstractContainerScreen<?> menu && AfkTracker.isAfkMenu(menu.getTitle().getString())) {
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
				if (event.key() == GLFW.GLFW_KEY_ESCAPE || Minecraft.getInstance().options.keyInventory.matches(event)) {
					userActionAt = Util.getMillis();
				}
				return true;
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				userActionAt = Util.getMillis();
				return true;
			});
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> drawHud(g, Minecraft.getInstance()));
		} else if (screen instanceof DisconnectedScreen && running()) {
			Screens.getWidgets(screen).add(Button.builder(Component.translatable("golemaddons.afk.stop_button"),
					b -> stop("golemaddons.afk.stopped.command")).bounds(screen.width / 2 - 100, 8, 200, 20).build());
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
				if (!running()) return;
				g.centeredText(Minecraft.getInstance().font, statusLine(Util.getMillis()), screen.width / 2, 32, 0xFFE0B04A);
				drawHud(g, Minecraft.getInstance());
			});
		}
	}

	// ---- HUD -------------------------------------------------------------------------------------------------

	public void drawHud(GuiGraphicsExtractor g, Minecraft mc) {
		GolemConfig.Afk s = settings;
		if (!s.enabled || !s.showHud || (!running() && !s.hudAlways)) return;
		AfkHud.Model model = model(Util.getMillis());
		int[] size = AfkHud.size(mc.font, model);
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		int x = Math.round(s.hud.x * Math.max(0, sw - size[0] * s.hud.scale));
		int y = Math.round(s.hud.y * Math.max(0, sh - size[1] * s.hud.scale));
		AfkHud.draw(g, mc.font, x, y, s.hud.scale, model);
	}

	public AfkHud.Model model(long now) {
		AfkSession sess = session;
		boolean on = running();
		String elapsed = on ? AfkHud.elapsed(now - sess.startedAt()) : "";
		Component status;
		int color;
		if (!on) {
			status = Component.translatable("golemaddons.afk.status.off");
			color = 0xFF6A6A72;
		} else {
			long left = (sess.waitLeft(now) + 999) / 1000;
			switch (sess.state()) {
				case AFKING -> {
					status = Component.translatable("golemaddons.afk.status.afk");
					color = 0xFF55FF55;
				}
				case REOPENING -> {
					status = left > 0 ? Component.translatable("golemaddons.afk.status.reopen_in", left)
							: Component.translatable("golemaddons.afk.status.reopening");
					color = 0xFFE0B04A;
				}
				case WAITING_TO_RECONNECT -> {
					status = Component.translatable("golemaddons.afk.status.reconnect_in", left);
					color = 0xFFFF5555;
				}
				default -> {
					status = Component.translatable("golemaddons.afk.status.connecting");
					color = 0xFFE0B04A;
				}
			}
		}
		return new AfkHud.Model(elapsed, status, color, tracker.counts(), tracker.total(), on ? sess.reconnects() : 0);
	}

	private Component statusLine(long now) {
		AfkHud.Model m = model(now);
		return Component.translatable("golemaddons.afk.status.line", m.status(), m.total());
	}

	// ---- Test tools ------------------------------------------------------------------------------------------

	private int test(Runnable action) {
		if (!settings.testTools) {
			message(Component.translatable("golemaddons.afk.test.disabled").withStyle(ChatFormatting.RED));
			return 0;
		}
		Minecraft.getInstance().schedule(action);
		return 1;
	}

	/** Drops the connection the way a Wi-Fi cut does: the game shows "Disconnected" and the helper takes over. */
	public void testDisconnect() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return;
		mc.getConnection().getConnection().disconnect(Component.translatable("golemaddons.afk.test.disconnect_reason"));
	}

	/** Closes the AFK menu as a warp would, and really switches lobby with the hub command. */
	public void testLobby() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) return;
		if (mc.screen instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
		String hub = settings.hubCommand;
		if (!hub.isBlank()) mc.getConnection().sendCommand(hub);
	}

	public void testReward(String dragon) {
		String name = dragon == null || dragon.isBlank() ? DRAGONS.get((int) (Math.random() * DRAGONS.size())) : dragon.strip();
		tracker.count(name);
		message(Component.translatable("golemaddons.afk.reward", name, tracker.total()).withStyle(ChatFormatting.AQUA));
	}

	private static void message(Component text) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.getChat().addClientSystemMessage(Component.literal("[GolemAddons] ").withStyle(ChatFormatting.GRAY).append(text));
	}
}
