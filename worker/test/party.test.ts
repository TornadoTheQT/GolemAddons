import { beforeEach, describe, expect, it } from "vitest";
import { cleanText, LIMITS, PartyBoard, type Outbox } from "../src/party";

const LEADER = "a".repeat(32);
const BOB = "b".repeat(32);
const CAT = "c".repeat(32);
const DAN = "d".repeat(32);

type Msg = Record<string, any>;

function setup() {
	const clock = { now: 1_000_000 };
	const inbox = new Map<string, Msg[]>();
	const online = new Set<string>();
	const broadcasts: Msg[] = [];
	const out: Outbox = {
		send: (uuid, m) => {
			if (!inbox.has(uuid)) inbox.set(uuid, []);
			inbox.get(uuid)!.push(JSON.parse(JSON.stringify(m)));
		},
		broadcast: (m) => broadcasts.push(JSON.parse(JSON.stringify(m))),
		isOnline: (uuid) => online.has(uuid),
	};
	let n = 0;
	const board = new PartyBoard(() => clock.now, out, undefined, () => `party${String(n++).padStart(5, "0")}`);
	const connect = (uuid: string, name: string) => {
		online.add(uuid);
		board.connect(uuid, name);
	};
	const last = (uuid: string) => {
		const list = inbox.get(uuid) ?? [];
		return list[list.length - 1];
	};
	const all = (uuid: string) => inbox.get(uuid) ?? [];
	const send = (uuid: string, m: Msg) => {
		board.handle(uuid, m);
		return last(uuid);
	};
	const create = (overrides: Msg = {}) => send(LEADER, {
		t: "create", type: "gdrag", name: "Gold run", desc: "chill", max: 4, roles: { stun: 1 }, autoAccept: false, ...overrides,
	});
	return { board, clock, online, broadcasts, connect, last, all, send, create };
}

let s: ReturnType<typeof setup>;
beforeEach(() => {
	s = setup();
	s.connect(LEADER, "Leader");
	s.connect(BOB, "Bob");
	s.connect(CAT, "Cat");
});

describe("creating", () => {
	it("creates a party and lists it for everyone", () => {
		const res = s.create({ ref: 7 });
		expect(res).toMatchObject({ t: "created", ref: 7, party: { id: "party00000", size: 1, status: "open", leader: { uuid: LEADER, name: "Leader" } } });
		expect(s.broadcasts.at(-1)).toMatchObject({ t: "upsert", party: { id: "party00000" } });
		const list = s.send(BOB, { t: "list" });
		expect(list.parties).toHaveLength(1);
		expect(list.parties[0].open).toEqual({ stun: 1, melee: 0, term: 0, mage: 0, support: 0 });
	});

	it("allows one party per leader", () => {
		s.create();
		expect(s.create()).toMatchObject({ t: "error", code: "already_leading" });
	});

	it("validates every field", () => {
		expect(s.create({ type: "edrag" }).code).toBe("bad_request");
		expect(s.create({ max: 7 }).code).toBe("bad_request");
		expect(s.create({ max: 0 }).code).toBe("bad_request");
		expect(s.create({ max: 2.5 }).code).toBe("bad_request");
		expect(s.create({ name: "   " }).code).toBe("text_empty");
		expect(s.create({ name: "x".repeat(33) }).code).toBe("text_too_long");
		expect(s.create({ desc: "join discord.gg/abc" }).code).toBe("text_contact_info");
		expect(s.create({ name: "§cRed" }).code).toBe("text_invalid");
		expect(s.create({ roles: { healer: 1 } }).code).toBe("bad_request");
		expect(s.create({ roles: { stun: 6 } }).code).toBe("bad_request");
		expect(s.create({ max: 3, roles: { stun: 2, mage: 1 } }).code).toBe("too_many_roles");
		expect(s.create({ extra: 1 }).code).toBe("bad_request");
	});

	it("can be edited, but not below the current size", () => {
		s.create({ max: 3, roles: {} });
		s.send(BOB, { t: "join", party: "party00000" });
		s.send(LEADER, { t: "respond", request: "1", accept: true });
		expect(s.send(LEADER, { t: "edit", max: 1 }).code).toBe("too_small");
		expect(s.send(LEADER, { t: "edit", name: "New name", ref: 2 })).toEqual({ t: "ok", ref: 2 });
		expect(s.broadcasts.at(-1)!.party).toMatchObject({ name: "New name", max: 3, size: 2 });
		expect(s.send(BOB, { t: "edit", name: "hijack" }).code).toBe("not_leader");
	});
});

