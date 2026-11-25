package com.golemaddons.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class PartyFinderConfigTest {
	@Test
	void oldConfigsGetDefaults() {
		GolemConfig config = new Gson().fromJson("{\"configVersion\":2}", GolemConfig.class);
		config.sanitize();
		assertTrue(config.partyFinder.enabled);
		assertEquals("p {name}", config.partyFinder.inviteCommand);
		assertEquals(5, config.partyFinder.draft.max);
	}

	@Test
	void tamperedValuesAreRepaired() {
		GolemConfig config = new Gson().fromJson("""
				{"partyFinder": {"inviteCommand": "op {name}; say hi", "typeFilter": "edrag", "sort": "random",
				 "roleFilter": ["stun", "king"], "joinAs": "king",
				 "draft": {"type": "x", "max": 40, "roles": {"stun": 9, "healer": 1}, "name": null}}}""", GolemConfig.class);
		config.sanitize();
		GolemConfig.PartyFinder pf = config.partyFinder;
		assertEquals("p {name}", pf.inviteCommand);
		assertEquals("all", pf.typeFilter);
		assertEquals("newest", pf.sort);
		// Roles were removed: anything saved by an older version is dropped.
		assertEquals(java.util.List.of(), pf.roleFilter);
		assertEquals("", pf.joinAs);
		assertEquals("gdrag", pf.draft.type);
		assertEquals(6, pf.draft.max);
		assertEquals(java.util.Map.of(), pf.draft.roles);
		assertEquals("", pf.draft.name);
	}

	@Test
	void copyIsDeep() {
		GolemConfig a = new GolemConfig();
		a.partyFinder.roleFilter.add("mage");
		a.partyFinder.draft.roles.put("term", 2);
		GolemConfig b = a.copy();
		b.partyFinder.roleFilter.clear();
		b.partyFinder.draft.roles.clear();
		assertEquals(1, a.partyFinder.roleFilter.size());
		assertEquals(1, a.partyFinder.draft.roles.size());
	}
}
