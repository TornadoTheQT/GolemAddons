package com.golemaddons.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blocking HTTP calls to the GolemAddons cosmetics Worker. Only ever called from the cosmetics executor, never from
 * a game thread. Every response body is read with a hard size cap and parsed into fixed fields; anything unexpected
 * is an {@link ApiException}.
 */
final class CosmeticsApi {
	static final int MAX_JSON_BYTES = 256 * 1024;

	private record Raw(int status, String etag, long retryAfter, byte[] body) {
	}
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

	/** What the server says a player has. Either field may be null. Not yet validated by the client. */
	record RemoteProfile(String name, String cape) {
	}

	record Challenge(String nonce) {
	}

	record Session(String token, UUID uuid, long expiresAt) {
	}

	/** A failed call: HTTP status (0 for network errors), the server's error code if any, and Retry-After seconds. */
	static final class ApiException extends IOException {
		final int status;
		final String code;
		final long retryAfterSeconds;

		ApiException(int status, String code, long retryAfterSeconds, String message) {
			super(message);
			this.status = status;
			this.code = code;
			this.retryAfterSeconds = retryAfterSeconds;
		}
	}

	private final HttpClient http;
	private final URI base;
	private final String userAgent;

	CosmeticsApi(HttpClient http, URI base, String version) {
		this.http = http;
		String b = base.toString();
		this.base = URI.create(b.endsWith("/") ? b : b + "/");
		this.userAgent = "GolemAddons/" + version;
	}

	URI baseUri() {
		return base;
	}

	String host() {
		return base.getHost();
	}

