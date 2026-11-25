import { beforeEach, describe, expect, it } from "vitest";
import { createToken } from "../src/auth";
import { handle, resetMemoryForTests, type RoomNamespace } from "../src/api";
import { HEARTBEAT_TIMEOUT_MS, PartyRoom, type RoomContext, type SocketLike } from "../src/partyRoom";
import { makeDeps, req, SECRET, UUID } from "./helpers";

class FakeSocket implements SocketLike {
	sent: string[] = [];
	readyState = 1;
	closed: number | null = null;
	private att: unknown = null;
	send(m: string) {
		this.sent.push(m);
	}
	close(code?: number) {
		this.closed = code ?? 1000;
		this.readyState = 3;
	}
	serializeAttachment(v: unknown) {
		this.att = JSON.parse(JSON.stringify(v));
	}
	deserializeAttachment() {
		return this.att;
	}
	messages() {
		return this.sent.map((m) => JSON.parse(m));
	}
}

function makeRoom() {
	const clock = { now: 5_000_000 };
	const sockets: { ws: FakeSocket; tags: string[] }[] = [];
	const store = new Map<string, unknown>();
	let alarm: number | null = null;
	const ctx: RoomContext = {
		acceptWebSocket: (ws, tags = []) => void sockets.push({ ws: ws as FakeSocket, tags }),
		getWebSockets: (tag) => sockets.filter((s) => !tag || s.tags.includes(tag)).map((s) => s.ws),
		getWebSocketAutoResponseTimestamp: () => null,
		setWebSocketAutoResponse: () => {},
		blockConcurrencyWhile: (fn) => fn(),
		storage: {
			get: async (k) => store.get(k),
			put: async (k, v) => void store.set(k, JSON.parse(JSON.stringify(v))),
			delete: async (k) => store.delete(k),
			setAlarm: async (t) => void (alarm = t),
			getAlarm: async () => alarm,
		},
	};
	let last: FakeSocket | null = null;
	const room = new PartyRoom(ctx, {}, {
		now: () => clock.now,
		newPair: () => {
			last = new FakeSocket();
			return { client: last, server: last };
		},
		autoResponse: () => null,
		upgradeResponse: () => new Response(null, { status: 200, headers: { "X-Upgraded": "1" } }),
	});
	const join = async (uuid: string, name: string) => {
		const res = await room.fetch(new Request("https://party-room/ws", { headers: { Upgrade: "websocket", "X-GA-UUID": uuid, "X-GA-Name": name } }));
		expect(res.headers.get("X-Upgraded")).toBe("1");
		return last as unknown as FakeSocket;
	};
	return { room, clock, store, join, alarm: () => alarm };
}

const A = "a".repeat(32);
const B = "b".repeat(32);

describe("PartyRoom", () => {
	it("welcomes, relays and saves the board", async () => {
		const r = makeRoom();
		await Promise.resolve();
		const a = await r.join(A, "Alice");
		const b = await r.join(B, "Bob");
		expect(a.messages()[0]).toMatchObject({ t: "parties", you: A, parties: [] });
		await r.room.webSocketMessage(a, JSON.stringify({ t: "create", type: "ndrag", name: "Run", desc: "", max: 2, roles: {}, autoAccept: false }));
		expect(b.messages().at(-1)).toMatchObject({ t: "upsert", party: { name: "Run" } });
		expect((r.store.get("state") as { parties: unknown[] }).parties).toHaveLength(1);
		expect(r.alarm()).not.toBeNull();
	});

	it("refuses oversized and non-JSON messages and unauthenticated requests", async () => {
		const r = makeRoom();
		const a = await r.join(A, "Alice");
		await r.room.webSocketMessage(a, "x".repeat(3000));
		expect(a.messages().at(-1)).toEqual({ t: "error", code: "too_large" });
		await r.room.webSocketMessage(a, "{nope");
		expect(a.messages().at(-1)).toEqual({ t: "error", code: "bad_request" });
		const bad = await r.room.fetch(new Request("https://party-room/ws", { headers: { Upgrade: "websocket" } }));
		expect(bad.status).toBe(400);
	});

	it("a new connection replaces the old one without marking the leader offline", async () => {
		const r = makeRoom();
		const first = await r.join(A, "Alice");
		await r.room.webSocketMessage(first, JSON.stringify({ t: "create", type: "gdrag", name: "Run", desc: "", max: 3, roles: {}, autoAccept: false }));
		const second = await r.join(A, "Alice");
		expect(first.closed).toBe(4001);
		await r.room.webSocketClose(first);
		const parties = second.messages()[0].parties;
		expect(parties[0].leaderOnline).toBe(true);
		await r.room.webSocketMessage(second, JSON.stringify({ t: "list" }));
		expect(second.messages().at(-1).parties[0].leaderOnline).toBe(true);
	});

	it("closes sockets whose heartbeat stopped and marks their party's leader offline", async () => {
		const r = makeRoom();
		const a = await r.join(A, "Alice");
		const b = await r.join(B, "Bob");
		await r.room.webSocketMessage(a, JSON.stringify({ t: "create", type: "gdrag", name: "Run", desc: "", max: 3, roles: {}, autoAccept: false }));
		r.clock.now += HEARTBEAT_TIMEOUT_MS + 1;
		await r.room.alarm();
		expect(a.closed).toBe(4002);
		expect(b.messages().at(-1)).toMatchObject({ t: "upsert", party: { leaderOnline: false } });
	});
});

