package com.golemaddons.partyfinder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * One WebSocket to the party finder. Incoming messages are size-capped, parsed off-thread and handed to the client
 * thread through {@code clientThread}; outgoing ones are chained so only one send is ever in flight (the JDK
 * WebSocket requires that). Heartbeats are a plain "ping" text the server answers without waking up.
 */
final class PartySocket implements WebSocket.Listener {
	static final int MAX_MESSAGE_CHARS = 512 * 1024;

	interface Events {
		void message(PartySocket socket, JsonObject message);

		void closed(PartySocket socket, int code, String reason);
	}

	private final Events events;
	private final Executor clientThread;
	private final StringBuilder partial = new StringBuilder();
	private volatile WebSocket ws;
	private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);
	private volatile boolean closed;

	private PartySocket(Events events, Executor clientThread) {
		this.events = events;
		this.clientThread = clientThread;
	}

	static CompletableFuture<PartySocket> connect(HttpClient http, URI uri, String token, String userAgent, Events events,
			Executor clientThread) {
		PartySocket socket = new PartySocket(events, clientThread);
		return http.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(8))
				.header("Authorization", "Bearer " + token)
				.header("User-Agent", userAgent)
				.buildAsync(uri, socket)
				.thenApply(ws -> {
					socket.ws = ws;
					return socket;
				});
	}

	synchronized void send(String text) {
		WebSocket current = ws;
		if (current == null || closed) return;
		sendChain = sendChain.handle((v, e) -> null).thenCompose(v -> current.sendText(text, true))
				.exceptionally(e -> null);
	}

	void send(JsonObject message) {
		send(message.toString());
	}

	void ping() {
		send("ping");
	}

	void close() {
		if (closed) return;
		closed = true;
		WebSocket current = ws;
		if (current != null) {
			current.sendClose(WebSocket.NORMAL_CLOSURE, "bye").orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
					.whenComplete((w, e) -> current.abort());
		}
	}

	boolean isOpen() {
		WebSocket current = ws;
		return !closed && current != null && !current.isInputClosed() && !current.isOutputClosed();
	}

	// ---- listener (JDK HTTP threads) ----

	@Override
	public void onOpen(WebSocket webSocket) {
		webSocket.request(1);
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		if (partial.length() + data.length() > MAX_MESSAGE_CHARS) {
			partial.setLength(0);
			webSocket.abort();
			fireClosed(1009, "too_large");
			return null;
		}
		partial.append(data);
		if (last) {
			String text = partial.toString();
			partial.setLength(0);
			if (!"pong".equals(text)) {
				JsonObject msg = parse(text);
				if (msg != null) clientThread.execute(() -> {
					if (!closed) events.message(this, msg);
				});
			}
		}
		webSocket.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
		webSocket.request(1); // the server never sends binary; ignore it
		return null;
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		fireClosed(statusCode, reason);
		return null;
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		fireClosed(1006, error.getClass().getSimpleName());
	}

	private void fireClosed(int code, String reason) {
		boolean already = closed;
		closed = true;
		if (!already || code != WebSocket.NORMAL_CLOSURE) {
			String r = reason == null ? "" : reason.length() > 64 ? reason.substring(0, 64) : reason;
			clientThread.execute(() -> events.closed(this, code, r));
		}
	}

	static JsonObject parse(String text) {
		try {
			JsonElement el = JsonParser.parseString(text);
			return el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