	Map<UUID, RemoteProfile> batch(List<UUID> uuids) throws IOException {
		JsonArray ids = new JsonArray();
		for (UUID uuid : uuids) ids.add(Hashes.compactUuid(uuid));
		JsonObject body = new JsonObject();
		body.add("uuids", ids);
		JsonObject profiles = object(postJson("v1/profiles/batch", body, null), "profiles");
		Map<UUID, RemoteProfile> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : profiles.entrySet()) {
			UUID uuid = Hashes.parseUuid(e.getKey());
			if (uuid != null && e.getValue().isJsonObject()) {
				out.put(uuid, profile(e.getValue().getAsJsonObject()));
			}
		}
		return out;
	}

	Challenge challenge(UUID uuid) throws IOException {
		JsonObject body = new JsonObject();
		body.addProperty("uuid", Hashes.compactUuid(uuid));
		return new Challenge(string(postJson("v1/auth/challenge", body, null), "nonce"));
	}

	/**
	 * Proves the account: the player's Mojang-certified chat public key (X.509), its expiry and Mojang's
	 * certificate signature, plus the player's own signature over the challenge. All binary values are base64.
	 */
	Session verify(String nonce, String publicKey, long keyExpiresAt, String keySignature, String signature) throws IOException {
		JsonObject body = new JsonObject();
		body.addProperty("nonce", nonce);
		body.addProperty("publicKey", publicKey);
		body.addProperty("keyExpiresAt", keyExpiresAt);
		body.addProperty("keySignature", keySignature);
		body.addProperty("signature", signature);
		JsonObject res = postJson("v1/auth/verify", body, null);
		UUID uuid = Hashes.parseUuid(string(res, "uuid"));
		if (uuid == null || !res.has("expiresAt") || !res.get("expiresAt").isJsonPrimitive()) {
			throw new ApiException(200, "bad_response", 0, "Malformed verify response");
		}
		return new Session(string(res, "token"), uuid, res.get("expiresAt").getAsLong());
	}

	/**
	 * Updates the signed-in player's profile. A null map value clears that field; a missing key leaves it alone.
	 * Keys: "name", "capeUrl".
	 */
	RemoteProfile update(String token, Map<String, String> changes) throws IOException {
		JsonObject body = new JsonObject();
		for (Map.Entry<String, String> e : changes.entrySet()) {
			if (e.getValue() == null) body.add(e.getKey(), JsonNull.INSTANCE);
			else body.addProperty(e.getKey(), e.getValue());
		}
		HttpRequest request = request("v1/profile")
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.build();
		return profile(parse(send(request, MAX_JSON_BYTES)));
	}

	byte[] cape(String sha256) throws IOException {
		HttpRequest request = request("v1/cape/" + sha256 + ".png").GET().build();
		return send(request, CapePng.MAX_BYTES);
	}

	boolean health() throws IOException {
		JsonObject res = parse(send(request("v1/health").GET().build(), MAX_JSON_BYTES));
		return res.has("ok") && res.get("ok").isJsonPrimitive() && res.get("ok").getAsBoolean();
	}

	private JsonObject postJson(String path, JsonObject body, String token) throws IOException {
		HttpRequest.Builder b = request(path)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
		if (token != null) b.header("Authorization", "Bearer " + token);
		return parse(send(b.build(), MAX_JSON_BYTES));
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(base.resolve(path))
				.timeout(REQUEST_TIMEOUT)
				.header("User-Agent", userAgent)
				.header("Accept", "application/json, image/png");
	}

	/** Sends and reads at most {@code maxBytes}; non-2xx responses become ApiException with the server's code. */
	private byte[] send(HttpRequest request, int maxBytes) throws IOException {
		Raw raw = sendRaw(request, maxBytes);
		checkOk(raw);
		return raw.body();
	}

	private static void checkOk(Raw raw) throws ApiException {
		int status = raw.status();
		if (status >= 200 && status < 300) {
			return;
		}
		String code = "http_" + status;
		try {
			JsonObject err = parse(raw.body());
			if (err.has("error") && err.get("error").isJsonPrimitive()) code = err.get("error").getAsString();
		} catch (IOException ignored) {
			// not JSON; keep the status code
		}
		throw new ApiException(status, code, Math.clamp(raw.retryAfter(), 0, 86_400), "HTTP " + status);
	}

	private Raw sendRaw(HttpRequest request, int maxBytes) throws IOException {
		HttpResponse<InputStream> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ApiException(0, "interrupted", 0, "Interrupted");
		} catch (IOException e) {
			throw new ApiException(0, "network", 0, e.getClass().getSimpleName());
		}
		byte[] data;
		try (InputStream in = response.body()) {
			data = in.readNBytes(maxBytes + 1);
		}
		if (data.length > maxBytes) {
			throw new ApiException(response.statusCode(), "too_large", 0, "Response too large");
		}
		return new Raw(response.statusCode(), response.headers().firstValue("ETag").orElse(null),
				response.headers().firstValueAsLong("Retry-After").orElse(0), data);
	}

	private static JsonObject parse(byte[] data) throws IOException {
		try {
			JsonElement el = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
			if (!el.isJsonObject()) throw new ApiException(200, "bad_response", 0, "Expected a JSON object");
			return el.getAsJsonObject();
		} catch (RuntimeException e) {
			throw new ApiException(200, "bad_response", 0, "Malformed JSON");
		}
	}

	private static RemoteProfile profile(JsonObject o) {
		return new RemoteProfile(optionalString(o, "name"), optionalString(o, "cape"));
	}

	private static String optionalString(JsonObject o, String key) {
		JsonElement el = o.get(key);
		return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
	}

	private static String string(JsonObject o, String key) throws ApiException {
		String value = optionalString(o, key);
		if (value == null || value.isEmpty() || value.length() > 4096) {
			throw new ApiException(200, "bad_response", 0, "Missing " + key);
		}
		return value;
	}

	private static JsonObject object(JsonObject o, String key) throws ApiException {
		JsonElement el = o.get(key);
		if (el == null || !el.isJsonObject()) throw new ApiException(200, "bad_response", 0, "Missing " + key);
		return el.getAsJsonObject();
	}
}