describe("party finder route", () => {
	beforeEach(() => resetMemoryForTests());

	function withRooms() {
		const seen: Request[] = [];
		const ns: RoomNamespace = {
			idFromName: (n) => "id:" + n,
			get: (id) => ({
				fetch: async (r: Request) => {
					seen.push(r);
					return new Response("room " + String(id));
				},
			}),
		};
		const d = makeDeps();
		d.deps.env.PARTIES = ns;
		return { ...d, seen };
	}

	const server = "0".repeat(32);

	it("is off without the binding", async () => {
		const { deps } = makeDeps();
		expect((await handle(req("GET", `/v1/pf/ws?server=${server}&name=Zach`), deps)).status).toBe(404);
	});

	it("needs a valid token, an upgrade and valid parameters", async () => {
		const { deps, clock } = withRooms();
		const auth = "Bearer " + (await createToken(SECRET, UUID, clock.now)).token;
		expect((await handle(req("GET", `/v1/pf/ws?server=${server}&name=Zach`, undefined, { Upgrade: "websocket" }), deps)).status).toBe(401);
		expect((await handle(req("GET", `/v1/pf/ws?server=${server}&name=Zach`, undefined, { Authorization: auth }), deps)).status).toBe(426);
		expect((await handle(req("GET", `/v1/pf/ws?server=nope&name=Zach`, undefined, { Authorization: auth, Upgrade: "websocket" }), deps)).status).toBe(400);
		expect((await handle(req("GET", `/v1/pf/ws?server=${server}&name=a%20b`, undefined, { Authorization: auth, Upgrade: "websocket" }), deps)).status).toBe(400);
	});

	it("forwards only the verified UUID to the server's room", async () => {
		const { deps, clock, seen } = withRooms();
		const auth = "Bearer " + (await createToken(SECRET, UUID, clock.now)).token;
		const res = await handle(req("GET", `/v1/pf/ws?server=${server}&name=Zach`, undefined,
			{ Authorization: auth, Upgrade: "websocket", "X-GA-UUID": "f".repeat(32) }), deps);
		expect(await res.text()).toBe("room id:" + server);
		expect(seen[0].headers.get("X-GA-UUID")).toBe(UUID);
		expect(seen[0].headers.get("X-GA-Name")).toBe("Zach");
		expect(seen[0].headers.get("Authorization")).toBeNull();
	});

	it("limits reconnect storms", async () => {
		const { deps, clock } = withRooms();
		const auth = "Bearer " + (await createToken(SECRET, UUID, clock.now)).token;
		let last = 0;
		for (let i = 0; i < 13; i++) {
			last = (await handle(req("GET", `/v1/pf/ws?server=${server}&name=Zach`, undefined, { Authorization: auth, Upgrade: "websocket" }), deps)).status;
		}
		expect(last).toBe(429);
	});
});
