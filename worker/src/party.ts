// Dragon Party Finder rules, as plain synchronous logic with an injected clock and outbox so every rule is unit
// tested without a runtime. One PartyBoard per Minecraft server (see partyRoom.ts); every player is identified by
// the UUID proven at sign-in, never by anything they send.
//
// Trust model: clients may be modified and malicious. Everything they send is validated here; member counts,
// statuses and open slots are computed here; a player can only ever ask to join as themselves. The leader's game runs
// /p with the name it sees for that UUID in its own tab list, so nobody can make a leader invite someone else.

export const ROLES = ["stun", "melee", "term", "mage", "support"] as const;
export type Role = (typeof ROLES)[number];
export const DRAGON_TYPES = ["gdrag", "ndrag"] as const;
export type DragonType = (typeof DRAGON_TYPES)[number];

export const LIMITS = {
	maxParties: 150,
	nameMax: 32,
	descMax: 120,
	minSize: 1,
	maxSize: 6,
	maxRoleSlots: 5,
	/** Pending join requests a party can have at once. */
	requestsPerParty: 8,
	requestTtlMs: 60_000,
	/** After a denial or expiry, the same player can't ask the same party again for this long. */
	retryCooldownMs: 30_000,
	/** A party whose leader dropped is removed after this long, unless they reconnect. */
	leaderGraceMs: 60_000,
	/** Messages per player: a burst of 20, refilling 2 per second. */
	burst: 20,
	refillPerSecond: 2,
	/** Create/edit/status changes per player per minute. */
	editsPerMinute: 10,
	joinsPerMinute: 8,
} as const;

export type RoleCounts = Record<Role, number>;

export interface Member {
	uuid: string;
	name: string;
	role: Role | null;
	joinedAt: number;
}

export interface Party {
	id: string;
	type: DragonType;
	name: string;
	desc: string;
	max: number;
	roles: RoleCounts;
	autoAccept: boolean;
	inProgress: boolean;
	leader: { uuid: string; name: string };
	members: Member[];
	createdAt: number;
	updatedAt: number;
	leaderOfflineSince: number | null;
}

export interface JoinRequest {
	id: string;
	party: string;
	uuid: string;
	name: string;
	role: Role | null;
	at: number;
}

export interface BoardState {
	v: 1;
	parties: Party[];
	requests: JoinRequest[];
	nextRequest: number;
}

/** How the board talks to players. send/broadcast must never throw. */
export interface Outbox {
	send(uuid: string, message: object): void;
	broadcast(message: object): void;
	isOnline(uuid: string): boolean;
}

const NAME = /^[A-Za-z0-9_]{1,16}$/;
const PARTY_ID = /^[a-z0-9]{10}$/;
// Mirrors the mod's cosmetic-name rule: no links, IP addresses, emails or phone numbers in shared text.
const CONTACT_INFO = [
	/\b(?:https?|ftp):\/\//i,
	/\bwww\./i,
	/\b[a-z0-9-]{2,}(?:\.|\s*\(dot\)\s*|\s*\[dot\]\s*)(?:com|net|org|gg|io|me|co|xyz|tk|ly|de|uk|ru|cc|tv|us|info|biz|link|site|online|club|app|dev)\b/i,
	/\bdiscord(?:\.gg|app\.com|\.com\/invite)\b/i,
	/\b\d{1,3}(?:\s*[.,]\s*\d{1,3}){3}\b/,
	/\b[0-9a-f]{1,4}(?::[0-9a-f]{0,4}){4,7}\b/i,
	/[a-z0-9._%+-]+\s*(?:@|\(at\)|\[at\])\s*[a-z0-9-]+\s*(?:\.|\(dot\)|\[dot\])\s*[a-z]{2,}/i,
	/(?:\d[\s().-]*){7,}/,
];

export function validPlayerName(name: unknown): name is string {
	return typeof name === "string" && NAME.test(name);
}

/** Cleans a free-text field: trims, collapses whitespace, rejects control/format characters, § and contact info. */
export function cleanText(raw: unknown, max: number, allowEmpty: boolean): { ok: true; text: string } | { ok: false; problem: string } {
	if (typeof raw !== "string") return { ok: false, problem: "bad_request" };
	const text = raw.replace(/\s+/g, " ").trim();
	if (!allowEmpty && text.length === 0) return { ok: false, problem: "text_empty" };
	if ([...text].length > max) return { ok: false, problem: "text_too_long" };
	if (/[\p{Cc}\p{Cf}\p{Co}\p{Cn}§�]/u.test(text)) return { ok: false, problem: "text_invalid" };
	if (CONTACT_INFO.some((r) => r.test(text))) return { ok: false, problem: "text_contact_info" };
	return { ok: true, text };
}

