package com.golemaddons.partyfinder;

import com.golemaddons.cosmetics.Hashes;
import com.golemaddons.partyfinder.PartyModels.DragonType;
import com.golemaddons.partyfinder.PartyModels.JoinRequest;
import com.golemaddons.partyfinder.PartyModels.Member;
import com.golemaddons.partyfinder.PartyModels.Party;
import com.golemaddons.partyfinder.PartyModels.Role;
import com.golemaddons.partyfinder.PartyModels.Settings;
import com.golemaddons.partyfinder.PartyModels.Status;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/**
 * Strict parsing of party finder messages. Anything malformed becomes null and is ignored, so a compromised or buggy
 * server can't feed the UI oversized text, formatting codes or bad names. Plain Java, unit tested.
 */
public final class PartyJson {
	private static final int MAX_MEMBERS = 6;

	private PartyJson() {
	}

	public static Party party(JsonElement el) {
		if (el == null || !el.isJsonObject()) return null;
		JsonObject o = el.getAsJsonObject();
		String id = string(o, "id", 16);
		DragonType type = DragonType.byId(string(o, "type", 8));
		Status status = Status.byId(string(o, "status", 16));
		String name = text(o, "name", PartyText.NAME_MAX, false);
		String desc = text(o, "desc", PartyText.DESC_MAX, true);
		int max = integer(o, "max", 1, 6);
		int size = integer(o, "size", 1, 6);
		EnumMap<Role, Integer> roles = roles(o.get("roles"));
		EnumMap<Role, Integer> open = roles(o.get("open"));
		JsonObject leader = o.has("leader") && o.get("leader").isJsonObject() ? o.getAsJsonObject("leader") : null;
		UUID leaderId = leader == null ? null : Hashes.parseUuid(string(leader, "uuid", 36));
		String leaderName = leader == null ? null : string(leader, "name", 16);
		List<Member> members = members(o.get("members"));
		if (id == null || !id.matches("[a-z0-9]{10}") || type == null || status == null || name == null || desc == null
				|| max < 0 || size < 0 || roles == null || open == null || leaderId == null
				|| !PartyText.validPlayerName(leaderName) || members == null) {
			return null;
		}
		return new Party(id, type, name, desc, max, size, roles, open, bool(o, "autoAccept"), status, leaderId,
				leaderName, List.copyOf(members), number(o, "createdAt"), !o.has("leaderOnline") || bool(o, "leaderOnline"));
	}

	public static JoinRequest request(JsonElement el) {
		if (el == null || !el.isJsonObject()) return null;
		JsonObject o = el.getAsJsonObject();
		String id = string(o, "id", 16);
		String party = string(o, "party", 16);
		UUID uuid = Hashes.parseUuid(string(o, "uuid", 36));
		String name = string(o, "name", 16);
		Role role = o.has("role") && o.get("role").isJsonNull() ? null : Role.byId(string(o, "role", 16));
		boolean roleOk = !o.has("role") || o.get("role").isJsonNull() || role != null;
		if (id == null || !id.matches("[0-9]{1,15}") || party == null || uuid == null || !PartyText.validPlayerName(name) || !roleOk) {
			return null;
		}
		return new JoinRequest(id, party, uuid, name, role, number(o, "at"));
	}

	public static List<Party> parties(JsonElement el) {
		List<Party> out = new ArrayList<>();
		if (el != null && el.isJsonArray()) {
			for (JsonElement p : el.getAsJsonArray()) {
				Party party = party(p);
				if (party != null && out.size() < 500) out.add(party);
			}
		}
		return out;
	}

	public static List<JoinRequest> requests(JsonElement el) {
		List<JoinRequest> out = new ArrayList<>();
		if (el != null && el.isJsonArray()) {
			for (JsonElement r : el.getAsJsonArray()) {
				JoinRequest req = request(r);
				if (req != null && out.size() < 50) out.add(req);
			}
		}
		return out;
	}

	/** The fields a create/edit message carries. */
	public static JsonObject settings(Settings s) {
		JsonObject o = new JsonObject();
		o.addProperty("type", s.type().id);
		o.addProperty("name", s.name());
		o.addProperty("desc", s.desc());
		o.addProperty("max", s.max());
		JsonObject roles = new JsonObject();
		for (Role r : Role.values()) roles.addProperty(r.id(), s.roles().getOrDefault(r, 0));
		o.add("roles", roles);
		o.addProperty("autoAccept", s.autoAccept());
		return o;
	}

	// ---- field helpers ----

	static String string(JsonObject o, String key, int max) {
		JsonElement el = o.get(key);
		if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) return null;
		String s = el.getAsString();
		return s.length() <= max ? s : null;
	}

	/** Shared text: must pass the same checks the server applies. */
	private static String text(JsonObject o, String key, int max, boolean allowEmpty) {
		String s = string(o, key, max * 2);
		if (s == null) return null;
		PartyText.Result r = PartyText.check(s, max, allowEmpty);
		return r.valid() ? r.text() : null;
	}

	private static int integer(JsonObject o, String key, int lo, int hi) {
		JsonElement el = o.get(key);
		if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) return -1;
		double d = el.getAsDouble();
		return d == Math.rint(d) && d >= lo && d <= hi ? (int) d : -1;
	}

	private static long number(JsonObject o, String key) {
		JsonElement el = o.get(key);
		if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) return 0;
		return Math.max(0, el.getAsLong());
	}

	private static boolean bool(JsonObject o, String key) {
		JsonElement el = o.get(key);
		return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean() && el.getAsBoolean();
	}

	private static EnumMap<Role, Integer> roles(JsonElement el) {
		if (el == null || !el.isJsonObject()) return null;
		EnumMap<Role, Integer> out = PartyModels.emptyRoles();
		JsonObject o = el.getAsJsonObject();
		for (Role r : Role.values()) {
			if (!o.has(r.id())) continue;
			int v = integer(o, r.id(), 0, 5);
			if (v < 0) return null;
			out.put(r, v);
		}
		return out;
	}

	private static List<Member> members(JsonElement el) {
		if (el == null || !el.isJsonArray()) return null;
		JsonArray arr = el.getAsJsonArray();
		if (arr.size() > MAX_MEMBERS) return null;
		List<Member> out = new ArrayList<>();
		for (JsonElement m : arr) {
			if (!m.isJsonObject()) return null;
			JsonObject o = m.getAsJsonObject();
			UUID uuid = Hashes.parseUuid(string(o, "uuid", 36));
			String name = string(o, "name", 16);
			Role role = o.has("role") && !o.get("role").isJsonNull() ? Role.byId(string(o, "role", 16)) : null;
			if (uuid == null || !PartyText.validPlayerName(name)) return null;
			out.add(new Member(uuid, name, role));
		}
		return out;
	}
}
