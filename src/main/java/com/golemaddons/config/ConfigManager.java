package com.golemaddons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and saves the config file. A file that can't be read is renamed to {@code .bad-<timestamp>}
 * rather than overwritten, so the user's rules are never silently lost.
 */
public final class ConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("golemaddons");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Path file;
	private final List<Consumer<GolemConfig>> listeners = new ArrayList<>();
	private GolemConfig config = GolemConfig.defaults();

	public ConfigManager(Path file) {
		this.file = file;
	}

	/** The live config. Treat it as read-only; edit a {@link GolemConfig#copy()} and pass it to {@link #save}. */
	public GolemConfig get() {
		return config;
	}

	/** Called after every load and successful save, e.g. so features can recompile their rules. */
	public void addListener(Consumer<GolemConfig> listener) {
		listeners.add(listener);
	}

	public void load() {
		config = readOrDefaults();
		notifyListeners();
	}

	private GolemConfig readOrDefaults() {
		if (!Files.exists(file)) {
			GolemConfig defaults = GolemConfig.defaults();
			write(defaults);
			return defaults;
		}
		try {
			JsonElement root;
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				root = JsonParser.parseReader(reader);
			}
			if (root == null || !root.isJsonObject()) {
				throw new JsonParseException("file is empty or not a JSON object");
			}
			int version = ConfigMigration.migrate(root.getAsJsonObject());
			if (version > GolemConfig.CURRENT_VERSION) {
				throw new JsonParseException("config version " + version + " is newer than this mod supports");
			}
			GolemConfig loaded = GSON.fromJson(root, GolemConfig.class);
			loaded.sanitize();
			loaded.configVersion = GolemConfig.CURRENT_VERSION;
			if (version < GolemConfig.CURRENT_VERSION) {
				Path backup = file.resolveSibling(file.getFileName() + ".v" + version + "-backup");
				Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
				LOGGER.info("Upgraded {} from config version {} to {} (old file kept as {}).",
						file.getFileName(), version, GolemConfig.CURRENT_VERSION, backup.getFileName());
				write(loaded);
			}
			return loaded;
		} catch (IOException | RuntimeException e) {
			Path backup = file.resolveSibling(file.getFileName() + ".bad-" + LocalDateTime.now().format(BACKUP_STAMP));
			LOGGER.warn("Could not read {} ({}). Moving it to {} and using defaults.", file, e.getMessage(), backup.getFileName());
			try {
				Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException moveError) {
				LOGGER.error("Could not back up the unreadable config; leaving it in place and not saving over it.", moveError);
				return GolemConfig.defaults();
			}
			GolemConfig defaults = GolemConfig.defaults();
			write(defaults);
			return defaults;
		}
	}

	/** Replaces the live config and writes it to disk. Returns false (and keeps the new config in memory) if writing failed. */
	public boolean save(GolemConfig newConfig) {
		newConfig.sanitize();
		config = newConfig;
		boolean written = write(newConfig);
		notifyListeners();
		return written;
	}

	private boolean write(GolemConfig value) {
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(value, writer);
			}
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not save {}", file, e);
			return false;
		}
	}

	private void notifyListeners() {
		for (Consumer<GolemConfig> listener : listeners) {
			listener.accept(config);
		}
	}
}