function emptyRoles(): RoleCounts {
	return { stun: 0, melee: 0, term: 0, mage: 0, support: 0 };
}

function isRole(v: unknown): v is Role {
	return typeof v === "string" && (ROLES as readonly string[]).includes(v);
}

function isInt(v: unknown, lo: number, hi: number): v is number {
	return typeof v === "number" && Number.isInteger(v) && v >= lo && v <= hi;
}

/** Party size counts the leader. Open slots per role subtract members holding that role. */
export function status(p: Party): "open" | "full" | "in_progress" {
	if (p.inProgress) return "in_progress";
	return p.members.length + 1 >= p.max ? "full" : "open";
}

export function openRoleSlots(p: Party): RoleCounts {
	const out = emptyRoles();
	for (const r of ROLES) out[r] = Math.max(0, p.roles[r] - p.members.filter((m) => m.role === r).length);
	return out;
}

/** Places nobody has reserved for a role: what a player joining without a role can take. */
export function freeUnreservedPlaces(p: Party): number {
	const open = openRoleSlots(p);
	const reservedOpen = ROLES.reduce((n, r) => n + open[r], 0);
	return p.max - 1 - p.members.length - reservedOpen;
}

function canTake(p: Party, role: Role | null): boolean {
	if (p.members.length + 1 >= p.max) return false;
	return role ? openRoleSlots(p)[role] > 0 || freeUnreservedPlaces(p) > 0 : freeUnreservedPlaces(p) > 0;
}

/** What clients see. Internal fields (offline timestamps) stay private. */
export function view(p: Party): object {
	return {
		id: p.id,
		type: p.type,
		name: p.name,
		desc: p.desc,
		max: p.max,
		size: p.members.length + 1,
		roles: p.roles,
		open: openRoleSlots(p),
		autoAccept: p.autoAccept,
		status: status(p),
		leader: p.leader,
		members: p.members.map((m) => ({ uuid: m.uuid, name: m.name, role: m.role })),
		createdAt: p.createdAt,
		leaderOnline: p.leaderOfflineSince === null,
	};
}

class Bucket {
	constructor(public tokens: number, public at: number) {}
}

class ApiProblem extends Error {
	constructor(public code: string) {
		super(code);
	}
}

export class PartyBoard {
	private readonly parties = new Map<string, Party>();
	private readonly requests = new Map<string, JoinRequest>();
	private readonly names = new Map<string, string>();
	private readonly messageBuckets = new Map<string, Bucket>();
	private readonly windows = new Map<string, number[]>();
	private readonly cooldowns = new Map<string, number>();
	private nextRequest = 1;
	/** True when something worth persisting changed since the last {@link takeDirty}. */
	private dirty = false;

	constructor(private readonly now: () => number, private readonly out: Outbox, state?: unknown,
		private readonly randomId: () => string = defaultId) {
		this.restore(state);
	}

	// ---- persistence ----

	snapshot(): BoardState {
		return { v: 1, parties: [...this.parties.values()], requests: [...this.requests.values()], nextRequest: this.nextRequest };
	}

	takeDirty(): boolean {
		const d = this.dirty;
		this.dirty = false;
		return d;
	}

	/** Stored state is trusted only as far as it re-validates. */
	private restore(state: unknown): void {
		if (!state || typeof state !== "object") return;
		const s = state as Partial<BoardState>;
		if (s.v !== 1 || !Array.isArray(s.parties) || !Array.isArray(s.requests)) return;
		for (const p of s.parties) {
			if (p && typeof p === "object" && PARTY_ID.test(p.id) && validPlayerName(p.leader?.name)) this.parties.set(p.id, p);
		}
		for (const r of s.requests) {
			if (r && typeof r === "object" && this.parties.has(r.party)) this.requests.set(r.id, r);
		}
		this.nextRequest = isInt(s.nextRequest, 1, Number.MAX_SAFE_INTEGER) ? s.nextRequest : 1;
	}

	// ---- presence ----

	connect(uuid: string, name: string): void {
		this.names.set(uuid, name);
		const led = this.partyLedBy(uuid);
		if (led) {
			const changed = led.leaderOfflineSince !== null || led.leader.name !== name;
			led.leaderOfflineSince = null;
			led.leader.name = name;
			if (changed) this.changed(led);
		}
		this.out.send(uuid, this.welcome(uuid));
	}

