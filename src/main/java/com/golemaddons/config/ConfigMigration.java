package com.golemaddons.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Upgrades older config JSON in place, one version step at a time. */
final class ConfigMigration {
	private ConfigMigration() {
	}

	/** @return the version the JSON was at before migrating (missing means 1) */
	static int migrate(JsonObject root) {
		int original = root.has("configVersion") ? root.get("configVersion").getAsInt() : 1;
		if (original < 2) {
			v1ToV2(root);
		}
		return original;
	}

	/** v1 "autoSell" {title, delay, hotbar, rules} becomes "selling" {title, delay, hotbar} + "miningAutoSell" {rules}. */
	private static void v1ToV2(JsonObject root) {
		JsonElement old = root.remove("autoSell");
		if (old != null && old.isJsonObject()) {
			JsonObject autoSell = old.getAsJsonObject();
			JsonObject selling = new JsonObject();
			for (String key : new String[] {"sellScreenTitlePattern", "clickDelayTicks", "protectHotbar"}) {
				if (autoSell.has(key)) selling.add(key, autoSell.get(key));
			}
			root.add("selling", selling);
			JsonObject mining = new JsonObject();
			if (autoSell.has("rules")) mining.add("rules", autoSell.get("rules"));
			root.add("miningAutoSell", mining);
		}
		root.addProperty("configVersion", 2);
	}
}
