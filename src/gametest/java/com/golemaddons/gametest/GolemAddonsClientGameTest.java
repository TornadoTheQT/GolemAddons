package com.golemaddons.gametest;

import com.golemaddons.partyfinder.PartyFinderFeature;
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
	private static final String[] CARDS = {
			"golemaddons.hub.mining", "golemaddons.hub.dragon", "golemaddons.hub.settings",
			"golemaddons.hub.cosmetics", "golemaddons.hub.autoclicker", "golemaddons.hub.partyfinder"};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();
			context.setScreen(() -> new GolemHubScreen(null));
			context.waitTicks(30); // let the entrance animation finish
			context.takeScreenshot("golemaddons-hub");
			for (String card : CARDS) {
				context.setScreen(() -> new GolemHubScreen(null));
				context.waitTicks(30);
				click(context, card);
				context.waitTicks(10);
				context.takeScreenshot(card.replace('.', '-'));
			}
			// From the party finder, open My Party (the create form) too.
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
		double[] pos = context.computeOnClient(mc -> {
			for (var child : mc.screen.children()) {
				if (child instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents t
						&& t.getKey().equals(key)) {
					double scale = mc.getWindow().getGuiScale();
					return new double[] {(w.getX() + w.getWidth() / 2.0) * scale, (w.getY() + w.getHeight() / 2.0) * scale};
				}
			}
			throw new AssertionError("No card " + key);
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTick();
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}
}
