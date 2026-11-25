package com.golemaddons.partyfinder;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The party finder's data, as the client sees it. Immutable; parsed and validated by {@link PartyJson}. */
public final class PartyModels {
	private PartyModels() {
	}

	public enum DragonType {
		GDRAG("gdrag", "GDrag", 0xFFE0B04A),
		NDRAG("ndrag", "NDrag", 0xFFB07BFF);

		public final String id;
		public final String label;
		public final int color;

		DragonType(String id, String label, int color) {
			this.id = id;
			this.label = label;
			this.color = color;
		}

		public static DragonType byId(String id) {
			for (DragonType t : values()) if (t.id.equals(id)) return t;
			return null;
		}
	}

	public enum Role {
		STUN(0xFFFFD866),
		MELEE(0xFFFF6B5C),
		TERM(0xFF6BE675),
		MAGE(0xFF6FA8FF),
		SUPPORT(0xFFF08CE6);

		public final int color;

		Role(int color) {
			this.color = color;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public String translationKey() {
			return "golemaddons.pf.role." + id();
		}

		public static Role byId(String id) {
			for (Role r : values()) if (r.id().equals(id)) return r;
			return null;
		}
	}

	public enum Status {
		OPEN("open"), FULL("full"), IN_PROGRESS("in_progress");

		public final String id;

		Status(String id) {
			this.id = id;
		}

		public static Status byId(String id) {
			for (Status s : values()) if (s.id.equals(id)) return s;
			return null;
		}
	}

	public record Member(UUID uuid, String name, Role role) {
	}

	/** {@code size} counts the leader; {@code open} is role slots still free; both come from the server. */
	public record Party(String id, DragonType type, String name, String desc, int max, int size,
			EnumMap<Role, Integer> roles, EnumMap<Role, Integer> open, boolean autoAccept, Status status,
			UUID leaderId, String leaderName, List<Member> members, long createdAt, boolean leaderOnline) {

		public int openRoleSlots() {
			int n = 0;
			for (int v : open.values()) n += v;
			return n;
		}

		/** Places nobody reserved for a role: what someone joining as "any role" can take. */
		public int freeUnreserved() {
			return max - size - openRoleSlots();
		}

		/** Whether a player could take a place as {@code role} (null = any). Mirrors the server's rule. */
		public boolean canTake(Role role) {
			if (status != Status.OPEN || size >= max) return false;
			return (role != null && open.getOrDefault(role, 0) > 0) || freeUnreserved() > 0;
		}

		public boolean hasMember(UUID uuid) {
			for (Member m : members) if (m.uuid().equals(uuid)) return true;
			return false;
		}
	}

	public record JoinRequest(String id, String party, UUID uuid, String name, Role role, long at) {
	}

	/** What a leader sets when creating or editing. */
	public record Settings(DragonType type, String name, String desc, int max, EnumMap<Role, Integer> roles,
			boolean autoAccept) {

		public int roleTotal() {
			int n = 0;
			for (int v : roles.values()) n += v;
			return n;
		}
	}

	public static EnumMap<Role, Integer> emptyRoles() {
		EnumMap<Role, Integer> m = new EnumMap<>(Role.class);
		for (Role r : Role.values()) m.put(r, 0);
		return m;
	}
}
