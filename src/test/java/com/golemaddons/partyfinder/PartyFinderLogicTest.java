package com.golemaddons.partyfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.golemaddons.partyfinder.PartyModels.DragonType;
import com.golemaddons.partyfinder.PartyModels.JoinRequest;
import com.golemaddons.partyfinder.PartyModels.Party;
import com.golemaddons.partyfinder.PartyModels.Role;
import com.golemaddons.partyfinder.PartyModels.Status;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PartyFinderLogicTest {
	private static final String LEADER = "a".repeat(32);
	private static final String BOB = "b".repeat(32);

	/** A party as the Worker's view() sends it. */
	private static String partyJson(String id, String type, String name, int size, int max, String status, long createdAt, String open) {
		StringBuilder members = new StringBuilder();
		for (int i = 1; i < size; i++) {
			if (i > 1) members.append(',');
			members.append("{\"uuid\":\"").append(String.valueOf((char) ('b' + i)).repeat(32)).append("\",\"name\":\"M").append(i)
					.append("\",\"role\":null}");
		}
		return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"name\":\"" + name + "\",\"desc\":\"chill run\",\"max\":" + max
				+ ",\"size\":" + size + ",\"roles\":{\"stun\":1},\"open\":" + open + ",\"autoAccept\":false,\"status\":\"" + status
				+ "\",\"leader\":{\"uuid\":\"" + LEADER + "\",\"name\":\"Leader\"},\"members\":[" + members + "],\"createdAt\":"
				+ createdAt + ",\"leaderOnline\":true}";
	}

	private static Party parse(String json) {
		return PartyJson.party(JsonParser.parseString(json));
	}

	@Test
	void parsesAServerParty() {
		Party p = parse(partyJson("abcdefghij", "gdrag", "Gold run", 2, 4, "open", 5, "{\"stun\":1}"));
		assertNotNull(p);
		assertEquals(DragonType.GDRAG, p.type());
		assertEquals(2, p.size());
		assertEquals(1, p.open().get(Role.STUN));
		assertEquals("Leader", p.leaderName());
		assertEquals(1, p.members().size());
		assertEquals(1, p.freeUnreserved()); // 4 places - 2 taken - 1 reserved for stun
		assertTrue(p.canTake(null));
		assertTrue(p.canTake(Role.STUN));
	}

	@Test
	void rejectsMalformedOrUnsafeParties() {
		assertNull(parse(partyJson("short", "gdrag", "x", 1, 4, "open", 0, "{}")), "bad id");
		assertNull(parse(partyJson("abcdefghij", "edrag", "x", 1, 4, "open", 0, "{}")), "unknown type");
		assertNull(parse(partyJson("abcdefghij", "gdrag", "§cRed", 1, 4, "open", 0, "{}")), "formatting code");
		assertNull(parse(partyJson("abcdefghij", "gdrag", "join discord.gg/x", 1, 4, "open", 0, "{}")), "link");
		assertNull(parse(partyJson("abcdefghij", "gdrag", "x", 1, 9, "open", 0, "{}")), "size out of range");
		assertNull(parse(partyJson("abcdefghij", "gdrag", "x", 1, 4, "weird", 0, "{}")), "unknown status");
		assertNull(parse(partyJson("abcdefghij", "gdrag", "x", 1, 4, "open", 0, "{\"stun\":9}")), "role count out of range");
		String badLeader = partyJson("abcdefghij", "gdrag", "x", 1, 4, "open", 0, "{}").replace("\"Leader\"", "\"p invite x\"");
		assertNull(parse(badLeader), "leader name with spaces");
		assertNull(PartyJson.party(JsonParser.parseString("[]")));
	}

	@Test
	void parsesRequestsStrictly() {
		JoinRequest r = PartyJson.request(JsonParser.parseString(
				"{\"id\":\"12\",\"party\":\"abcdefghij\",\"uuid\":\"" + BOB + "\",\"name\":\"Bob\",\"role\":\"mage\",\"at\":1}"));
		assertNotNull(r);
		assertEquals(Role.MAGE, r.role());
		assertNull(PartyJson.request(JsonParser.parseString(
				"{\"id\":\"12; say hi\",\"party\":\"abcdefghij\",\"uuid\":\"" + BOB + "\",\"name\":\"Bob\",\"role\":null,\"at\":1}")),
				"request ids end up in a /gapf command, so only digits");
		assertNull(PartyJson.request(JsonParser.parseString(
				"{\"id\":\"1\",\"party\":\"abcdefghij\",\"uuid\":\"" + BOB + "\",\"name\":\"Bob\",\"role\":\"king\",\"at\":1}")));
	}

	@Test
	void settingsSerializeEveryRole() {
		var roles = PartyModels.emptyRoles();
		roles.put(Role.TERM, 2);
		JsonObject o = PartyJson.settings(new PartyModels.Settings(DragonType.NDRAG, "Run", "", 5, roles, true));
		assertEquals("ndrag", o.get("type").getAsString());
		assertEquals(2, o.getAsJsonObject("roles").get("term").getAsInt());
		assertEquals(0, o.getAsJsonObject("roles").get("stun").getAsInt());
		assertTrue(o.get("autoAccept").getAsBoolean());
	}

	@Test
	void filtersSortsAndPages() {
		Party a = parse(partyJson("aaaaaaaaaa", "gdrag", "Alpha", 1, 4, "open", 100, "{\"stun\":1}"));
		Party b = parse(partyJson("bbbbbbbbbb", "ndrag", "Bravo", 3, 4, "open", 300, "{}"));
		Party c = parse(partyJson("cccccccccc", "gdrag", "Charlie", 4, 4, "full", 200, "{}"));
		List<Party> all = List.of(a, b, c);
		var newest = new PartyQuery.Filter(null, "", Set.of(), false, PartyQuery.Sort.NEWEST);
		assertEquals(List.of(b, a, c), PartyQuery.apply(all, newest), "open first, then newest");
		assertEquals(List.of(a, c), PartyQuery.apply(all, new PartyQuery.Filter(DragonType.GDRAG, "", Set.of(), false, PartyQuery.Sort.NEWEST)));
		assertEquals(List.of(b, a), PartyQuery.apply(all, new PartyQuery.Filter(null, "", Set.of(), true, PartyQuery.Sort.NEWEST)));
		assertEquals(List.of(a), PartyQuery.apply(all, new PartyQuery.Filter(null, "", EnumSet.of(Role.STUN), false, PartyQuery.Sort.NEWEST)));
		assertEquals(List.of(b), PartyQuery.apply(all, new PartyQuery.Filter(null, "BRA", Set.of(), false, PartyQuery.Sort.NEWEST)));
		assertEquals(List.of(b, c), PartyQuery.apply(all, new PartyQuery.Filter(null, "m2", Set.of(), false, PartyQuery.Sort.NEWEST)), "member names");
		assertEquals(List.of(a, b, c), PartyQuery.apply(all, new PartyQuery.Filter(null, "", Set.of(), false, PartyQuery.Sort.FEWEST_PLAYERS)));
		assertEquals(List.of(a, b, c), PartyQuery.apply(all, new PartyQuery.Filter(null, "", Set.of(), false, PartyQuery.Sort.MOST_OPEN)));

		PartyQuery.Page<Integer> page = PartyQuery.page(List.of(1, 2, 3, 4, 5), 9, 2);
		assertEquals(2, page.page(), "clamped to the last page");
		assertEquals(List.of(5), page.items());
		assertEquals(3, page.pages());
		assertEquals(1, PartyQuery.page(List.of(), 0, 20).pages());
	}

	@Test
	void fullAndInProgressPartiesCantBeJoined() {
		Party full = parse(partyJson("cccccccccc", "gdrag", "Charlie", 4, 4, "full", 200, "{}"));
		Party busy = parse(partyJson("dddddddddd", "gdrag", "Delta", 1, 4, "in_progress", 200, "{}"));
		Party reserved = parse(partyJson("eeeeeeeeee", "gdrag", "Echo", 2, 3, "open", 200, "{\"stun\":1}"));
		assertFalse(full.canTake(null));
		assertFalse(busy.canTake(null));
		assertEquals(Status.IN_PROGRESS, busy.status());
		assertFalse(reserved.canTake(null), "the last place is kept for a stun");
		assertFalse(reserved.canTake(Role.MAGE));
		assertTrue(reserved.canTake(Role.STUN));
	}

	@ParameterizedTest
	@ValueSource(strings = {"play.example.com", "1.2.3.4", "me@mail.com", "call 555 123 4567", "https://x", "discord.gg/abc", "a‮b", "§aHi"})
	void rejectsUnsafeText(String text) {
		assertFalse(PartyText.check(text, 120, true).valid(), text);
	}

	@ParameterizedTest
	@ValueSource(strings = {"Need a mage. Message me", "GDrag 4/6, chill", "lvl 300+ only", "Fast & clean ★"})
	void acceptsNormalText(String text) {
		assertTrue(PartyText.check(text, 120, true).valid(), text);
	}

	@Test
	void textIsTrimmedAndLimited() {
		assertEquals("need stun", PartyText.check("  need   stun ", 32, false).text());
		assertEquals(PartyText.Problem.EMPTY, PartyText.check("   ", 32, false).problem());
		assertTrue(PartyText.check("", 32, true).valid());
		assertEquals(PartyText.Problem.TOO_LONG, PartyText.check("x".repeat(33), 32, false).problem());
	}

	@Test
	void inviteCommandsCantBeInjected() {
		assertEquals("p Bob_1", PartyText.inviteCommand("p {name}", "Bob_1"));
		assertEquals("party invite Bob", PartyText.inviteCommand("/party invite {name}", "Bob"));
		assertNull(PartyText.inviteCommand("p {name}", "Bob say hi"));
		assertNull(PartyText.inviteCommand("p {name}", "Bob;op"));
		assertNull(PartyText.inviteCommand("p {name}", ""));
		assertNull(PartyText.inviteCommand("p", "Bob"), "template needs the placeholder");
		assertNull(PartyText.inviteCommand("p {name} {name}", "Bob"));
		assertNull(PartyText.inviteCommand("p {name} & op me", "Bob"));
		assertFalse(PartyText.validTemplate("tell {name} §chi"));
		assertTrue(PartyText.validTemplate("p {name}"));
	}

	@Test
	void invitesArePacedAndDeduplicated() {
		AtomicLong now = new AtomicLong(10_000);
		InviteThrottle t = new InviteThrottle(now::get);
		assertTrue(t.offer("p A"));
		assertFalse(t.offer("p A"), "no duplicates");
		for (int i = 0; i < InviteThrottle.MAX_QUEUED - 1; i++) assertTrue(t.offer("p N" + i));
		assertFalse(t.offer("p overflow"), "bounded queue");
		assertEquals("p A", t.poll());
		assertNull(t.poll(), "spaced out");
		now.addAndGet(InviteThrottle.SPACING_MS);
		assertEquals("p N0", t.poll());
	}

	@Test
	void roomKeyIsAStableHashOfTheAddress() {
		String key = PartyFinderFeature.roomKey("Play.Example.net");
		assertEquals(32, key.length());
		assertTrue(key.matches("[0-9a-f]{32}"));
		assertEquals(key, PartyFinderFeature.roomKey("play.example.net:25565"));
		assertEquals(key, PartyFinderFeature.roomKey(" play.example.net. "));
		assertNotEquals(key, PartyFinderFeature.roomKey("other.example.net"));
		assertFalse(key.contains("example"));
	}
}