	/** The player's last connection closed. */
	disconnect(uuid: string): void {
		const led = this.partyLedBy(uuid);
		if (led && led.leaderOfflineSince === null) {
			led.leaderOfflineSince = this.now();
			this.changed(led);
		}
		// They can't hear the answer any more: drop what they asked for.
		for (const r of [...this.requests.values()]) {
			if (r.uuid === uuid) this.dropRequest(r, null);
		}
		this.messageBuckets.delete(uuid);
	}

	/** Expires requests and parties whose leader is gone. Returns when it next needs to run, or null. */
	tick(): number | null {
		const now = this.now();
		let next: number | null = null;
		const soon = (t: number) => {
			next = next === null ? t : Math.min(next, t);
		};
		for (const r of [...this.requests.values()]) {
			const due = r.at + LIMITS.requestTtlMs;
			if (due <= now) {
				this.cooldowns.set(`${r.uuid}:${r.party}`, now + LIMITS.retryCooldownMs);
				this.dropRequest(r, "expired");
			} else {
				soon(due);
			}
		}
		for (const p of [...this.parties.values()]) {
			if (p.leaderOfflineSince === null) continue;
			const due = p.leaderOfflineSince + LIMITS.leaderGraceMs;
			if (due <= now) this.removeParty(p, "leader_left");
			else soon(due);
		}
		for (const [k, until] of this.cooldowns) if (until <= now) this.cooldowns.delete(k);
		return next;
	}

	// ---- messages ----

	/** Handles one message from a signed-in player. Never throws; problems go back to that player as errors. */
	handle(uuid: string, raw: unknown, name?: string): void {
		let ref: number | undefined;
		// The room passes the name each time: after hibernation the in-memory map starts empty.
		if (name !== undefined && validPlayerName(name)) this.names.set(uuid, name);
		try {
			if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new ApiProblem("bad_request");
			const msg = raw as Record<string, unknown>;
			if (msg.ref !== undefined && !isInt(msg.ref, 0, 1_000_000_000)) throw new ApiProblem("bad_request");
			ref = msg.ref as number | undefined;
			this.spend(uuid);
			switch (msg.t) {
				case "list": return this.reply(uuid, ref, this.welcome(uuid));
				case "create": return this.create(uuid, msg, ref);
				case "edit": return this.edit(uuid, msg, ref);
				case "status": return this.setInProgress(uuid, msg, ref);
				case "disband": return this.disband(uuid, msg, ref);
				case "join": return this.join(uuid, msg, ref);
				case "cancel": return this.cancel(uuid, msg, ref);
				case "respond": return this.respond(uuid, msg, ref);
				case "kick": return this.kick(uuid, msg, ref);
				case "leave": return this.leave(uuid, msg, ref);
				default: throw new ApiProblem("bad_request");
			}
		} catch (e) {
			const code = e instanceof ApiProblem ? e.code : "internal";
			const err: Record<string, unknown> = { t: "error", code };
			if (ref !== undefined) err.ref = ref;
			this.out.send(uuid, err);
		}
	}

	private welcome(uuid: string): object {
		const led = this.partyLedBy(uuid);
		return {
			t: "parties",
			you: uuid,
			parties: [...this.parties.values()].map(view),
			requests: led ? [...this.requests.values()].filter((r) => r.party === led.id).map(requestView) : [],
			pending: [...this.requests.values()].filter((r) => r.uuid === uuid).map(requestView),
			limits: { maxSize: LIMITS.maxSize, nameMax: LIMITS.nameMax, descMax: LIMITS.descMax, maxRoleSlots: LIMITS.maxRoleSlots },
		};
	}

	private create(uuid: string, msg: Record<string, unknown>, ref?: number): void {
		allowKeys(msg, ["t", "ref", "type", "name", "desc", "max", "roles", "autoAccept"]);
		if (this.partyLedBy(uuid)) throw new ApiProblem("already_leading");
		if (this.parties.size >= LIMITS.maxParties) throw new ApiProblem("board_full");
		const name = this.names.get(uuid);
		if (!name) throw new ApiProblem("unauthorized");
		const fields = parseSettings(msg, null);
		// Counted only once valid: the mod checks fields first, so typos don't use up the allowance.
		this.window(uuid, "edit", LIMITS.editsPerMinute);
		const now = this.now();
		// Leading means you're no longer someone else's member.
		const current = this.partyWithMember(uuid);
		if (current) this.removeMember(current, uuid, "left");
		for (const r of [...this.requests.values()]) if (r.uuid === uuid) this.dropRequest(r, "cancelled");
		let id = this.randomId();
		while (this.parties.has(id)) id = this.randomId();
		const party: Party = {
			id, ...fields, inProgress: false, leader: { uuid, name }, members: [],
			createdAt: now, updatedAt: now, leaderOfflineSince: null,
		};
		this.parties.set(id, party);
		this.changed(party);
		this.reply(uuid, ref, { t: "created", party: view(party) });
	}

