package com.golemaddons.gametest;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.partyfinder.PartyFinderFeature;
import com.golemaddons.terminals.TerminalFeature;
import com.golemaddons.terminals.TerminalPractice;
import com.golemaddons.terminals.TerminalPuzzle;
import com.golemaddons.terminals.TerminalType;
import com.golemaddons.ui.GolemHubScreen;
import com.golemaddons.ui.PartyFinderScreen;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Smoke test in a real client: opens the hub and every feature screen inside a world and takes a screenshot of each,
 * so layout problems (and crashes, e.g. in the player preview) show up without playing.
 */
public class GolemAddonsClientGameTest implements FabricClientGameTest {
	/** Each top-level category card and the feature tiles inside it (tiles that open a screen). */
	private static final String[][] CATEGORIES = {
			{"golemaddons.hub.selling", "golemaddons.hub.mining", "golemaddons.hub.dragon", "golemaddons.hub.settings"},
			{"golemaddons.hub.qol", "golemaddons.hub.autoclicker", "golemaddons.hub.camera", "golemaddons.hub.terminals"},
			{"golemaddons.hub.visuals", "golemaddons.hub.cosmetics", "golemaddons.hub.animations"}};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();
			context.setScreen(() -> new GolemHubScreen(null));
			context.waitTicks(30); // let the entrance animation finish
			context.takeScreenshot("golemaddons-hub");
			for (String[] category : CATEGORIES) {
				context.setScreen(() -> new GolemHubScreen(null));
				context.waitTicks(30);
				click(context, category[0]);
				context.waitTicks(30);
				context.takeScreenshot(category[0].replace('.', '-'));
				for (int i = 1; i < category.length; i++) {
					click(context, category[i]);
					context.waitTicks(10);
					context.takeScreenshot(category[i].replace('.', '-'));
					context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); // back to the category
					context.waitTicks(30);
				}
			}
			// Animation presets tab.
			context.setScreen(() -> new GolemHubScreen(null));
			context.waitTicks(30);
			click(context, "golemaddons.hub.visuals");
			context.waitTicks(30);
			click(context, "golemaddons.hub.animations");
			context.waitTicks(10);
			click(context, "golemaddons.animations.tabs", 0.5); // Options, the middle of the three tabs
			context.waitTicks(10);
			context.takeScreenshot("golemaddons-animations-options");
			click(context, "golemaddons.animations.tabs", 0.85); // Presets
			context.waitTicks(10);
			context.takeScreenshot("golemaddons-animations-presets");

			// Terminals: the Look tab, then every practice terminal solved by clicking the GUI like a player.
			context.setScreen(() -> new GolemHubScreen(null));
			context.waitTicks(30);
			click(context, "golemaddons.hub.qol");
			context.waitTicks(30);
			click(context, "golemaddons.hub.terminals");
			context.waitTicks(10);
			click(context, "golemaddons.terminals.tabs", 0.5);
			context.waitTicks(10);
			context.takeScreenshot("golemaddons-terminals-clicking");
			click(context, "golemaddons.terminals.tabs", 0.85);
			context.waitTicks(10);
			context.takeScreenshot("golemaddons-terminals-look");
			context.setScreen(() -> null);
			setTerminals(context, t -> t.othersSaturation = 15);
			for (TerminalType type : TerminalType.values()) {
				solveByClicking(context, type, Mode.CLICK, "golemaddons-terminal-" + type.name().toLowerCase(java.util.Locale.ROOT));
			}
			// Hover click: only move the cursor over the highlights, never press a button.
			setTerminals(context, t -> t.hoverClick = true);
			solveByClicking(context, TerminalType.PANES, Mode.HOVER, "golemaddons-terminal-hover");
			solveByClicking(context, TerminalType.RUBIX, Mode.HOVER, null);
			// Auto terms: no input at all.
			setTerminals(context, t -> {
				t.hoverClick = false;
				t.autoTerms = true;
			});
			for (TerminalType type : TerminalType.values()) {
				solveByClicking(context, type, Mode.AUTO, type == TerminalType.MELODY ? "golemaddons-terminal-auto-melody" : null);
			}
			setTerminals(context, t -> t.autoTerms = false);
			// The vanilla chest with highlights instead of the compact GUI.
			setTerminals(context, t -> {
				t.hoverClick = false;
				t.customGui = false;
			});
			solveByClicking(context, TerminalType.ORDER, Mode.CLICK, "golemaddons-terminal-highlights");
			setTerminals(context, t -> t.customGui = true);

			// The refresh button in the hub footer: checks for an update and shows the result next to it.
			context.setScreen(() -> new GolemHubScreen(null));
			context.waitTicks(30);
			click(context, "golemaddons.hub.update.button");
			context.waitTicks(60);
			context.takeScreenshot("golemaddons-hub-update-check");

			// Party finder, and from it My Party (the create form).
			context.setScreen(() -> new GolemHubScreen(null));
			context.waitTicks(30);
			click(context, "golemaddons.hub.partyfinder");
			context.waitTicks(10);
			context.takeScreenshot("golemaddons-hub-partyfinder");
			click(context, "golemaddons.pf.create");
			context.waitTicks(10);
			context.takeScreenshot("golemaddons-pf-my-party");

			// A populated finder: your party (with a member and a request) and two others.
			context.runOnClient(mc -> PartyFinderFeature.instance().showSampleData(sample(mc.getUser().getProfileId())));
			context.setScreen(() -> new PartyFinderScreen(null));
			context.waitTicks(20);
			context.takeScreenshot("golemaddons-pf-list");
			click(context, "golemaddons.pf.my_party");
			context.waitTicks(10);
			context.takeScreenshot("golemaddons-pf-leading");
			context.setScreen(() -> null);
		}
	}

	private static void setTerminals(ClientGameTestContext context, java.util.function.Consumer<GolemConfig.Terminals> change) {
		context.runOnClient(mc -> {
			GolemConfig next = GolemAddonsClient.config().get().copy();
			change.accept(next.terminals);
			if (!GolemAddonsClient.config().save(next)) throw new AssertionError("config not saved");
		});
	}

	/**
	 * Opens a practice terminal and solves it through the real GUI: the cursor goes to the next highlighted tile
	 * and clicks (or, with hover click, only rests there). Fails on any wrong click or if it never finishes.
	 */
	private enum Mode {CLICK, HOVER, AUTO}

	private static void solveByClicking(ClientGameTestContext context, TerminalType type, Mode mode, String screenshot) {
		context.runOnClient(mc -> TerminalPractice.start(java.util.List.of(type)));
		context.waitTicks(5);
		TerminalPuzzle puzzle = context.computeOnClient(mc -> TerminalPractice.current().puzzle());
		if (screenshot != null) context.takeScreenshot(screenshot);
		for (int tick = 0; !context.computeOnClient(mc -> puzzle.solved()); tick++) {
			if (tick > 1200) throw new AssertionError(type + " was not solved");
			double[] target = context.computeOnClient(mc -> {
				double[] t = TerminalFeature.nextTarget();
				if (t == null) return null;
				double scale = mc.getWindow().getGuiScale();
				return new double[] {t[0] * scale, t[1] * scale};
			});
			if (target == null || mode == Mode.AUTO) {
				context.waitTick();
				continue;
			}
			context.getInput().setCursorPos(target[0], target[1]);
			context.waitTick();
			if (mode == Mode.CLICK) context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(mode == Mode.HOVER ? 4 : 2);
		}
		int misclicks = context.computeOnClient(mc -> puzzle.misclicks());
		if (misclicks != 0) throw new AssertionError(type + ": " + misclicks + " wrong clicks");
		context.waitTicks(15); // the practice run closes its screen once done
		if (context.computeOnClient(mc -> mc.screen instanceof TerminalPractice.PracticeScreen)) {
			throw new AssertionError(type + ": practice screen still open after solving");
		}
	}

	private static JsonObject sample(UUID me) {
		String you = me.toString().replace("-", "");
		long now = System.currentTimeMillis();
		String json = """
				{"t":"parties","you":"%1$s","parties":[
				 {"id":"aaaaaaaaaa","type":"gdrag","name":"Gold farm, chill","desc":"Need a stun and a mage. 300+ only","max":5,"size":2,
				  "roles":{"stun":1,"mage":1},"open":{"stun":1,"mage":1},"autoAccept":false,"status":"open",
				  "leader":{"uuid":"%1$s","name":"You"},"members":[{"uuid":"%2$s","name":"Steve","role":"term"}],"createdAt":%3$d,"leaderOnline":true},
				 {"id":"bbbbbbbbbb","type":"ndrag","name":"Quick NDrag runs","desc":"","max":4,"size":3,"roles":{},"open":{},
				  "autoAccept":true,"status":"open","leader":{"uuid":"%4$s","name":"Alex"},
				  "members":[{"uuid":"%5$s","name":"Notch","role":null},{"uuid":"%6$s","name":"Jeb_","role":"melee"}],
				  "createdAt":%7$d,"leaderOnline":true},
				 {"id":"cccccccccc","type":"gdrag","name":"Sweaty gold","desc":"Full send","max":2,"size":2,"roles":{},"open":{},
				  "autoAccept":false,"status":"full","leader":{"uuid":"%8$s","name":"Dinnerbone"},
				  "members":[{"uuid":"%9$s","name":"Grumm","role":"support"}],"createdAt":%10$d,"leaderOnline":false}],
				 "requests":[{"id":"7","party":"aaaaaaaaaa","uuid":"%11$s","name":"Herobrine","role":"stun","at":%3$d}],"pending":[]}
				""".formatted(you, "1".repeat(32), now - 120_000, "2".repeat(32), "3".repeat(32), "4".repeat(32), now - 30_000,
				"5".repeat(32), "6".repeat(32), now - 4_000_000, "7".repeat(32));
		return JsonParser.parseString(json).getAsJsonObject();
	}

	/** Clicks the hub card whose title is {@code key} (cards are custom widgets, so find them by their label). */
	private static void click(ClientGameTestContext context, String key) {
		click(context, key, 0.5);
	}

	/** Clicks the widget labelled {@code key} at {@code fraction} of its width. */
	private static void click(ClientGameTestContext context, String key, double fraction) {
		double[] pos = context.computeOnClient(mc -> {
			for (var child : mc.screen.children()) {
				if (child instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents t
						&& (t.getKey().equals(key))) {
					double scale = mc.getWindow().getGuiScale();
					return new double[] {(w.getX() + w.getWidth() * fraction) * scale, (w.getY() + w.getHeight() / 2.0) * scale};
				}
			}
			throw new AssertionError("No card " + key);
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTick();
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}
}
