package com.golemaddons.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.golemaddons.animation.HeldItemAnimation;
import com.golemaddons.dragon.DragonCatalog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ConfigMigrationTest {
	private static final String V1 = """
			{
			  "configVersion": 1,
			  "autoSell": {
			    "sellScreenTitlePattern": "Sell Items",
			    "clickDelayTicks": 6,
			    "protectHotbar": false,
			    "rules": [ { "enabled": true, "label": "", "namePattern": "Enchanted Obsidian", "itemId": "", "caseInsensitive": false } ]
			  }
			}""";

	@Test
	void v1IsSplitIntoSellingAndMiningWithoutLosingData() {
		JsonObject root = JsonParser.parseString(V1).getAsJsonObject();
		assertEquals(1, ConfigMigration.migrate(root));
		GolemConfig config = new Gson().fromJson(root, GolemConfig.class);
		config.sanitize();

		assertEquals(2, config.configVersion);
		assertEquals("Sell Items", config.selling.sellScreenTitlePattern);
		assertEquals(6, config.selling.clickDelayTicks);
		assertFalse(config.selling.protectHotbar);
		assertEquals(1, config.miningAutoSell.rules.size());
		assertEquals("Enchanted Obsidian", config.miningAutoSell.rules.get(0).namePattern);
		// New section gets its defaults.
		assertTrue(config.dragonSeller.enabled);
		assertTrue(config.dragonSeller.sellsDragon(DragonCatalog.DragonType.YOUNG));
		assertFalse(config.dragonSeller.sellsDragon(DragonCatalog.DragonType.GOLDEN));
	}

	@Test
	void partialDragonMapsKeepMissingKeysAndDropUnknownOnes() {
		String json = """
				{ "configVersion": 2, "dragonSeller": { "enabled": true,
				  "dragons": { "GOLDEN": true, "NOT_A_DRAGON": true } } }""";
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		assertEquals(2, ConfigMigration.migrate(root));
		GolemConfig config = new Gson().fromJson(root, GolemConfig.class);
		config.sanitize();

		assertTrue(config.dragonSeller.sellsDragon(DragonCatalog.DragonType.GOLDEN));
		// Missing from a stored map = keep: an update never starts selling something unasked.
		assertFalse(config.dragonSeller.sellsDragon(DragonCatalog.DragonType.YOUNG));
		assertFalse(config.dragonSeller.sellsDragon(DragonCatalog.DragonType.CHAOTIC));
		assertFalse(config.dragonSeller.dragons.containsKey("NOT_A_DRAGON"));
		assertEquals(DragonCatalog.DragonType.values().length, config.dragonSeller.dragons.size());
		// A section absent from the file gets the defaults.
		assertTrue(config.dragonSeller.sellsPiece(DragonCatalog.Piece.FRAGMENT));
	}

	@Test
	void renderSectionDefaultsOffAndRoundTrips() {
		JsonObject root = JsonParser.parseString(V1).getAsJsonObject();
		ConfigMigration.migrate(root);
		GolemConfig migrated = new Gson().fromJson(root, GolemConfig.class);
		migrated.sanitize();
		assertFalse(migrated.render.dragonHitboxes, "missing render section defaults to off");

		GolemConfig on = GolemConfig.defaults();
		on.render.dragonHitboxes = true;
		GolemConfig back = new Gson().fromJson(new Gson().toJson(on), GolemConfig.class);
		back.sanitize();
		assertTrue(back.render.dragonHitboxes);
		assertTrue(on.copy().render.dragonHitboxes);

		GolemConfig nulled = new Gson().fromJson("{\"configVersion\":2,\"render\":null}", GolemConfig.class);
		nulled.sanitize();
		assertFalse(nulled.render.dragonHitboxes);
	}
}
