package com.golemaddons.update;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks GitHub for a newer GolemAddons release and, when asked (or with auto-update on), downloads, verifies and
 * stages it for the next start. All network work runs on one background thread; results are published for the UI.
 */
public final class UpdateChecker {
	private static final Logger LOGGER = LoggerFactory.getLogger("golemaddons");
	private static final long AUTO_CHECK_INTERVAL_MS = 60 * 60 * 1000;
	private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(8_000L);
	private static final UpdateChecker INSTANCE = new UpdateChecker();

	public enum State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, STAGED, ERROR }

	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "GolemAddons updater");
		t.setDaemon(true);
		return t;
	});
	private final String currentVersion;
	private final String userAgent;
	private ConfigManager config;
	private HttpClient http;
	private UpdateInstaller installer;
	private UpdateInstaller.Unavailable unavailable;

	private volatile State state = State.IDLE;
	private volatile Release latest;
	/** Translation key (under golemaddons.updates.error.) of the last failure, for the UI. */
	private volatile String error;
	private volatile String errorDetail = "";
	private volatile long downloaded;
	private volatile long lastCheckAt;
	private String toastedVersion;

	private UpdateChecker() {
		currentVersion = FabricLoader.getInstance().getModContainer(GolemAddonsClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0.0.0");
		userAgent = "GolemAddons/" + currentVersion;
	}

	public static UpdateChecker instance() {
		return INSTANCE;
	}

	public void register(ConfigManager config) {
		this.config = config;
		ModContainer self = FabricLoader.getInstance().getModContainer(GolemAddonsClient.MOD_ID).orElse(null);
		List<Path> origin = self != null ? self.getOrigin().getKind() == net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH
				? self.getOrigin().getPaths() : List.of() : List.of();
		UpdateInstaller.Located located = UpdateInstaller.locate(origin, FabricLoader.getInstance().getGameDir().resolve("mods"));
		installer = located.installer();
		unavailable = located.unavailable();
		if (installer != null) {
			LOGGER.info("Updates install into {}", installer.folder());
			worker.execute(installer::cleanUpLeftovers);
		} else {
			LOGGER.info("Updates can't be installed from here ({}): loaded from {}, mods folder {}", unavailable, origin,
					FabricLoader.getInstance().getGameDir().resolve("mods"));
		}

		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
			if (config.get().updates.checkOnStartup) check(false);
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> swap());
		// Fallback when the game is closed without the normal shutdown (e.g. the window is killed).
		Runtime.getRuntime().addShutdownHook(new Thread(this::swap, "GolemAddons update swap"));
	}

	private void swap() {
		if (installer != null) installer.swapIfStaged();
	}

	public String currentVersion() {
		return currentVersion;
	}

	public State state() {
		return state;
	}

	/** The newest release found by the last successful check (any version), or null. */
	public Release latest() {
		return latest;
	}

	/** The latest release if it is newer than this one and the player hasn't skipped it, else null. */
	public Release newerRelease() {
		Release r = latest;
		if (r == null || !Versions.isNewer(r.version(), currentVersion)) return null;
		return r.version().equals(config.get().updates.skippedVersion) ? null : r;
	}

	public String error() {
		return error;
	}

	public String errorDetail() {
		return errorDetail;
	}

	public long downloaded() {
		return downloaded;
	}

	/** Null when installing from here works; otherwise why it doesn't. */
	public UpdateInstaller.Unavailable installUnavailable() {
		return unavailable;
	}

	private synchronized HttpClient http() {
		if (http == null) {
			http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
		}
		return http;
	}

	/** Starts a check. Automatic checks run at most once an hour; manual ones whenever nothing else is running. */
	public void check(boolean manual) {
		State s = state;
		if (s == State.CHECKING || s == State.DOWNLOADING || s == State.STAGED) return;
		long now = System.currentTimeMillis();
		if (!manual && now - lastCheckAt < AUTO_CHECK_INTERVAL_MS) return;
		lastCheckAt = now;
		boolean prereleases = config.get().updates.includePrereleases;
		state = State.CHECKING;
		worker.execute(() -> {
			try {
				JsonElement json = fetch(prereleases ? UpdateSource.LIST : UpdateSource.LATEST);
				Release release = prereleases ? ReleaseParser.newest(json, true) : ReleaseParser.parse(json);
				latest = release;
				error = null;
				state = Versions.isNewer(release.version(), currentVersion) ? State.AVAILABLE : State.UP_TO_DATE;
				Minecraft.getInstance().execute(this::afterCheck);
			} catch (ReleaseParser.ParseException e) {
				fail(e.problem() == ReleaseParser.Problem.MALFORMED ? "malformed" : "no_release", "");
			} catch (FetchException e) {
				fail(e.key, e.detail);
			}
		});
	}

	private void afterCheck() {
		Release newer = newerRelease();
		if (newer == null) return;
		GolemConfig.Updates settings = config.get().updates;
		if (settings.autoUpdate && installer != null && newer.sha256() != null) {
			install();
			return;
		}
		if (!newer.version().equals(toastedVersion)) {
			toastedVersion = newer.version();
			SystemToast.addOrUpdate(Minecraft.getInstance().getToastManager(), TOAST, Component.translatable("golemaddons.updates.toast.title"),
					Component.translatable("golemaddons.updates.toast.available", newer.version(), GolemAddonsClient.openMenuKeyName()));
		}
	}

	/** Downloads and stages the latest release. Does nothing unless a newer, checksummed release is known. */
	public void install() {
		Release release = latest;
		if (installer == null || release == null || release.sha256() == null || !Versions.isNewer(release.version(), currentVersion)) return;
		if (state == State.DOWNLOADING || state == State.STAGED) return;
		state = State.DOWNLOADING;
		downloaded = 0;
		worker.execute(() -> {
			try {
				installer.stage(release, new Downloader(http(), UpdateSource::allowedDownload, userAgent), n -> downloaded = n);
				state = State.STAGED;
				error = null;
				Minecraft.getInstance().execute(() -> SystemToast.addOrUpdate(Minecraft.getInstance().getToastManager(), TOAST,
						Component.translatable("golemaddons.updates.toast.title"),
						Component.translatable("golemaddons.updates.toast.staged", release.version())));
			} catch (Downloader.DownloadException e) {
				LOGGER.warn("Update download failed: {}", e.getMessage());
				fail("download." + e.problem().name().toLowerCase(java.util.Locale.ROOT), "");
			} catch (UpdateInstaller.InstallException | RuntimeException e) {
				LOGGER.warn("Update install failed: {}", e.getMessage());
				fail("install", e.getMessage() == null ? "" : e.getMessage());
			}
		});
	}

	private void fail(String key, String detail) {
		error = key;
		errorDetail = detail;
		state = State.ERROR;
	}

	private static final class FetchException extends Exception {
		final String key;
		final String detail;

		FetchException(String key, String detail) {
			super(key, null, false, false);
			this.key = key;
			this.detail = detail;
		}
	}

	private JsonElement fetch(URI uri) throws FetchException {
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
				.header("User-Agent", userAgent)
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.GET().build();
		try {
			HttpResponse<InputStream> response = http().send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = response.body()) {
				int status = response.statusCode();
				if (status == 403 || status == 429) throw new FetchException("rate_limited", "");
				if (status == 404) throw new FetchException("no_release", "");
				if (status != 200) throw new FetchException("http", Integer.toString(status));
				byte[] body = in.readNBytes(MAX_RESPONSE_BYTES + 1);
				if (body.length > MAX_RESPONSE_BYTES) throw new FetchException("malformed", "");
				return JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			}
		} catch (IOException e) {
			throw new FetchException("network", e.getClass().getSimpleName());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new FetchException("network", "interrupted");
		} catch (JsonParseException e) {
			throw new FetchException("malformed", "");
		}
	}
}
