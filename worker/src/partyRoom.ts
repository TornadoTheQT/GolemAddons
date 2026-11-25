// The Durable Object behind the party finder: one instance per Minecraft server (named by a hash of its address),
// holding that server's parties and the players' WebSockets. Uses the WebSocket Hibernation API, so idle sockets cost
// nothing: heartbeats ("ping" -> "pong") are answered by Cloudflare without waking the object, and an alarm sweeps
// sockets whose heartbeat stopped. Board state is saved to the object's storage after every change, so it survives
// hibernation and restarts.
//
// Only the Worker (api.ts) can reach this object: it verifies the player's session token and passes the proven UUID in
// X-GA-UUID on a request it builds itself, so a client can never choose its own identity.

import { LIMITS, PartyBoard, type Outbox } from "./party";

export const MAX_MESSAGE_BYTES = 2048;
export const MAX_SOCKETS = 400;
/** Clients ping every 20 s; a socket silent for this long is treated as gone. */
export const HEARTBEAT_TIMEOUT_MS = 70_000;
const SWEEP_MS = 30_000;

export interface SocketLike {
	send(message: string): void;
	close(code?: number, reason?: string): void;
	serializeAttachment(value: unknown): void;
	deserializeAttachment(): unknown;
	readyState: number;
}

export interface RoomContext {
	acceptWebSocket(ws: SocketLike, tags?: string[]): void;
	getWebSockets(tag?: string): SocketLike[];
	getWebSocketAutoResponseTimestamp(ws: SocketLike): Date | null;
	setWebSocketAutoResponse(pair: unknown): void;
	blockConcurrencyWhile<T>(fn: () => Promise<T>): Promise<T>;
	storage: {
		get(key: string): Promise<unknown>;
		put(key: string, value: unknown): Promise<void>;
		delete(key: string): Promise<boolean>;
		setAlarm(at: number): Promise<void>;
		getAlarm(): Promise<number | null>;
	};
}

interface Attachment {
	uuid: string;
	name: string;
	connectedAt: number;
}

/** Runtime pieces the tests replace. */
export interface RoomRuntime {
	now: () => number;
	newPair: () => { client: unknown; server: SocketLike };
	autoResponse: () => unknown;
	upgradeResponse: (client: unknown) => Response;
}

const OPEN = 1;

declare const WebSocketPair: { new (): { 0: unknown; 1: SocketLike } };
declare const WebSocketRequestResponsePair: { new (request: string, response: string): unknown };

const workerRuntime: RoomRuntime = {
	now: Date.now,
	newPair: () => {
		const pair = new WebSocketPair();
		return { client: pair[0], server: pair[1] };
	},
	autoResponse: () => new WebSocketRequestResponsePair("ping", "pong"),
	upgradeResponse: (client) => new Response(null, { status: 101, webSocket: client } as ResponseInit),
};

export class PartyRoom {
	private board!: PartyBoard;
	/** Resolves once the board is loaded; the runtime also holds events back until then. */
	private readonly ready: Promise<void>;
	private readonly runtime: RoomRuntime;

	constructor(private readonly ctx: RoomContext, _env: unknown, runtime: RoomRuntime = workerRuntime) {
		this.runtime = runtime;
		ctx.setWebSocketAutoResponse(runtime.autoResponse());
		const outbox: Outbox = {
			send: (uuid, message) => {
				const text = JSON.stringify(message);
				for (const ws of this.open(uuid)) trySend(ws, text);
			},
			broadcast: (message) => {
				const text = JSON.stringify(message);
				for (const ws of this.open()) trySend(ws, text);
			},
			isOnline: (uuid) => this.open(uuid).length > 0,
		};
		this.ready = ctx.blockConcurrencyWhile(async () => {
			this.board = new PartyBoard(runtime.now, outbox, await ctx.storage.get("state"));
		});
	}