	private edit(uuid: string, msg: Record<string, unknown>, ref?: number): void {
		allowKeys(msg, ["t", "ref", "type", "name", "desc", "max", "roles", "autoAccept"]);
		const party = this.requireLed(uuid);
		const fields = parseSettings(msg, party);
		if (fields.max < party.members.length + 1) throw new ApiProblem("too_small");
		this.window(uuid, "edit", LIMITS.editsPerMinute);
		Object.assign(party, fields);
		this.changed(party);
		this.reply(uuid, ref, { t: "ok" });
		if (party.autoAccept) this.acceptAllPending(party);
	}

	private setInProgress(uuid: string, msg: Record<string, unknown>, ref?: number): void {
		allowKeys(msg, ["t", "ref", "inProgress"]);
		if (typeof msg.inProgress !== "boolean") throw new ApiProblem("bad_request");
		this.window(uuid, "edit", LIMITS.editsPerMinute);
		const party = this.requireLed(uuid);
		if (party.inProgress !== msg.inProgress) {
			party.inProgress = msg.inProgress;
			if (party.inProgress) for (const r of this.requestsFor(party)) this.dropRequest(r, "in_progress");
			this.changed(party);
		}
		this.reply(uuid, ref, { t: "ok" });
	}

	private disband(uuid: string, msg: Record<string, unknown>, ref?: number): void {
		allowKeys(msg, ["t", "ref"]);
		this.removeParty(this.requireLed(uuid), "disbanded");
		this.reply(uuid, ref, { t: "ok" });
	}

	private join(uuid: string, msg: Record<string, unknown>, ref?: number): void {
		allowKeys(msg, ["t", "ref", "party", "role"]);
		const role = msg.role === undefined || msg.role === null ? null : msg.role;
		if (typeof msg.party !== "string" || !PARTY_ID.test(msg.party) || (role !== null && !isRole(role))) throw new ApiProblem("bad_request");
		this.window(uuid, "join", LIMITS.joinsPerMinute);
		const party = this.parties.get(msg.party);
		if (!party) throw new ApiProblem("no_party");
		const name = this.names.get(uuid);
		if (!name) throw new ApiProblem("unauthorized");
		if (party.leader.uuid === uuid) throw new ApiProblem("own_party");
		if (this.partyLedBy(uuid)) throw new ApiProblem("already_leading");
		if (party.members.some((m) => m.uuid === uuid)) throw new ApiProblem("already_member");
		if (party.inProgress) throw new ApiProblem("in_progress");
		if (!canTake(party, role)) throw new ApiProblem(party.members.length + 1 >= party.max ? "full" : "role_full");
		if ([...this.requests.values()].some((r) => r.uuid === uuid)) throw new ApiProblem("already_requested");
		const cooldown = this.cooldowns.get(`${uuid}:${party.id}`);
		if (cooldown !== undefined && cooldown > this.now()) throw new ApiProblem("cooldown");
		if (party.leaderOfflineSince !== null || !this.out.isOnline(party.leader.uuid)) throw new ApiProblem("leader_offline");
		if (this.requestsFor(party).length >= LIMITS.requestsPerParty) throw new ApiProblem("busy");

		const request: JoinRequest = { id: String(this.nextRequest++), party: party.id, uuid, name, role, at: this.now() };
		this.requests.set(request.id, request);
		this.dirty = true;
		this.reply(uuid, ref, { t: "pending", request: requestView(request) });
		if (party.autoAccept) {
			this.accept(party, request);
		} else {
			this.out.send(party.leader.uuid, { t: "request", request: requestView(request) });
		}
	}

	private cancel(uuid: string, msg: Record<string, unknown>, ref?: number): void {
		allowKeys(msg, ["t", "ref"]);
		for (const r of [...this.requests.values()]) if (r.uuid === uuid) this.dropRequest(r, null);
		this.reply(uuid, ref, { t: "ok" });
	}

