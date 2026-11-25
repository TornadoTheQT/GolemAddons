package com.golemaddons.cosmetics;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synced custom names and capes. Owns the cosmetics executor, the per-UUID cache, cape textures and the sign-in
 * flow; the mixins call {@link #lookup} and {@link #skinWithCape} from the render thread.
 *
 * <p>Threading: the client thread discovers players and starts batches; all HTTP, sign-in, hashing, disk and PNG
 * decoding run on a two-thread executor; results go into concurrent structures or back to the client thread with
 * {@code Minecraft.execute}. Nothing here blocks a game thread.
 */
public final class CosmeticsFeature {
	private static final Logger LOGGER = LoggerFactory.getLogger("golemaddons");
	/** Max UUIDs per batch request (the Worker enforces the same limit). */
	static final int BATCH_SIZE = 100;
	private static final long FLUSH_INTERVAL_MS = 1_000;
	private static final int DISCOVER_EVERY_TICKS = 20;
	private static final int TEXTURE_TICK_EVERY = 100;
	private static final int BREAKER_FAILURES = 5;
	private static final long BREAKER_PAUSE_MS = 5 * 60_000L;
	private static final long AUTH_RETRY_MS = 30_000L;
	/** Re-sign-in this long before the token expires. */
	private static final long TOKEN_MARGIN_MS = 60_000L;
	/** What the challenge signature covers; the Worker checks the same string (worker/src/auth.ts). */
	private static final String CHALLENGE_PREFIX = "golemaddons-cosmetics:";

	private static CosmeticsFeature instance;

	/** One player's cosmetics, validated and ready to draw. Either field may be null, not both. */
	public record Cosmetic(String rawName, Component name, String capeHash) {
	}

	private record SkinMemo(PlayerSkin base, ClientAsset.ResourceTexture cape, PlayerSkin patched) {
	}

	private record Settings(boolean enabled, boolean showOthers, boolean showOwn) {
	}

	private final String builtInUrl;
	private final String version;
	private final ThreadPoolExecutor executor;
	private final CapeTextures textures;
	private final Map<UUID, SkinMemo> skinMemo = new ConcurrentHashMap<>();
	/** Swaps usernames (and a rank right before them) for custom names in chat, sidebar, tab list and nametags. */
	private final NameRewriter names = new NameRewriter();
	private final AtomicBoolean batchInFlight = new AtomicBoolean();
	private final AtomicBoolean updating = new AtomicBoolean();
	private final AtomicInteger consecutiveFailures = new AtomicInteger();

	private volatile Settings settings = new Settings(false, true, true);
	private volatile CosmeticsApi api;
	private volatile ProfileCache<Cosmetic> cache = new ProfileCache<>(System::currentTimeMillis);
	private volatile CosmeticsApi.Session session;
	private volatile long pausedUntil;
	private volatile long authBlockedUntil;
	private volatile String lastError = "";
	private volatile Component lastUpdateMessage;
	private HttpClient http;
	/** The cosmetics server's address even while cosmetics are off: the party finder signs in through it. */
	private volatile URI authUri;
	private UUID selfId;
	private long lastFlush;
	private int ticks;

	public CosmeticsFeature() {
		this.builtInUrl = FabricLoader.getInstance().getModContainer("golemaddons")
				.flatMap(c -> java.util.Optional.ofNullable(c.getMetadata().getCustomValue("golemaddons:cosmetics_api")))
				.map(v -> v.getAsString().strip()).orElse("");
		this.version = FabricLoader.getInstance().getModContainer("golemaddons")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		AtomicInteger threadNumber = new AtomicInteger();
		this.executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(256), r -> {
			Thread t = new Thread(r, "GolemAddons-Cosmetics-" + threadNumber.incrementAndGet());
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY + 1);
			return t;
		}, new ThreadPoolExecutor.AbortPolicy());
		this.executor.allowCoreThreadTimeOut(true);
		Path cacheDir = FabricLoader.getInstance().getConfigDir().resolve("golemaddons").resolve("cape-cache");
		this.textures = new CapeTextures(executor, () -> api, cacheDir, System::currentTimeMillis);
	}

	/** The running feature, for mixins; null before client init. */
	public static CosmeticsFeature instance() {
		return instance;
	}

	public void register(ConfigManager config) {
		instance = this;
		config.addListener(this::apply);
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			cache.clearQueue();
			skinMemo.clear();
			names.clear();
		});
		CosmeticsCommands.register(this);
	}

	// ---- configuration ----

	private void apply(GolemConfig config) {
		GolemConfig.Cosmetics c = config.cosmetics;
		settings = new Settings(c.enabled, c.showOthers, c.showOwn);
		names.clear(); // rebuilt from the tab list within a second, honoring the new toggles
		URI configured = ApiUrl.parse(c.apiBaseUrl.isEmpty() ? builtInUrl : c.apiBaseUrl);
		if (configured == null ? authUri != null : !configured.equals(authUri)) {
			session = null;
		}
		authUri = configured;
		URI uri = c.enabled ? configured : null;
		CosmeticsApi current = api;
		URI currentUri = current == null ? null : current.baseUri();
		if (uri == null ? currentUri == null : uri.equals(currentUri)) {
			return;
		}
		api = uri == null ? null : new CosmeticsApi(http(), uri, version);
		// A different server is a different data set: start clean.
		cache = new ProfileCache<>(System::currentTimeMillis);
		session = null;
		consecutiveFailures.set(0);
		pausedUntil = 0;
		skinMemo.clear();
		Minecraft mc = Minecraft.getInstance();
		if (mc != null) {
			mc.execute(textures::clear);
		}
	}

	/** One shared HTTP client (HTTP/2, no redirects) for cosmetics and the party finder. */
	public synchronized HttpClient http() {
		if (http == null) {
			http = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(5))
					.followRedirects(HttpClient.Redirect.NEVER)
					.version(HttpClient.Version.HTTP_2)
					.build();
		}
		return http;
	}

	/** The GolemAddons server (cosmetics and party finder), whether or not cosmetics are on; null if none. */
	public URI serverUri() {
		return authUri;
	}

	/** Why the party finder couldn't sign in, ready to show. */
	public static final class AuthFailure extends Exception {
		public final Component message;
		public final long retryAfterSeconds;

		AuthFailure(Component message, long retryAfterSeconds) {
			super(message.getString(), null, false, false);
			this.message = message;
			this.retryAfterSeconds = retryAfterSeconds;
		}
	}

	/**
	 * A session token for the signed-in account, reusing the cosmetics sign-in (chat key proof). Signs in on the
	 * cosmetics executor if needed; completes off the game thread, exceptionally with {@link AuthFailure}.
	 */
	public java.util.concurrent.CompletableFuture<String> sessionToken() {
		java.util.concurrent.CompletableFuture<String> out = new java.util.concurrent.CompletableFuture<>();
		URI uri = authUri;
		Minecraft mc = Minecraft.getInstance();
		if (uri == null) {
			out.completeExceptionally(new AuthFailure(Component.translatable("golemaddons.cosmetics.error.no_server"), 0));
			return out;
		}
		CosmeticsApi current = api != null && api.baseUri().equals(uri) ? api : new CosmeticsApi(http(), uri, version);
		User user = mc.getUser();
		ProfileKeyPairManager keys = mc.getProfileKeyPairManager();
		try {
			executor.execute(() -> {
				try {
					out.complete(signIn(current, user, keys).token());
				} catch (SignInException e) {
					out.completeExceptionally(new AuthFailure(e.message, AUTH_RETRY_MS / 1000));
				} catch (CosmeticsApi.ApiException e) {
					out.completeExceptionally(new AuthFailure(e.status == 429
							? Component.translatable("golemaddons.cosmetics.error.rate_limited", Math.max(1, e.retryAfterSeconds))
							: Component.translatable("golemaddons.cosmetics.error.network"), Math.max(e.retryAfterSeconds, 0)));
				} catch (Throwable t) {
					out.completeExceptionally(new AuthFailure(Component.translatable("golemaddons.cosmetics.error.network"), 0));
				}
			});
		} catch (RuntimeException e) {
			out.completeExceptionally(new AuthFailure(Component.translatable("golemaddons.cosmetics.error.network"), 0));
		}
		return out;
	}

	/** Forget the session token (the server refused it); the next {@link #sessionToken()} signs in again. */
	public void dropSession() {
		session = null;
	}

	/** True when a server is configured and the feature is on. */
	public boolean active() {
		return api != null;
	}

	/** The server in use, for the settings screen; empty when none. */
	public String serverDescription() {
		CosmeticsApi current = api;
		return current == null ? "" : current.baseUri().toString();
	}

	public boolean hasBuiltInServer() {
		return ApiUrl.parse(builtInUrl) != null;
	}

	public String lastError() {
		return lastError;
	}

	public boolean paused() {
		return System.currentTimeMillis() < pausedUntil;
	}

	// ---- render-thread hooks (allocation-free on the common path) ----

	/** Cosmetics to draw for this player, honoring the show-own / show-others toggles. Null when none. */
	public Cosmetic lookup(UUID id) {
		if (api == null || id == null) {
			return null;
		}
		Settings s = settings;
		if (id.equals(self()) ? !s.showOwn() : !s.showOthers()) {
			return null;
		}
		return cache.value(id);
	}

	/**
	 * {@code text} with every online player's username (and a [RANK] directly before it) replaced by their custom
	 * name; {@code text} itself when nothing matches. Render and chat threads; memoized.
	 */
	public Component rewriteNames(Component text) {
		return names.rewrite(text);
	}

	/** {@code base} with the custom cape (and matching elytra) once its texture is ready; otherwise {@code base}. */
	public PlayerSkin skinWithCape(UUID id, PlayerSkin base, String capeHash) {
		ClientAsset.ResourceTexture cape = textures.get(capeHash);
		if (cape == null) {
			return base; // not loaded yet or failed: vanilla cape
		}
		SkinMemo memo = skinMemo.get(id);
		if (memo != null && memo.base() == base && memo.cape() == cape) {
			return memo.patched();
		}
		PlayerSkin patched = new PlayerSkin(base.body(), cape, cape, base.model(), base.secure());
		if (skinMemo.size() > 512) skinMemo.clear();
		skinMemo.put(id, new SkinMemo(base, cape, patched));
		return patched;
	}

	private UUID self() {
		if (selfId == null) {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null) selfId = mc.getUser().getProfileId();
		}
		return selfId;
	}

	// ---- discovery and batching (client thread) ----

	private void onTick(Minecraft mc) {
		CosmeticsApi current = api;
		if (current == null) {
			return;
		}
		ticks++;
		ProfileCache<Cosmetic> c = cache;
		if (ticks % DISCOVER_EVERY_TICKS == 0) {
			discover(mc, c);
		}
		if (ticks % TEXTURE_TICK_EVERY == 0) {
			textures.tick();
		}
		long now = System.currentTimeMillis();
		int queued = c.queued();
		if (queued == 0 || now < pausedUntil || batchInFlight.get()) {
			return;
		}
		if (queued < BATCH_SIZE && now - lastFlush < FLUSH_INTERVAL_MS) {
			return;
		}
		lastFlush = now;
		List<UUID> batch = c.drain(BATCH_SIZE);
		batchInFlight.set(true);
		try {
			executor.execute(() -> runBatch(current, c, batch));
		} catch (RuntimeException e) {
			batchInFlight.set(false);
			c.failed(batch);
		}
	}

	/**
	 * Queues everyone in the tab list. Only version-4 (Mojang account) UUIDs are looked up: servers give NPCs and
	 * offline-mode players other versions, and those can never have synced cosmetics.
	 */
	private void discover(Minecraft mc, ProfileCache<Cosmetic> c) {
		UUID me = self();
		if (me != null && me.version() == 4) {
			c.request(me);
		}
		ClientPacketListener connection = mc.getConnection();
		if (connection == null) {
			return;
		}
		Map<String, Component> custom = new java.util.HashMap<>();
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			UUID id = info.getProfile().id();
			if (id != null && id.version() == 4) {
				c.request(id);
				Cosmetic cosmetic = lookup(id);
				if (cosmetic != null && cosmetic.name() != null) {
					custom.put(info.getProfile().name(), cosmetic.name());
				}
			}
		}
		if (names.update(custom) && mc.level != null) {
			// Text displays lay out their text once; make them do it again with the new names.
			for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof net.minecraft.world.entity.Display.TextDisplay display) {
					((com.golemaddons.mixin.TextDisplayAccessor) display).golemaddons$setClientDisplayCache(null);
				}
			}
		}
	}

	private void runBatch(CosmeticsApi current, ProfileCache<Cosmetic> c, List<UUID> batch) {
		try {
			Map<UUID, CosmeticsApi.RemoteProfile> found = current.batch(batch);
			for (UUID id : batch) {
				c.resolved(id, toCosmetic(found.get(id)));
			}
			consecutiveFailures.set(0);
		} catch (Throwable t) {
			c.failed(batch);
			noteFailure(t);
		} finally {
			batchInFlight.set(false);
		}
	}

	private void noteFailure(Throwable t) {
		lastError = t instanceof CosmeticsApi.ApiException e ? e.code : t.getClass().getSimpleName();
		if (consecutiveFailures.incrementAndGet() >= BREAKER_FAILURES) {
			consecutiveFailures.set(0);
			pausedUntil = System.currentTimeMillis() + BREAKER_PAUSE_MS;
			LOGGER.info("GolemAddons: cosmetics server unreachable ({}); pausing for 5 minutes", lastError);
		}
	}

	/** Validates what the server sent. Anything off is dropped, so a bad server can't inject names or hashes. */
	static Cosmetic toCosmetic(CosmeticsApi.RemoteProfile remote) {
		if (remote == null) {
			return null;
		}
		CosmeticName.Result name = remote.name() == null ? null : CosmeticName.parse(remote.name());
		boolean nameOk = name != null && name.valid();
		boolean capeOk = Hashes.isSha256Hex(remote.cape());
		if (!nameOk && !capeOk) {
			return null;
		}
		return new Cosmetic(nameOk ? name.raw() : null, nameOk ? CosmeticText.build(name.runs()) : null,
				capeOk ? remote.cape() : null);
	}

	// ---- updates (commands) ----

	/** Your own current cosmetics as last seen, or null. */
	public Cosmetic own() {
		UUID me = self();
		return me == null ? null : cache.value(me);
	}

	/**
	 * Sends a profile change. {@code changes} keys are "name" / "capeUrl"; a null value clears it. Returns false
	 * (and says why) if it couldn't start; the outcome is reported to chat later.
	 */
	public boolean update(Map<String, String> changes) {
		Minecraft mc = Minecraft.getInstance();
		CosmeticsApi current = api;
		if (current == null) {
			report(mc, Component.translatable(settings.enabled() ? "golemaddons.cosmetics.error.no_server"
					: "golemaddons.cosmetics.error.disabled").withStyle(ChatFormatting.RED));
			return false;
		}
		if (!updating.compareAndSet(false, true)) {
			report(mc, Component.translatable("golemaddons.cosmetics.busy").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		User user = mc.getUser();
		ProfileKeyPairManager keys = mc.getProfileKeyPairManager();
		ProfileCache<Cosmetic> c = cache;
		try {
			executor.execute(() -> runUpdate(mc, current, c, user, keys, changes));
		} catch (RuntimeException e) {
			updating.set(false);
			chat(mc, Component.translatable("golemaddons.cosmetics.error.network").withStyle(ChatFormatting.RED));
			return false;
		}
		return true;
	}

	private void runUpdate(Minecraft mc, CosmeticsApi current, ProfileCache<Cosmetic> c, User user,
			ProfileKeyPairManager keys, Map<String, String> changes) {
		try {
			CosmeticsApi.RemoteProfile result;
			try {
				result = current.update(signIn(current, user, keys).token(), changes);
			} catch (CosmeticsApi.ApiException e) {
				if (e.status != 401) throw e;
				session = null; // token rejected (expired or server secret changed): sign in once more
				result = current.update(signIn(current, user, keys).token(), changes);
			}
			c.resolved(user.getProfileId(), toCosmetic(result));
			skinMemo.remove(user.getProfileId());
			report(mc, Component.translatable("golemaddons.cosmetics.updated").withStyle(ChatFormatting.GREEN));
		} catch (SignInException e) {
			report(mc, e.message.copy().withStyle(ChatFormatting.RED));
		} catch (CosmeticsApi.ApiException e) {
			lastError = e.code;
			Component msg = e.status == 429
					? Component.translatable("golemaddons.cosmetics.error.rate_limited", Math.max(1, e.retryAfterSeconds))
					: e.status == 0
							? Component.translatable("golemaddons.cosmetics.error.network")
							: Component.translatableWithFallback("golemaddons.cosmetics.error." + safeCode(e.code),
									"The cosmetics server refused the change (" + safeCode(e.code) + ").");
			report(mc, msg.copy().withStyle(ChatFormatting.RED));
		} catch (Throwable t) {
			LOGGER.warn("GolemAddons: cosmetics update failed", t);
			report(mc, Component.translatable("golemaddons.cosmetics.error.network").withStyle(ChatFormatting.RED));
		} finally {
			updating.set(false);
		}
	}

	/** The outcome of the last update, for chat and for the Cosmetics screen (which hides chat). */
	private void report(Minecraft mc, Component message) {
		lastUpdateMessage = message;
		mc.execute(() -> chat(mc, message));
	}

	/** True while an update is being sent. */
	public boolean updating() {
		return updating.get();
	}

	/** The result of the last name/cape change ("updated" or why not), or null if none yet. */
	public Component lastUpdateMessage() {
		return lastUpdateMessage;
	}

	/** Server error codes are shown in chat and used in a translation key, so keep them tame. */
	private static String safeCode(String code) {
		return code != null && code.matches("[a-z0-9_]{1,40}") ? code : "unknown";
	}

	private static final class SignInException extends Exception {
		final Component message;

		SignInException(Component message) {
			super(message.getString(), null, false, false);
			this.message = message;
		}
	}

	/**
	 * Proves account ownership without the Worker contacting Mojang (Mojang refuses requests from Cloudflare). The
	 * game already holds a chat key pair that Mojang issued to this account, with a Mojang signature binding the
	 * public key to the UUID. The Worker hands out a signed nonce; we sign it with the private key and send the public
	 * key, its expiry and Mojang's signature. The Worker checks both signatures. The private key and the access token
	 * never leave the game. Executor thread only.
	 */
	private CosmeticsApi.Session signIn(CosmeticsApi current, User user, ProfileKeyPairManager keys)
			throws IOException, SignInException {
		CosmeticsApi.Session s = session;
		long now = System.currentTimeMillis();
		if (s != null && s.uuid().equals(user.getProfileId()) && s.expiresAt() - TOKEN_MARGIN_MS > now) {
			return s;
		}
		if (now < authBlockedUntil) {
			throw new SignInException(Component.translatable("golemaddons.cosmetics.error.auth_wait",
					(authBlockedUntil - now + 999) / 1000));
		}
		try {
			ProfileKeyPair pair = chatKeyPair(keys);
			ProfilePublicKey.Data certified = pair.publicKey().data();
			CosmeticsApi.Challenge challenge = current.challenge(user.getProfileId());
			String signature;
			try {
				Signature signer = Signature.getInstance("SHA256withRSA");
				signer.initSign(pair.privateKey());
				signer.update((CHALLENGE_PREFIX + challenge.nonce()).getBytes(StandardCharsets.UTF_8));
				signature = Base64.getEncoder().encodeToString(signer.sign());
			} catch (GeneralSecurityException e) {
				LOGGER.info("GolemAddons: cosmetics sign-in: couldn't sign the challenge ({})", e.toString());
				throw new SignInException(Component.translatable("golemaddons.cosmetics.error.no_chat_key"));
			}
			CosmeticsApi.Session fresh;
			try {
				fresh = current.verify(challenge.nonce(),
						Base64.getEncoder().encodeToString(certified.key().getEncoded()),
						certified.expiresAt().toEpochMilli(),
						Base64.getEncoder().encodeToString(certified.keySignature()),
						signature);
			} catch (CosmeticsApi.ApiException e) {
				LOGGER.info("GolemAddons: cosmetics sign-in: server refused verification (HTTP {}, {})", e.status, e.code);
				if (e.status == 401 || e.status == 400) {
					throw new SignInException(Component.translatable("key_expired".equals(e.code)
							? "golemaddons.cosmetics.error.no_chat_key" : "golemaddons.cosmetics.error.account"));
				}
				throw e;
			}
			if (!fresh.uuid().equals(user.getProfileId())) {
				LOGGER.info("GolemAddons: cosmetics sign-in: server confirmed a different account");
				throw new SignInException(Component.translatable("golemaddons.cosmetics.error.account"));
			}
			session = fresh;
			return fresh;
		} catch (SignInException | IOException e) {
			authBlockedUntil = System.currentTimeMillis() + AUTH_RETRY_MS;
			throw e;
		}
	}

	/** The game's Mojang-issued chat key pair, fetched or refreshed if needed. Blocks; executor thread only. */
	private static ProfileKeyPair chatKeyPair(ProfileKeyPairManager keys) throws SignInException {
		try {
			ProfileKeyPair pair = keys.prepareKeyPair().get(15, TimeUnit.SECONDS).orElse(null);
			if (pair != null && !pair.publicKey().data().hasExpired()) {
				return pair;
			}
			LOGGER.info("GolemAddons: cosmetics sign-in: no usable chat key pair (offline account or key service unavailable)");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			LOGGER.info("GolemAddons: cosmetics sign-in: couldn't get the chat key pair ({})", e.toString());
		}
		throw new SignInException(Component.translatable("golemaddons.cosmetics.error.no_chat_key"));
	}

	/** Asks the server whether it is up, then reports. Used by /ga custom status. */
	public void checkHealth(java.util.function.Consumer<Boolean> done) {
		CosmeticsApi current = api;
		Minecraft mc = Minecraft.getInstance();
		if (current == null) {
			done.accept(false);
			return;
		}
		try {
			executor.execute(() -> {
				boolean ok;
				try {
					ok = current.health();
				} catch (Throwable t) {
					ok = false;
				}
				boolean result = ok;
				mc.execute(() -> done.accept(result));
			});
		} catch (RuntimeException e) {
			done.accept(false);
		}
	}

	static void chat(Minecraft mc, Component message) {
		if (mc.player != null) {
			mc.gui.getChat().addClientSystemMessage(Component.literal("[GolemAddons] ").withStyle(ChatFormatting.GRAY).append(message));
		}
	}

	/** The API host, so cape URLs pointing back at it can be refused. */
	String apiHost() {
		CosmeticsApi current = api;
		return current == null ? null : current.host();
	}
}
