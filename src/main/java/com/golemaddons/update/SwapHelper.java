package com.golemaddons.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Finishes an update where the running jar can't be deleted while the game is open (Windows locks it). Started as
 * a separate process when the game closes, from the new jar's own classpath: waits for the game to exit, deletes
 * the old jar (retrying while antivirus or the launcher still holds it), then copies the new jar into place.
 * JDK classes only, since nothing else is on its classpath. Each step goes to the update log.
 *
 * <p>The new jar is copied rather than moved because this process is running from it. If the old jar can't be
 * removed, nothing is copied (two copies of the mod would stop the game from starting) and the mod tries again the
 * next time the game closes; the {@code .part} left behind is how it knows.
 */
public final class SwapHelper {
	private static final long WAIT_FOR_GAME_MINUTES = 30;
	private static final long DELETE_RETRY_MS = 500;
	private static final int DELETE_ATTEMPTS = 240; // two minutes

	private SwapHelper() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 5) return;
		long pid = Long.parseLong(args[0]);
		Path oldJar = Path.of(args[1]);
		Path part = Path.of(args[2]);
		Path finalJar = Path.of(args[3]);
		Path log = Path.of(args[4]);
		Path folder = oldJar.getParent();
		if (folder == null || !folder.equals(part.getParent()) || !folder.equals(finalJar.getParent()) || !folder.equals(log.getParent())) return;

		log(log, "Helper waiting for the game (pid " + pid + ") to close.");
		var game = ProcessHandle.of(pid);
		if (game.isPresent()) {
			try {
				game.get().onExit().get(WAIT_FOR_GAME_MINUTES, TimeUnit.MINUTES);
			} catch (Exception timeout) {
				log(log, "The game was still running after " + WAIT_FOR_GAME_MINUTES + " minutes; trying again next time it closes.");
				return;
			}
		}
		// Copy the new jar in under a name Fabric ignores first, so the only step between "old jar gone" and "new jar
		// in place" is a rename: the game can never be left without the mod if this process is stopped midway.
		Path ready = folder.resolve(finalJar.getFileName() + ".new");
		try {
			Files.copy(part, ready, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			log(log, "Could not prepare " + ready.getFileName() + " (" + e + "); trying again next time the game closes.");
			return;
		}
		boolean deleted = false;
		Exception last = null;
		for (int attempt = 0; attempt < DELETE_ATTEMPTS && !deleted; attempt++) {
			try {
				Files.deleteIfExists(oldJar);
				deleted = true;
			} catch (Exception locked) {
				last = locked;
				Thread.sleep(DELETE_RETRY_MS);
			}
		}
		if (!deleted) {
			deleteQuietly(ready);
			log(log, "Could not delete " + oldJar.getFileName() + " (" + last + "); trying again next time the game closes.");
			return;
		}
		if (Files.exists(finalJar, LinkOption.NOFOLLOW_LINKS)) {
			deleteQuietly(ready);
			log(log, "Deleted " + oldJar.getFileName() + "; " + finalJar.getFileName() + " was already there.");
			return;
		}
		for (int attempt = 0; attempt < 20; attempt++) {
			try {
				Files.move(ready, finalJar);
				log(log, "Replaced " + oldJar.getFileName() + " with " + finalJar.getFileName() + ".");
				return;
			} catch (IOException e) {
				last = e;
				Thread.sleep(DELETE_RETRY_MS);
			}
		}
		log(log, "Deleted " + oldJar.getFileName() + " but could not rename " + ready.getFileName() + " (" + last
				+ "). Rename it to " + finalJar.getFileName() + " yourself.");
	}

	private static void deleteQuietly(Path p) {
		try {
			Files.deleteIfExists(p);
		} catch (IOException ignored) {
			// left for the mod's cleanup
		}
	}

	private static void log(Path log, String message) {
		try {
			Files.writeString(log, LocalDateTime.now().withNano(0) + "  [helper] " + message + System.lineSeparator(),
					StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
			// nowhere else to report
		}
	}
}