	private respond(uuid: string, msg: Record<string, unknown>, ref?: number): void {
		allowKeys(msg, ["t", "ref", "request", "accept"]);
		if (typeof msg.request !== "string" || msg.request.length > 16 || typeof msg.accept !== "boolean") throw new ApiProblem("bad_request");
		const party = this.requireLed(uuid);
		const request = this.requests.get(msg.request);
		if (!request || request.party !== party.id) throw new ApiProblem("no_request");
		if (!msg.accept) {
			this.cooldowns.set(`${request.uuid}:${party.id}`, this.now() + LIMITS.retryCooldownMs);
			this.dropRequest(request, "denied");
		} else if (!canTake(party, request.role)) {
			this.dropRequest(request, "full");
			throw new ApiProblem("full");
		} else {
			this.accept(party, request);
		}
		this.reply(uuid, ref, { t: "ok" });
	}

	/** Reserves the place and tells the leader's game to invite (it checks the tab list, then runs /p). */
	private accept(party: Party, request: JoinRequest): void {
		this.requests.delete(request.id);
		const previous = this.partyWithMember(request.uuid);
		if (previous) this.removeMember(previous, request.uuid, "left");
		party.members.push({ uuid: request.uuid, name: request.name, role: request.role, joinedAt: this.now() });
		party.updatedAt = this.now();
		this.changed(party);
		this.out.send(party.leader.uuid, { t: "invite", request: requestView(request) });
		this.out.send(request.uuid, { t: "result", kind: "accepted", party: party.id, leader: party.leader.name });
		if (status(party) !== "open") for (const r of this.requestsFor(party)) if (!canTake(party, r.role)) this.dropRequest(r, "full");
	}

	private acceptAllPending(party: Party): void {
		for (const r of this.requestsFor(party)) {
			if (canTake(party, r.role)) this.accept(party, r);
			else this.dropRequest(r, "full");
		}
	}

	private kick(uuid: string, msg: Record<string, unknown>, ref?: number): void {
		allowKeys(msg, ["t", "ref", "uuid", "reason"]);
		if (typeof msg.uuid !== "string" || !/^[0-9a-f]{32}$/.test(msg.uuid)) throw new ApiProblem("bad_request");
		const reason = msg.reason === undefined ? "kicked" : msg.reason;
		if (reason !== "kicked" && reason !== "not_online" && reason !== "offline") throw new ApiProblem("bad_request");
		const party = this.requireLed(uuid);
		if (!party.members.some((m) => m.uuid === msg.uuid)) throw new ApiProblem("not_member");
		this.removeMember(party, msg.uuid, reason);
		this.reply(uuid, ref, { t: "ok" });
	}

	private leave(uuid: string, msg: Record<string, unknown>, ref?: number): void {
		allowKeys(msg, ["t", "ref"]);
		const party = this.partyWithMember(uuid);
		if (!party) throw new ApiProblem("not_member");
		this.removeMember(party, uuid, "left");
		this.reply(uuid, ref, { t: "ok" });
	}

	// ---- helpers ----

	private removeMember(party: Party, uuid: string, reason: string): void {
		party.members = party.members.filter((m) => m.uuid !== uuid);
		party.updatedAt = this.now();
		this.changed(party);
		if (reason === "left") {
			this.out.send(party.leader.uuid, { t: "left", party: party.id, uuid });
		} else {
			this.out.send(uuid, { t: "result", kind: reason === "not_online" ? "invite_failed" : reason === "offline" ? "removed_offline" : "kicked", party: party.id, leader: party.leader.name });
		}
	}

	private removeParty(party: Party, reason: string): void {
		this.parties.delete(party.id);
		for (const r of this.requestsFor(party)) this.dropRequest(r, reason);
		for (const m of party.members) this.out.send(m.uuid, { t: "result", kind: reason, party: party.id, leader: party.leader.name });
		this.dirty = true;
		this.out.broadcast({ t: "remove", id: party.id });
	}

	/** Removes a request; {@code kind} tells the requester why (null: they cancelled or left, so tell nobody but the leader). */
	private dropRequest(request: JoinRequest, kind: string | null): void {
		if (!this.requests.delete(request.id)) return;
		this.dirty = true;
		const party = this.parties.get(request.party);
		if (party) this.out.send(party.leader.uuid, { t: "request_gone", id: request.id });
		if (kind) this.out.send(request.uuid, { t: "result", kind, party: request.party, leader: party?.leader.name ?? "" });
	}

