package com.golemaddons.partyfinder;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.cosmetics.CosmeticsFeature;
import com.golemaddons.cosmetics.Hashes;
import com.golemaddons.partyfinder.PartyModels.JoinRequest;
import com.golemaddons.partyfinder.PartyModels.Member;
import com.golemaddons.partyfinder.PartyModels.Party;
import com.golemaddons.partyfinder.PartyModels.Role;
import com.golemaddons.partyfinder.PartyModels.Settings;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Dragon Party Finder: one WebSocket to the GolemAddons server's room for the Minecraft server you're on, the
 * listings it streams, and the actions the screens and {@code /gapf} call.
 *
 * <p>Connected only while it's useful: while a finder screen is open (plus 30 s, so reopening is instant) and while
 * you lead, belong to or are asking to join a party. Everything here runs on the client thread except sign-in and
 * the socket's I/O, whose results come back through {@code Minecraft.execute}.
 *
 * <p>Invites: when the server says a request was accepted, this game (the leader's) looks the player up by UUID in
 * its own tab list and runs the invite command with the name it finds there, never a name from the message. If they
 * aren't on this server, the invite is cancelled and they're told.
 */
public final class PartyFinderFeature {
	private static final Logger LOGGER = LoggerFactory.getLogger("golemaddons");
	private static final long PING_MS = 20_000;
	private static final long LINGER_MS = 30_000;
	private static final long FIRST_BACKOFF_MS = 5_000;
	private static final long MAX_BACKOFF_MS = 120_000;
	/** A listed member missing from the leader's tab list this long is removed from the listing. */
	private static final long ABSENT_KICK_MS = 90_000;
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(6_000L);

	public enum Connection { OFF, CONNECTING, ONLINE, FAILED }

	private static PartyFinderFeature instance;

	private final Map<String, Party> parties = new LinkedHashMap<>();
	private final List<JoinRequest> incoming = new ArrayList<>();
	private final Map<UUID, String> knownNames = new HashMap<>();
	private final Map<UUID, Long> absentSince = new HashMap<>();
	private final InviteThrottle invites = new InviteThrottle(Util::getMillis);
	private final String userAgent;

	private GolemConfig.PartyFinder settings = new GolemConfig.PartyFinder();
	private JoinRequest outgoing;
	private UUID you;
	private Connection connection = Connection.OFF;
	private Component problem;
	private boolean loaded;
	private boolean refreshing;
	private int revision;
	private PartySocket socket;
	private String socketServer;
	private int attempt;
	private long nextAttempt;
	private long backoff = FIRST_BACKOFF_MS;
	private long lastPing;
	private long lingerUntil;
	private boolean screenOpen;
	private boolean openScreenNextTick;
	private int nextRef = 1;
	private int ticks;
	/** Sample data from the in-game smoke test: no connection is made. */
	private boolean sample;

	public PartyFinderFeature() {
		String version = FabricLoader.getInstance().getModContainer("golemaddons")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		this.userAgent = "GolemAddons/" + version;
	}

	public static PartyFinderFeature instance() {
		return instance;
	}

	public void register(ConfigManager config) {
		instance = this;
		config.addListener(c -> {
			boolean wasEnabled = settings.enabled;
			settings = c.partyFinder.copy();
			if (wasEnabled && !settings.enabled) disconnect(true);
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> disconnect(true)));
		PartyFinderCommands.register(this);
	}

	// ---- state for screens (client thread) ----

	public boolean enabled() {
		return settings.enabled;
	}

	public GolemConfig.PartyFinder settings() {
		return settings;
	}

	/** True when on a multiplayer server, the only place parties exist. */
	public boolean onServer() {
		return sample || serverKey(Minecraft.getInstance()) != null;
	}

	/**
	 * Shows these parties and requests as if connected, without connecting. Only the in-game smoke test calls this,
	 * to screenshot a populated list; {@code json} is the same format the server sends for "parties".
	 */
	public void showSampleData(JsonObject json) {
		sample = true;
		disconnect(true);
		handle(Minecraft.getInstance(), json);
		connection = Connection.ONLINE;
		changed();
	}

	public Connection connection() {
		return connection;
	}

	/** Why the last connection or action failed; null if nothing to show. */
	public Component problem() {
		return problem;
	}

	/** True once the first listing has arrived on this connection, and while not refreshing. */
	public boolean loaded() {
		return loaded && !refreshing;
	}

	/** Bumped on every change, so screens know when to redraw lists. */
	public int revision() {
		return revision;
	}

	public Collection<Party> parties() {
		return Collections.unmodifiableCollection(parties.values());
	}

	public Party party(String id) {
		return parties.get(id);
	}

	public List<JoinRequest> incoming() {
		return Collections.unmodifiableList(incoming);
	}

	public JoinRequest outgoing() {
		return outgoing;
	}

	public UUID you() {
		return you != null ? you : Minecraft.getInstance().getUser().getProfileId();
	}

	public Party leading() {
		UUID me = you();
		for (Party p : parties.values()) if (p.leaderId().equals(me)) return p;
		return null;
	}

	public Party memberOf() {
		UUID me = you();
		for (Party p : parties.values()) if (p.hasMember(me)) return p;
		return null;
	}

	public String nameOf(UUID uuid) {
		return knownNames.getOrDefault(uuid, "");
	}

	// ---- screen lifecycle ----

	/** A finder screen opened: connect now, or refresh if already connected. */
	public void screenOpened() {
		screenOpen = true;
		problem = null;
		if (connection == Connection.FAILED) {
			nextAttempt = 0;
			backoff = FIRST_BACKOFF_MS;
		}
		refresh();
	}

	public void screenClosed() {
		screenOpen = false;
		lingerUntil = Util.getMillis() + LINGER_MS;
	}

	void openScreenNextTick() {
		openScreenNextTick = true;
	}

	// ---- actions (client thread); each returns false if it couldn't be sent ----

	public boolean refresh() {
		if (!online()) {
			nextAttempt = Math.min(nextAttempt, Util.getMillis());
			return false;
		}
		refreshing = true;
		changed();
		return send(message("list"));
	}

	public boolean create(Settings s) {
		JsonObject m = PartyJson.settings(s);
		m.addProperty("t", "create");
		return send(m);
	}

	public boolean edit(Settings s) {
		JsonObject m = PartyJson.settings(s);
		m.addProperty("t", "edit");
		return send(m);
	}

	public boolean setInProgress(boolean inProgress) {
		JsonObject m = message("status");
		m.addProperty("inProgress", inProgress);
		return send(m);
	}

	public boolean disband() {
		return send(message("disband"));
	}

	public boolean join(Party party, Role role) {
		JsonObject m = message("join");
		m.addProperty("party", party.id());
		if (role != null) m.addProperty("role", role.id());
		return send(m);
	}

	public boolean cancelRequest() {
		boolean sent = send(message("cancel"));
		if (sent) {
			outgoing = null;
			changed();
		}
		return sent;
	}

	public boolean respond(String requestId, boolean accept) {
		JsonObject m = message("respond");
		m.addProperty("request", requestId);
		m.addProperty("accept", accept);
		boolean sent = send(m);
		if (sent) {
			incoming.removeIf(r -> r.id().equals(requestId));
			changed();
		}
		return sent;
	}

	public boolean kick(UUID member) {
		return kick(member, "kicked");
	}

	private boolean kick(UUID member, String reason) {
		JsonObject m = message("kick");
		m.addProperty("uuid", Hashes.compactUuid(member));
		m.addProperty("reason", reason);
		return send(m);
	}

	public boolean leave() {
		return send(message("leave"));
	}

	private JsonObject message(String type) {
		JsonObject m = new JsonObject();
		m.addProperty("t", type);
		return m;
	}

	private boolean online() {
		if (sample) return false;
		return socket != null && connection == Connection.ONLINE && socket.isOpen();
	}

	private boolean send(JsonObject m) {
		if (!online()) {
			problem = Component.translatable("golemaddons.pf.error.offline");
			changed();
			return false;
		}
		m.addProperty("ref", nextRef++);
		socket.send(m);
		return true;
	}

	// ---- connection management (client thread) ----

	private boolean wanted(Minecraft mc, long now) {
		if (!settings.enabled || serverKey(mc) == null) return false;
		return screenOpen || now < lingerUntil || outgoing != null || leading() != null || memberOf() != null;
	}

	private void onTick(Minecraft mc) {
		if (openScreenNextTick) {
			openScreenNextTick = false;
			mc.setScreen(new com.golemaddons.ui.PartyFinderScreen(null));
		}
		String command = invites.poll();
		if (command != null && mc.player != null) {
			mc.player.connection.sendCommand(command);
		}
		if (++ticks % 20 != 0 || sample) return;

		long now = Util.getMillis();
		String key = serverKey(mc);
		if (socket != null && !java.util.Objects.equals(key, socketServer)) {
			disconnect(true); // switched servers: different room, different parties
		}
		boolean wanted = wanted(mc, now);
		if (wanted && socket == null && connection != Connection.CONNECTING && now >= nextAttempt) {
			connect(mc, key);
		} else if (!wanted && (socket != null || connection == Connection.CONNECTING)) {
			disconnect(false);
		}
		if (online() && now - lastPing >= PING_MS) {
			lastPing = now;
			socket.ping();
		}
		checkMembersPresent(mc, now);
	}

	/** Leader only: members who left the Minecraft server come off the listing, so counts don't go stale. */
	private void checkMembersPresent(Minecraft mc, long now) {
		Party own = leading();
		ClientPacketListener net = mc.getConnection();
		if (own == null || net == null || !online()) {
			absentSince.clear();
			return;
		}
		for (Member m : own.members()) {
			if (net.getPlayerInfo(m.uuid()) != null) {
				absentSince.remove(m.uuid());
			} else {
				long since = absentSince.computeIfAbsent(m.uuid(), u -> now);
				if (now - since > ABSENT_KICK_MS) {
					absentSince.remove(m.uuid());
					kick(m.uuid(), "offline");
				}
			}
		}
		absentSince.keySet().removeIf(u -> !own.hasMember(u));
	}

	private void connect(Minecraft mc, String key) {
		CosmeticsFeature cosmetics = CosmeticsFeature.instance();
		URI base = cosmetics == null ? null : cosmetics.serverUri();
		String name = mc.getUser().getName();
		if (base == null || !PartyText.validPlayerName(name)) {
			fail(Component.translatable(base == null ? "golemaddons.pf.error.no_server" : "golemaddons.pf.error.account"), 60_000);
			return;
		}
		String b = base.toString();
		String wsBase = (b.startsWith("https://") ? "wss://" + b.substring(8) : "ws://" + b.substring(b.indexOf("://") + 3));
		if (!wsBase.endsWith("/")) wsBase += "/";
		URI uri = URI.create(wsBase + "v1/pf/ws?server=" + key + "&name=" + URLEncoder.encode(name, StandardCharsets.UTF_8));
		int id = ++attempt;
		connection = Connection.CONNECTING;
		changed();
		PartySocket.Events events = new PartySocket.Events() {
			@Override
			public void message(PartySocket s, JsonObject message) {
				if (s == socket) handle(mc, message);
			}

			@Override
			public void closed(PartySocket s, int code, String reason) {
				if (s == socket) onClosed(code);
			}
		};
		cosmetics.sessionToken()
				.thenCompose(token -> PartySocket.connect(cosmetics.http(), uri, token, userAgent, events, mc::execute))
				.whenComplete((s, error) -> mc.execute(() -> connected(mc, id, key, s, error, cosmetics)));
	}

	private void connected(Minecraft mc, int id, String key, PartySocket s, Throwable error, CosmeticsFeature cosmetics) {
		if (id != attempt || connection != Connection.CONNECTING || !wanted(mc, Util.getMillis())) {
			if (s != null) s.close();
			return;
		}
		if (error == null) {
			socket = s;
			socketServer = key;
			connection = Connection.ONLINE;
			problem = null;
			backoff = FIRST_BACKOFF_MS;
			lastPing = Util.getMillis();
			refreshing = true;
			changed();
			return;
		}
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		if (cause instanceof CosmeticsFeature.AuthFailure auth) {
			fail(auth.message, Math.max(30_000, auth.retryAfterSeconds * 1000));
		} else if (cause instanceof WebSocketHandshakeException hs) {
			int status = hs.getResponse().statusCode();
			if (status == 401) {
				cosmetics.dropSession(); // token expired or the server's secret changed: sign in again
				fail(Component.translatable("golemaddons.pf.error.network"), FIRST_BACKOFF_MS);
			} else if (status == 404) {
				fail(Component.translatable("golemaddons.pf.error.not_deployed"), 5 * 60_000);
			} else if (status == 429) {
				fail(Component.translatable("golemaddons.pf.error.rate_limited"), 60_000);
			} else {
				failWithBackoff();
			}
		} else {
			LOGGER.debug("GolemAddons: party finder connection failed", cause);
			failWithBackoff();
		}
	}

	private void failWithBackoff() {
		fail(Component.translatable("golemaddons.pf.error.network"), backoff);
		backoff = Math.min(MAX_BACKOFF_MS, backoff * 2);
	}

	private void fail(Component why, long retryInMs) {
		connection = Connection.FAILED;
		problem = why;
		nextAttempt = Util.getMillis() + retryInMs;
		changed();
	}

	private void onClosed(int code) {
		socket = null;
		socketServer = null;
		loaded = false;
		if (code == 4001) {
			fail(Component.translatable("golemaddons.pf.error.replaced"), 60_000);
		} else {
			failWithBackoff();
		}
	}

	/** Closes the socket; {@code reset} also forgets the listings (left or switched servers). */
	private void disconnect(boolean reset) {
		attempt++;
		if (socket != null) socket.close();
		socket = null;
		socketServer = null;
		connection = Connection.OFF;
		loaded = false;
		refreshing = false;
		invites.clear();
		if (reset) {
			parties.clear();
			incoming.clear();
			outgoing = null;
			absentSince.clear();
			problem = null;
			nextAttempt = 0;
			backoff = FIRST_BACKOFF_MS;
		}
		changed();
	}

	/**
	 * The room for the server you're on: a hash of its address (lowercase, default port dropped), so the GolemAddons
	 * server never learns which Minecraft server it is. Null in singleplayer or when not connected.
	 */
	static String serverKey(Minecraft mc) {
		if (mc == null || mc.getConnection() == null || mc.isLocalServer()) return null;
		ServerData data = mc.getCurrentServer();
		if (data == null || data.ip == null || data.ip.isBlank()) return null;
		return roomKey(data.ip);
	}

	static String roomKey(String address) {
		String host = address.strip().toLowerCase(Locale.ROOT);
		if (host.endsWith(":25565")) host = host.substring(0, host.length() - 6);
		if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
		return Hashes.sha256Hex(("golemaddons-pf:" + host).getBytes(StandardCharsets.UTF_8)).substring(0, 32);
	}

	// ---- incoming messages (client thread) ----

	private void handle(Minecraft mc, JsonObject m) {
		String t = PartyJson.string(m, "t", 32);
		if (t == null) return;
		switch (t) {
			case "parties" -> {
				UUID me = Hashes.parseUuid(PartyJson.string(m, "you", 36));
				if (me != null) you = me;
				parties.clear();
				for (Party p : PartyJson.parties(m.get("parties"))) put(p);
				incoming.clear();
				incoming.addAll(PartyJson.requests(m.get("requests")));
				List<JoinRequest> pending = PartyJson.requests(m.get("pending"));
				outgoing = pending.isEmpty() ? null : pending.getFirst();
				loaded = true;
				refreshing = false;
			}
			case "upsert" -> {
				Party p = PartyJson.party(m.get("party"));
				if (p != null) put(p);
			}
			case "remove" -> {
				String id = PartyJson.string(m, "id", 16);
				if (id != null) parties.remove(id);
			}
			case "created" -> {
				Party p = PartyJson.party(m.get("party"));
				if (p != null) {
					put(p);
					notify(mc, Component.translatable("golemaddons.pf.notice.created", p.name()), false);
				}
			}
			case "pending" -> {
				JoinRequest r = PartyJson.request(m.get("request"));
				if (r != null) {
					outgoing = r;
					Party p = parties.get(r.party());
					notify(mc, Component.translatable("golemaddons.pf.notice.requested", p == null ? "?" : p.leaderName()), false);
				}
			}
			case "request" -> onRequest(mc, PartyJson.request(m.get("request")));
			case "request_gone" -> {
				String id = PartyJson.string(m, "id", 16);
				incoming.removeIf(r -> r.id().equals(id));
			}
			case "invite" -> onInvite(mc, PartyJson.request(m.get("request")));
			case "result" -> onResult(mc, m);
			case "left" -> {
				UUID who = Hashes.parseUuid(PartyJson.string(m, "uuid", 36));
				if (who != null) {
					notify(mc, Component.translatable("golemaddons.pf.notice.left", knownNames.getOrDefault(who, "?")), false);
				}
			}
			case "error" -> {
				String code = PartyJson.string(m, "code", 40);
				problem = errorText(code);
				refreshing = false;
				notify(mc, problem, false);
			}
			default -> {
				// "ok" and anything newer: nothing to do
			}
		}
		changed();
	}

	private void put(Party p) {
		parties.put(p.id(), p);
		if (knownNames.size() > 1_000) knownNames.clear();
		knownNames.put(p.leaderId(), p.leaderName());
		for (Member member : p.members()) knownNames.put(member.uuid(), member.name());
	}

	private void onRequest(Minecraft mc, JoinRequest r) {
		Party own = leading();
		if (r == null || own == null || !own.id().equals(r.party())) return;
		incoming.removeIf(x -> x.id().equals(r.id()));
		if (incoming.size() >= 20) return;
		incoming.add(r);
		knownNames.put(r.uuid(), r.name());
		Component role = r.role() == null ? Component.translatable("golemaddons.pf.role.any")
				: Component.translatable(r.role().translationKey()).withColor(r.role().color & 0xFFFFFF);
		Component text = Component.translatable("golemaddons.pf.notice.request", r.name(), role);
		toast(mc, text);
		if (mc.player != null) {
			// Clicking fills in the command; you press Enter. The request id is digits only (checked when parsed).
			MutableComponent line = Component.literal("[Party Finder] ").withStyle(ChatFormatting.DARK_PURPLE).append(text.copy().withStyle(ChatFormatting.WHITE))
					.append(" ").append(chatButton("golemaddons.pf.chat.accept", "/gapf accept " + r.id(), ChatFormatting.GREEN))
					.append(" ").append(chatButton("golemaddons.pf.chat.deny", "/gapf deny " + r.id(), ChatFormatting.RED));
			mc.gui.getChat().addClientSystemMessage(line);
		}
		if (settings.sound) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.6f));
	}

	private static Component chatButton(String key, String command, ChatFormatting color) {
		return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(s -> s.withColor(color)
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	private void onInvite(Minecraft mc, JoinRequest r) {
		Party own = leading();
		if (r == null || own == null || !own.id().equals(r.party())) return;
		incoming.removeIf(x -> x.id().equals(r.id()));
		ClientPacketListener net = mc.getConnection();
		PlayerInfo info = net == null ? null : net.getPlayerInfo(r.uuid());
		String name = info == null ? null : info.getProfile().name();
		String command = name == null ? null : PartyText.inviteCommand(settings.inviteCommand, name);
		if (command == null) {
			kick(r.uuid(), "not_online");
			notify(mc, Component.translatable("golemaddons.pf.notice.not_online", r.name()), false);
			return;
		}
		if (!invites.offer(command)) {
			kick(r.uuid(), "not_online");
			notify(mc, Component.translatable("golemaddons.pf.notice.invite_busy", name), false);
			return;
		}
		notify(mc, Component.translatable("golemaddons.pf.notice.inviting", name), false);
	}

	private void onResult(Minecraft mc, JsonObject m) {
		String kind = PartyJson.string(m, "kind", 24);
		String party = PartyJson.string(m, "party", 16);
		String leader = PartyJson.string(m, "leader", 16);
		if (kind == null || !kind.matches("[a-z_]+")) return;
		if (outgoing != null && outgoing.party().equals(party)) outgoing = null;
		String who = PartyText.validPlayerName(leader) ? leader : "?";
		Component text = Component.translatableWithFallback("golemaddons.pf.result." + kind, kind, who);
		notify(mc, text, "accepted".equals(kind));
		if ("accepted".equals(kind) && settings.sound) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f));
		}
	}

	private static Component errorText(String code) {
		String c = code != null && code.matches("[a-z_]{1,40}") ? code : "unknown";
		return Component.translatableWithFallback("golemaddons.pf.error." + c, "Party finder: " + c);
	}

	/** A compact toast (replacing the previous one, so they never stack up) and, if enabled, one chat line. */
	private void notify(Minecraft mc, Component text, boolean important) {
		toast(mc, text);
		if ((settings.chatMessages || important) && mc.player != null) {
			mc.gui.getChat().addClientSystemMessage(Component.literal("[Party Finder] ").withStyle(ChatFormatting.DARK_PURPLE)
					.append(text.copy().withStyle(ChatFormatting.WHITE)));
		}
	}

	private void toast(Minecraft mc, Component text) {
		if (settings.toasts) {
			SystemToast.addOrUpdate(mc.getToastManager(), TOAST, Component.translatable("golemaddons.pf.title"), text);
		}
	}

	private void changed() {
		revision++;
	}

	/** For /gapf accept|deny: whether that request is waiting for you. */
	boolean hasIncoming(String id) {
		for (JoinRequest r : incoming) if (r.id().equals(id)) return true;
		return false;
	}
}