describe("joining", () => {
	it("request, accept, invite: the leader gets the request and the invite, the player the result", () => {
		s.create();
		expect(s.send(BOB, { t: "join", party: "party00000", role: "stun" })).toMatchObject({ t: "pending", request: { id: "1", uuid: BOB, name: "Bob", role: "stun" } });
		expect(s.last(LEADER)).toMatchObject({ t: "request", request: { id: "1", uuid: BOB } });
		s.send(LEADER, { t: "respond", request: "1", accept: true });
		expect(s.all(LEADER).some((m) => m.t === "invite" && m.request.uuid === BOB)).toBe(true);
		expect(s.all(BOB).at(-1)).toMatchObject({ t: "result", kind: "accepted", leader: "Leader" });
		const party = s.send(CAT, { t: "list" }).parties[0];
		expect(party).toMatchObject({ size: 2, open: { stun: 0 }, members: [{ uuid: BOB, name: "Bob", role: "stun" }] });
	});

	it("auto-accept invites straight away", () => {
		s.create({ autoAccept: true });
		s.send(BOB, { t: "join", party: "party00000" });
		expect(s.all(LEADER).some((m) => m.t === "invite")).toBe(true);
		expect(s.all(LEADER).some((m) => m.t === "request")).toBe(false);
	});

	it("respects role slots and size", () => {
		s.create({ max: 3, roles: { stun: 1, mage: 1 } });
		expect(s.send(BOB, { t: "join", party: "party00000" }).code).toBe("role_full"); // both places reserved
		expect(s.send(BOB, { t: "join", party: "party00000", role: "term" }).code).toBe("role_full");
		expect(s.send(BOB, { t: "join", party: "party00000", role: "stun" }).t).toBe("pending");
		s.send(LEADER, { t: "respond", request: "1", accept: true });
		expect(s.send(CAT, { t: "join", party: "party00000", role: "stun" }).code).toBe("role_full");
		expect(s.send(CAT, { t: "join", party: "party00000", role: "mage" }).t).toBe("pending");
		s.send(LEADER, { t: "respond", request: "2", accept: true });
		s.connect(DAN, "Dan");
		expect(s.send(DAN, { t: "join", party: "party00000", role: "mage" }).code).toBe("full");
		expect(s.send(DAN, { t: "list" }).parties[0].status).toBe("full");
	});

	it("refuses the leader, members, in-progress parties and offline leaders", () => {
		s.create();
		expect(s.send(LEADER, { t: "join", party: "party00000" }).code).toBe("own_party");
		expect(s.send(BOB, { t: "join", party: "nosuchpart" }).code).toBe("no_party");
		expect(s.send(BOB, { t: "join", party: "../x" }).code).toBe("bad_request");
		s.send(LEADER, { t: "status", inProgress: true });
		expect(s.send(BOB, { t: "join", party: "party00000" }).code).toBe("in_progress");
		expect(s.send(BOB, { t: "list" }).parties[0].status).toBe("in_progress");
		s.send(LEADER, { t: "status", inProgress: false });
		s.online.delete(LEADER);
		expect(s.send(BOB, { t: "join", party: "party00000" }).code).toBe("leader_offline");
	});

	it("one pending request at a time, and a cooldown after a denial", () => {
		s.create();
		s.send(BOB, { t: "join", party: "party00000" });
		expect(s.send(BOB, { t: "join", party: "party00000" }).code).toBe("already_requested");
		s.send(LEADER, { t: "respond", request: "1", accept: false });
		expect(s.all(BOB).at(-1)).toMatchObject({ t: "result", kind: "denied" });
		expect(s.send(BOB, { t: "join", party: "party00000" }).code).toBe("cooldown");
		s.clock.now += LIMITS.retryCooldownMs;
		expect(s.send(BOB, { t: "join", party: "party00000" }).t).toBe("pending");
	});

	it("requests expire and the requester is told", () => {
		s.create();
		s.send(BOB, { t: "join", party: "party00000" });
		expect(s.board.tick()).toBe(s.clock.now + LIMITS.requestTtlMs);
		s.clock.now += LIMITS.requestTtlMs;
		s.board.tick();
		expect(s.all(BOB).at(-1)).toMatchObject({ t: "result", kind: "expired" });
		expect(s.last(LEADER)).toEqual({ t: "request_gone", id: "1" });
	});

	it("only the leader of that party can answer, and only its own requests", () => {
		s.create();
		s.send(BOB, { t: "join", party: "party00000" });
		expect(s.send(CAT, { t: "respond", request: "1", accept: true }).code).toBe("not_leader");
		s.connect(DAN, "Dan");
		s.send(DAN, { t: "create", type: "ndrag", name: "Other", desc: "", max: 6, roles: {}, autoAccept: false });
		expect(s.send(DAN, { t: "respond", request: "1", accept: true }).code).toBe("no_request");
	});

	it("accepting into a new party removes you from the old one", () => {
		s.create({ max: 6, roles: {} });
		s.send(BOB, { t: "join", party: "party00000" });
		s.send(LEADER, { t: "respond", request: "1", accept: true });
		s.send(CAT, { t: "create", type: "ndrag", name: "Cat's", desc: "", max: 6, roles: {}, autoAccept: true });
		s.send(BOB, { t: "join", party: "party00001" });
		const parties = s.send(BOB, { t: "list" }).parties;
		expect(parties.find((p: Msg) => p.id === "party00000").size).toBe(1);
		expect(parties.find((p: Msg) => p.id === "party00001").size).toBe(2);
		expect(s.all(LEADER).some((m) => m.t === "left" && m.uuid === BOB)).toBe(true);
	});

	it("limits pending requests per party", () => {
		s.create({ max: 6, roles: {} });
		for (let i = 0; i < LIMITS.requestsPerParty; i++) {
			const id = String(i).padStart(32, "e");
			s.connect(id, "P" + i);
			expect(s.send(id, { t: "join", party: "party00000" }).t).toBe("pending");
		}
		s.connect(DAN, "Dan");
		expect(s.send(DAN, { t: "join", party: "party00000" }).code).toBe("busy");
	});
});

