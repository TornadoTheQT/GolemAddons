package com.golemaddons.partyfinder;

import com.golemaddons.partyfinder.PartyModels.DragonType;
import com.golemaddons.partyfinder.PartyModels.Party;
import com.golemaddons.partyfinder.PartyModels.Role;
import com.golemaddons.partyfinder.PartyModels.Status;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Search, filters, sorting and paging for the browse list. Plain Java, unit tested. */
public final class PartyQuery {
	public enum Sort {
		NEWEST("newest"), OLDEST("oldest"), MOST_PLAYERS("most_players"), FEWEST_PLAYERS("fewest_players"), MOST_OPEN("most_open");

		public final String id;

		Sort(String id) {
			this.id = id;
		}

		public static Sort byId(String id) {
			for (Sort s : values()) if (s.id.equals(id)) return s;
			return NEWEST;
		}
	}

	/** {@code type} null = both; {@code roles} empty = any; a party matches if it has an open slot for any listed role. */
	public record Filter(DragonType type, String search, Set<Role> roles, boolean openOnly, Sort sort) {
	}

	public record Page<T>(List<T> items, int page, int pages, int total) {
	}

	private PartyQuery() {
	}

	public static List<Party> apply(Collection<Party> parties, Filter f) {
		String needle = f.search() == null ? "" : f.search().strip().toLowerCase(Locale.ROOT);
		Set<Role> roles = f.roles() == null || f.roles().isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(f.roles());
		List<Party> out = new ArrayList<>();
		for (Party p : parties) {
			if (f.type() != null && p.type() != f.type()) continue;
			if (f.openOnly() && p.status() != Status.OPEN) continue;
			if (!roles.isEmpty() && roles.stream().noneMatch(r -> p.open().getOrDefault(r, 0) > 0)) continue;
			if (!needle.isEmpty() && !matches(p, needle)) continue;
			out.add(p);
		}
		out.sort(comparator(f.sort()));
		return out;
	}

	private static boolean matches(Party p, String needle) {
		if (p.name().toLowerCase(Locale.ROOT).contains(needle) || p.desc().toLowerCase(Locale.ROOT).contains(needle)
				|| p.leaderName().toLowerCase(Locale.ROOT).contains(needle)) {
			return true;
		}
		return p.members().stream().anyMatch(m -> m.name().toLowerCase(Locale.ROOT).contains(needle));
	}

	static Comparator<Party> comparator(Sort sort) {
		// Open parties first in every order, then the chosen key, then newest, then id for a stable order.
		Comparator<Party> openFirst = Comparator.comparingInt(p -> p.status() == Status.OPEN ? 0 : 1);
		Comparator<Party> key = switch (sort) {
			case NEWEST -> Comparator.comparingLong(Party::createdAt).reversed();
			case OLDEST -> Comparator.comparingLong(Party::createdAt);
			case MOST_PLAYERS -> Comparator.comparingInt(Party::size).reversed();
			case FEWEST_PLAYERS -> Comparator.comparingInt(Party::size);
			case MOST_OPEN -> Comparator.comparingInt((Party p) -> p.max() - p.size()).reversed();
		};
		return openFirst.thenComparing(key).thenComparing(Comparator.comparingLong(Party::createdAt).reversed())
				.thenComparing(Party::id);
	}

	public static <T> Page<T> page(List<T> items, int page, int size) {
		int pages = Math.max(1, (items.size() + size - 1) / size);
		int p = Math.clamp(page, 0, pages - 1);
		int from = p * size;
		return new Page<>(items.subList(from, Math.min(items.size(), from + size)), p, pages, items.size());
	}
}
