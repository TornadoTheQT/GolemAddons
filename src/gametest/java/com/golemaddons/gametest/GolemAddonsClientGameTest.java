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
			{"golemaddons.hub.selling", "golemaddons.hub.mining", "golemaddons.hub.dragon", "golemaddons.hub.pets", "golemaddons.hub.settings"},
			{"golemaddons.hub.qol", "golemaddons.hub.autoclicker", "golemaddons.hub.camera", "golemaddons.hub.terminals", "golemaddons.hub.afk"},
			{"golemaddons.hub.combat", "golemaddons.hub.riftborn", "golemaddons.hub.boneswap", "golemaddons.hub.chatcleaner"},
			{"golemaddons.hub.visuals", "golemaddons.hub.scoreboard", "golemaddons.hub.cosmetics", "golemaddons.hub.animations",
					"golemaddons.hub.world"}};

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
				// Every page of the section is one click away in the sidebar.
				for (int i = 1; i < category.length; i++) {
					click(context, category[i]);
					context.waitTicks(10);
					context.takeScreenshot(category[i].replace('.', '-'));
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

			scoreboardPages(context);
			riftbornAndBones(context, world);
			afkHud(context);

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
			// The version number opens the Updates screen with the release notes.
			click(context, "golemaddons.hub.version");
			context.waitTicks(10);
			context.takeScreenshot("golemaddons-updates-screen");

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

	/** A Riftborn fight set up with commands: sidebar lines, two sea lanterns, then the anchors message. */
	private static void riftbornAndBones(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		context.setScreen(() -> null); // an open menu pauses the singleplayer server
		context.waitTicks(5);
		server.runCommand("gamerule send_command_feedback false");
		server.runCommand("scoreboard objectives add ga dummy \"DRAGSIM\"");
		server.runCommand("scoreboard objectives setdisplay sidebar ga");
		server.runCommand("team add t1");
		server.runCommand("team modify t1 prefix \"Riftborn Seraph\"");
		server.runCommand("team join t1 boss1");
		server.runCommand("team add t2");
		server.runCommand("team modify t2 prefix \"Slay the boss!\"");
		server.runCommand("team join t2 boss2");
		server.runCommand("scoreboard players set boss1 ga 2");
		server.runCommand("scoreboard players set boss2 ga 1");
		server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 10");
		int[] p = context.computeOnClient(mc -> new int[] {mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
		var pos1 = new net.minecraft.core.BlockPos(p[0] + 1, p[1] + 1, p[2] + 5);
		var pos2 = new net.minecraft.core.BlockPos(p[0] - 2, p[1] + 2, p[2] + 6);
		// The anchor search reads the client's world; set the blocks there directly.
		context.runOnClient(mc -> {
			mc.level.setBlock(pos1, net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState(), 3);
			mc.level.setBlock(pos2, net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState(), 3);
		});
		context.waitTicks(20);
		if (!context.computeOnClient(mc -> com.golemaddons.boss.Bosses.active(com.golemaddons.boss.BossType.RIFTBORN_SERAPH))) {
			throw new AssertionError("Riftborn fight not recognised from the sidebar");
		}
		server.runCommand("tellraw @a \"It hangs itself on 2 anchors. Shoot every one with a bow.\"");
		context.waitTicks(20);
		int found = context.computeOnClient(mc -> com.golemaddons.boss.Bosses.anchorCount());
		if (found != 2) {
			String seen = context.computeOnClient(mc -> mc.level.getBlockState(pos1) + " / " + mc.level.getBlockState(pos2) + " player " + mc.player.blockPosition()
					+ " minY " + mc.level.getMinY() + " maxY " + mc.level.getMaxY());
			throw new AssertionError("expected 2 anchors, found " + found + ": " + seen);
		}
		context.takeScreenshot("golemaddons-riftborn-anchors");
		// takeScreenshot renders its own frame outside the game loop, which skips gizmos (outlines); save a frame the
		// game rendered itself instead.
		context.runOnClient(mc -> net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
			try (image) {
				image.writeToFile(mc.gameDirectory.toPath().resolve("screenshots/golemaddons-riftborn-anchors-live.png"));
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}));
		context.waitTicks(5);
		context.runOnClient(mc -> mc.level.setBlock(pos1, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3));
		context.waitTicks(10);
		found = context.computeOnClient(mc -> com.golemaddons.boss.Bosses.anchorCount());
		if (found != 1) throw new AssertionError("a broken anchor should lose its outline, still " + found);
		// The custom scoreboard over this sidebar, in every theme.
		for (var theme : com.golemaddons.scoreboard.ScoreboardTheme.values()) {
			context.runOnClient(mc -> {
				GolemConfig next = GolemAddonsClient.config().get().copy();
				next.scoreboard.enabled = true;
				next.scoreboard.theme = theme.name();
				GolemAddonsClient.config().save(next);
			});
			context.waitTicks(3);
			if (context.computeOnClient(mc -> com.golemaddons.scoreboard.ScoreboardFeature.model()) == null) {
				throw new AssertionError("custom scoreboard has no model with a sidebar showing");
			}
			context.takeScreenshot("golemaddons-scoreboard-" + theme.name().toLowerCase(java.util.Locale.ROOT));
		}
		// "Hide server's": other renderers see no sidebar, GolemAddons still does.
		boolean hidden = context.computeOnClient(mc -> mc.level.getScoreboard()
				.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR) == null);
		if (!hidden) throw new AssertionError("server sidebar still visible to other renderers");
		if (!context.computeOnClient(mc -> com.golemaddons.boss.Bosses.active(com.golemaddons.boss.BossType.RIFTBORN_SERAPH))) {
			throw new AssertionError("boss helpers lost the sidebar while it is hidden");
		}
		// Disable debuffs: blindness and nausea on, their screen effects off.
		server.runCommand("effect give @a minecraft:blindness 30 0 true");
		server.runCommand("effect give @a minecraft:nausea 30 0 true");
		context.runOnClient(mc -> {
			GolemConfig next = GolemAddonsClient.config().get().copy();
			next.render.disableDebuffs = true;
			GolemAddonsClient.config().save(next);
		});
		context.waitTicks(40);
		float nausea = context.computeOnClient(mc -> mc.player.getEffectBlendFactor(net.minecraft.world.effect.MobEffects.NAUSEA, 1f));
		boolean blind = context.computeOnClient(mc -> mc.player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS));
		if (!blind || nausea != 0f) throw new AssertionError("debuffs: blind=" + blind + " nausea blend=" + nausea);
		context.takeScreenshot("golemaddons-debuffs-hidden");
		server.runCommand("effect clear @a");
		context.runOnClient(mc -> {
			GolemConfig next = GolemAddonsClient.config().get().copy();
			next.render.disableDebuffs = false;
			GolemAddonsClient.config().save(next);
		});
		context.runOnClient(mc -> {
			GolemConfig next = GolemAddonsClient.config().get().copy();
			next.scoreboard.enabled = false;
			GolemAddonsClient.config().save(next);
		});
		server.runCommand("scoreboard objectives remove ga");
		context.waitTicks(10);
		if (context.computeOnClient(mc -> com.golemaddons.boss.Bosses.anchorCount()) != 0) throw new AssertionError("outlines left after the fight");

		// Bone swap: three Bonemerangs, end on slot 1.
		server.runCommand("item replace entity @p hotbar.2 with minecraft:bone[custom_name=\"Bonemerang\"]");
		server.runCommand("item replace entity @p hotbar.4 with minecraft:bone[custom_name=\"Bonemerang\"]");
		server.runCommand("item replace entity @p hotbar.6 with minecraft:bone[custom_name=\"Heroic Bonemerang\"]");
		context.runOnClient(mc -> {
			GolemConfig next = GolemAddonsClient.config().get().copy();
			next.boneSwap.endSlot = 1;
			next.boneSwap.delayMs = 40;
			GolemAddonsClient.config().save(next);
			mc.player.getInventory().setSelectedSlot(8);
			var key = com.golemaddons.combat.BoneSwap.instance().key();
			key.setKey(com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard.g"));
			net.minecraft.client.KeyMapping.resetMapping();
		});
		context.waitTicks(10);
		context.getInput().pressKey(com.golemaddons.combat.BoneSwap.instance().key());
		int[] seen = new int[9];
		for (int i = 0; i < 20; i++) {
			context.waitTick();
			int slot = context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
			seen[slot]++;
		}
		int end = context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
		if (end != 0) throw new AssertionError("bone swap should end on slot 1, ended on " + (end + 1));
		if (seen[2] + seen[4] + seen[6] == 0) throw new AssertionError("bone swap never selected a bone");

		// Pet seller: a legendary and an epic pet; only legendary picked.
		server.runCommand("item replace entity @p inventory.10 with minecraft:bone[custom_name=[{\"text\":\"[Lvl 5] \",\"color\":\"gray\",\"italic\":false},{\"text\":\"Wolf\",\"color\":\"gold\"}]]");
		server.runCommand("item replace entity @p inventory.11 with minecraft:bone[custom_name=[{\"text\":\"[Lvl 5] \",\"color\":\"gray\",\"italic\":false},{\"text\":\"Bee\",\"color\":\"dark_purple\"}]]");
		context.runOnClient(mc -> {
			GolemConfig next = GolemAddonsClient.config().get().copy();
			next.petSeller.legendary = true;
			GolemAddonsClient.config().save(next);
		});
		context.waitTicks(5);
		boolean[] pets = context.computeOnClient(mc -> {
			var rules = com.golemaddons.autosell.AutoSellRules.from(GolemAddonsClient.config().get());
			var inv = mc.player.getInventory();
			return new boolean[] {rules.pets().matches(inv.getItem(19)), rules.pets().matches(inv.getItem(20))};
		});
		if (!pets[0] || pets[1]) throw new AssertionError("pet seller: legendary=" + pets[0] + " epic=" + pets[1]);
		context.runOnClient(mc -> {
			GolemConfig next = GolemAddonsClient.config().get().copy();
			next.petSeller.legendary = false;
			GolemAddonsClient.config().save(next);
		});
	}

	private static void afkHud(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			GolemConfig next = GolemAddonsClient.config().get().copy();
			next.afk.hudAlways = true;
			GolemAddonsClient.config().save(next);
			var f = com.golemaddons.afk.AfkFeature.instance();
			f.testReward("Old Dragon");
			f.testReward("Old Dragon");
			f.testReward("Superior Dragon");
		});
		context.waitTicks(5);
		context.takeScreenshot("golemaddons-afk-hud");
		setAfk(context, false);
	}

	private static void setAfk(ClientGameTestContext context, boolean always) {
		context.runOnClient(mc -> {
			GolemConfig next = GolemAddonsClient.config().get().copy();
			next.afk.hudAlways = always;
			GolemAddonsClient.config().save(next);
		});
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

	/** Scoreboard editor tabs and a long page scrolled in the section pane. */
	private static void scoreboardPages(ClientGameTestContext context) {
		context.setScreen(() -> new GolemHubScreen(null));
		context.waitTicks(30);
		click(context, "golemaddons.hub.visuals");
		context.waitTicks(20);
		click(context, "golemaddons.hub.scoreboard");
		context.waitTicks(10);
		click(context, "golemaddons.scoreboard.tabs", 0.5);
		context.waitTicks(15);
		context.takeScreenshot("golemaddons-scoreboard-background");
		click(context, "golemaddons.scoreboard.tabs", 0.85);
		context.waitTicks(15);
		context.takeScreenshot("golemaddons-scoreboard-text");
		click(context, "golemaddons.scoreboard.tabs", 0.15);
		context.waitTicks(10);
	}

	/** Clicks the hub card whose title is {@code key} (cards are custom widgets, so find them by their label). */
	private static void click(ClientGameTestContext context, String key) {
		click(context, key, 0.5);
	}

	/** Clicks the widget labelled {@code key} at {@code fraction} of its width. */
	private static void click(ClientGameTestContext context, String key, double fraction) {
		double[] pos = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			double[] found = find(mc.screen, key, fraction, 0, 0, scale);
			// Section screens show their page in a pane: look there too.
			if (found == null && mc.screen instanceof com.golemaddons.ui.PaneHost host && host.page() != null) {
				found = find(host.page(), key, fraction, host.pageOffsetX(), host.pageOffsetY(), scale);
			}
			if (found == null) throw new AssertionError("No card " + key);
			return found;
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTick();
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}

	private static double[] find(net.minecraft.client.gui.screens.Screen screen, String key, double fraction, int ox, int oy, double scale) {
		for (var child : screen.children()) {
			if (child instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
				return new double[] {(ox + w.getX() + w.getWidth() * fraction) * scale, (oy + w.getY() + w.getHeight() / 2.0) * scale};
			}
		}
		return null;
	}
}