describe("members and leaving", () => {
	beforeEach(() => {
		s.create({ max: 6, roles: {} });
		s.send(BOB, { t: "join", party: "party00000" });
		s.send(LEADER, { t: "respond", request: "1", accept: true });
	});

	it("leader can kick; a failed invite tells the player why", () => {
		s.send(LEADER, { t: "kick", uuid: BOB, reason: "not_online" });
		expect(s.all(BOB).at(-1)).toMatchObject({ t: "result", kind: "invite_failed" });
		expect(s.send(CAT, { t: "list" }).parties[0].size).toBe(1);
		expect(s.send(LEADER, { t: "kick", uuid: CAT }).code).toBe("not_member");
		expect(s.send(LEADER, { t: "kick", uuid: BOB, reason: "anything" }).code).toBe("bad_request");
	});

	it("members can leave", () => {
		expect(s.send(BOB, { t: "leave", ref: 3 })).toEqual({ t: "ok", ref: 3 });
		expect(s.send(BOB, { t: "leave" }).code).toBe("not_member");
	});

	it("disbanding tells members and removes the listing", () => {
		s.send(LEADER, { t: "disband" });
		expect(s.all(BOB).at(-1)).toMatchObject({ t: "result", kind: "disbanded" });
		expect(s.broadcasts.at(-1)).toEqual({ t: "remove", id: "party00000" });
		expect(s.send(CAT, { t: "list" }).parties).toHaveLength(0);
	});
});