	async fetch(request: Request): Promise<Response> {
		await this.ready;
		const uuid = request.headers.get("X-GA-UUID") ?? "";
		const name = request.headers.get("X-GA-Name") ?? "";
		if (!/^[0-9a-f]{32}$/.test(uuid) || !/^[A-Za-z0-9_]{1,16}$/.test(name)) return new Response("bad request", { status: 400 });
		if ((request.headers.get("Upgrade") ?? "").toLowerCase() !== "websocket") return new Response("upgrade required", { status: 426 });

		// One connection per player: a new one (another window, a reconnect) replaces the old.
		const previous = this.open(uuid);
		if (this.open().length - previous.length >= MAX_SOCKETS) return new Response("busy", { status: 503 });
		for (const ws of previous) {
			ws.serializeAttachment({ ...(ws.deserializeAttachment() as Attachment), replaced: true });
			try {
				ws.close(4001, "replaced");
			} catch {
				// already closing
			}
		}

		const { client, server } = this.runtime.newPair();
		this.ctx.acceptWebSocket(server, [uuid]);
		server.serializeAttachment({ uuid, name, connectedAt: this.runtime.now() } satisfies Attachment);
		this.board.connect(uuid, name);
		await this.afterChange();
		return this.runtime.upgradeResponse(client);
	}

	async webSocketMessage(ws: SocketLike, message: string | ArrayBuffer): Promise<void> {
		await this.ready;
		const who = attachment(ws);
		if (!who) return;
		if (typeof message !== "string" || message.length > MAX_MESSAGE_BYTES) {
			trySend(ws, JSON.stringify({ t: "error", code: "too_large" }));
			return;
		}
		if (message === "ping") {
			trySend(ws, "pong"); // the auto-response normally answers this without waking us
			return;
		}
		let value: unknown;
		try {
			value = JSON.parse(message);
		} catch {
			trySend(ws, JSON.stringify({ t: "error", code: "bad_request" }));
			return;
		}
		this.board.handle(who.uuid, value, who.name);
		await this.afterChange();
	}

	async webSocketClose(ws: SocketLike): Promise<void> {
		await this.gone(ws);
	}

	async webSocketError(ws: SocketLike): Promise<void> {
		await this.gone(ws);
	}

	/** Closes sockets whose heartbeat stopped, then expires requests and abandoned parties. */
	async alarm(): Promise<void> {
		await this.ready;
		const now = this.runtime.now();
		for (const ws of this.open()) {
			const who = attachment(ws);
			if (!who) continue;
			const beat = Math.max(who.connectedAt, this.ctx.getWebSocketAutoResponseTimestamp(ws)?.getTime() ?? 0);
			if (now - beat > HEARTBEAT_TIMEOUT_MS) {
				try {
					ws.close(4002, "timeout");
				} catch {
					// already closed
				}
				ws.serializeAttachment({ ...who, replaced: true });
				if (this.open(who.uuid).filter((s) => s !== ws).length === 0) this.board.disconnect(who.uuid);
			}
		}
		await this.afterChange();
	}

	private async gone(ws: SocketLike): Promise<void> {
		await this.ready;
		const who = attachment(ws);
		try {
			ws.close(1000, "bye");
		} catch {
			// already closed
		}
		if (!who || (who as Attachment & { replaced?: boolean }).replaced) return;
		if (this.open(who.uuid).filter((s) => s !== ws).length === 0) this.board.disconnect(who.uuid);
		await this.afterChange();
	}

	/** Saves the board if it changed and keeps the sweep alarm running while anyone is connected. */
	private async afterChange(): Promise<void> {
		const due = this.board.tick();
		if (this.board.takeDirty()) await this.ctx.storage.put("state", this.board.snapshot());
		const now = this.runtime.now();
		let next: number | null = this.open().length > 0 ? now + SWEEP_MS : null;
		if (due !== null) next = next === null ? due : Math.min(next, due);
		if (next !== null) {
			const current = await this.ctx.storage.getAlarm();
			if (current === null || current > next || current < now) await this.ctx.storage.setAlarm(Math.max(next, now + 1_000));
		}
	}

	private open(tag?: string): SocketLike[] {
		return this.ctx.getWebSockets(tag).filter((ws) => ws.readyState === OPEN && !(attachment(ws) as { replaced?: boolean } | null)?.replaced);
	}
}

function attachment(ws: SocketLike): Attachment | null {
	try {
		const a = ws.deserializeAttachment() as Attachment | null;
		return a && typeof a.uuid === "string" ? a : null;
	} catch {
		return null;
	}
}

function trySend(ws: SocketLike, text: string): void {
	try {
		ws.send(text);
	} catch {
		// closing; the close handler cleans up
	}
}

export const ROOM_LIMITS = { ...LIMITS, MAX_SOCKETS, MAX_MESSAGE_BYTES };