	private changed(party: Party): void {
		this.dirty = true;
		this.out.broadcast({ t: "upsert", party: view(party) });
	}

	private reply(uuid: string, ref: number | undefined, message: object): void {
		this.out.send(uuid, ref === undefined ? message : { ...message, ref });
	}

	private requireLed(uuid: string): Party {
		const p = this.partyLedBy(uuid);
		if (!p) throw new ApiProblem("not_leader");
		return p;
	}

	private partyLedBy(uuid: string): Party | undefined {
		for (const p of this.parties.values()) if (p.leader.uuid === uuid) return p;
		return undefined;
	}

	private partyWithMember(uuid: string): Party | undefined {
		for (const p of this.parties.values()) if (p.members.some((m) => m.uuid === uuid)) return p;
		return undefined;
	}

	private requestsFor(party: Party): JoinRequest[] {
		return [...this.requests.values()].filter((r) => r.party === party.id);
	}

	/** Token bucket for every message. */
	private spend(uuid: string): void {
		const now = this.now();
		let b = this.messageBuckets.get(uuid);
		if (!b) {
			if (this.messageBuckets.size > 5_000) this.messageBuckets.clear();
			b = new Bucket(LIMITS.burst, now);
			this.messageBuckets.set(uuid, b);
		}
		b.tokens = Math.min(LIMITS.burst, b.tokens + ((now - b.at) / 1000) * LIMITS.refillPerSecond);
		b.at = now;
		if (b.tokens < 1) throw new ApiProblem("rate_limited");
		b.tokens -= 1;
	}

	/** At most {@code limit} actions of a kind per minute. */
	private window(uuid: string, kind: string, limit: number): void {
		const now = this.now();
		const key = `${kind}:${uuid}`;
		const times = (this.windows.get(key) ?? []).filter((t) => t > now - 60_000);
		if (times.length >= limit) throw new ApiProblem("rate_limited");
		times.push(now);
		if (this.windows.size > 10_000) this.windows.clear();
		this.windows.set(key, times);
	}
}

function requestView(r: JoinRequest): object {
	return { id: r.id, party: r.party, uuid: r.uuid, name: r.name, role: r.role, at: r.at };
}

function allowKeys(msg: Record<string, unknown>, allowed: string[]): void {
	for (const k of Object.keys(msg)) if (!allowed.includes(k)) throw new ApiProblem("bad_request");
}

/** Party settings from a create (all required) or edit (missing fields keep {@code current}'s values). */
function parseSettings(msg: Record<string, unknown>, current: Party | null) {
	const pick = <T>(key: string, fallback: T | undefined): unknown => (key in msg ? msg[key] : fallback);
	const type = pick("type", current?.type);
	const max = pick("max", current?.max);
	const autoAccept = pick("autoAccept", current?.autoAccept ?? false);
	const rawRoles = pick("roles", current?.roles ?? emptyRoles());
	if (typeof type !== "string" || !(DRAGON_TYPES as readonly string[]).includes(type)) throw new ApiProblem("bad_request");
	if (!isInt(max, LIMITS.minSize, LIMITS.maxSize) || typeof autoAccept !== "boolean") throw new ApiProblem("bad_request");
	const name = cleanText(pick("name", current?.name), LIMITS.nameMax, false);
	if (!name.ok) throw new ApiProblem(name.problem);
	const desc = cleanText(pick("desc", current?.desc ?? ""), LIMITS.descMax, true);
	if (!desc.ok) throw new ApiProblem(desc.problem);
	if (!rawRoles || typeof rawRoles !== "object" || Array.isArray(rawRoles)) throw new ApiProblem("bad_request");
	const roles = emptyRoles();
	for (const [k, v] of Object.entries(rawRoles)) {
		if (!isRole(k) || !isInt(v, 0, LIMITS.maxRoleSlots)) throw new ApiProblem("bad_request");
		roles[k] = v;
	}
	if (ROLES.reduce((n, r) => n + roles[r], 0) > max - 1) throw new ApiProblem("too_many_roles");
	return { type: type as DragonType, name: name.text, desc: desc.text, max, roles, autoAccept };
}

function defaultId(): string {
	const bytes = crypto.getRandomValues(new Uint8Array(10));
	let s = "";
	for (const b of bytes) s += "abcdefghijklmnopqrstuvwxyz0123456789"[b % 36];
	return s;
}