describe("presence", () => {
	it("removes a party after its leader is gone for the grace period, unless they come back", () => {
		s.create();
		s.online.delete(LEADER);
		s.board.disconnect(LEADER);
		expect(s.send(BOB, { t: "list" }).parties[0].leaderOnline).toBe(false);
		s.clock.now += LIMITS.leaderGraceMs - 1;
		s.board.tick();
		s.connect(LEADER, "Leader");
		s.clock.now += LIMITS.leaderGraceMs;
		s.board.tick();
		expect(s.send(BOB, { t: "list" }).parties).toHaveLength(1);

		s.online.delete(LEADER);
		s.board.disconnect(LEADER);
		s.clock.now += LIMITS.leaderGraceMs;
		s.board.tick();
		expect(s.send(BOB, { t: "list" }).parties).toHaveLength(0);
	});

	it("drops a requester's requests when they disconnect", () => {
		s.create();
		s.send(BOB, { t: "join", party: "party00000" });
		s.board.disconnect(BOB);
		expect(s.last(LEADER)).toEqual({ t: "request_gone", id: "1" });
	});

	it("survives a restart through its snapshot", () => {
		s.create();
		s.send(BOB, { t: "join", party: "party00000" });
		const out: Outbox = { send() {}, broadcast() {}, isOnline: () => true };
		const restored = new PartyBoard(() => s.clock.now, out, JSON.parse(JSON.stringify(s.board.snapshot())));
		expect(restored.snapshot().parties).toHaveLength(1);
		expect(restored.snapshot().requests).toHaveLength(1);
		expect(new PartyBoard(() => 0, out, { v: 1, parties: [{ id: "../bad" }], requests: [] }).snapshot().parties).toHaveLength(0);
	});
});

describe("abuse", () => {
	it("rate limits message floods", () => {
		let limited = 0;
		for (let i = 0; i < LIMITS.burst + 5; i++) if (s.send(BOB, { t: "list" }).code === "rate_limited") limited++;
		expect(limited).toBe(5);
		s.clock.now += 1_000;
		expect(s.send(BOB, { t: "list" }).t).toBe("parties");
	});

	it("rate limits create/edit churn", () => {
		s.create();
		let limited = false;
		for (let i = 0; i < LIMITS.editsPerMinute + 1; i++) {
			if (s.send(LEADER, { t: "edit", name: "n" + i }).code === "rate_limited") limited = true;
		}
		expect(limited).toBe(true);
	});

	it("rejects malformed messages", () => {
		expect(s.send(BOB, null as unknown as Msg).code).toBe("bad_request");
		expect(s.send(BOB, { t: "nope" }).code).toBe("bad_request");
		expect(s.send(BOB, { t: "list", ref: -1 }).code).toBe("bad_request");
		expect(s.send(BOB, { t: "join", party: "party00000", role: "king" }).code).toBe("bad_request");
	});
});

describe("cleanText", () => {
	it("collapses whitespace and rejects contact info and control characters", () => {
		expect(cleanText("  need   stun  ", 32, false)).toEqual({ ok: true, text: "need stun" });
		for (const bad of ["play.example.com", "1.2.3.4", "me@mail.com", "call 555 123 4567", "https://x", "a‮b"]) {
			expect(cleanText(bad, 120, true).ok).toBe(false);
		}
		for (const fine of ["Need a mage. Message me", "GDrag 4/6, chill", "lvl 300+ only"]) {
			expect(cleanText(fine, 120, true).ok).toBe(true);
		}
	});
});
