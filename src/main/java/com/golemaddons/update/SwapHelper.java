package com.golemaddons.update;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * Finishes an update where the running jar can't be deleted while the game is open (Windows locks it). Started as
 * a separate process when the game closes, from the new jar's own classpath: waits for the game to exit, deletes
 * the old jar, then copies the new jar into place. JDK classes only, since nothing else is on its classpath.
 *
 * <p>The new jar is copied rather than moved because this process is running from it; the leftover
 * {@code .jar.part} is removed by the mod on its next start.
 */
public final class SwapHelper {
	private SwapHelper() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 4) return;
		long pid = Long.parseLong(args[0]);
		Path oldJar = Path.of(args[1]);
		Path part = Path.of(args[2]);
		Path finalJar = Path.of(args[3]);
		if (!oldJar.getParent().equals(part.getParent()) || !part.getParent().equals(finalJar.getParent())) return;

		var game = ProcessHandle.of(pid);
		if (game.isPresent()) {
			try {
				game.get().onExit().get(10, TimeUnit.MINUTES);
			} catch (Exception timeout) {
				return; // game still running: leave everything as it is
			}
		}
		boolean deleted = false;
		for (int attempt = 0; attempt < 40 && !deleted; attempt++) {
			try {
				if (Files.isRegularFile(oldJar, LinkOption.NOFOLLOW_LINKS)) Files.delete(oldJar);
				deleted = true;
			} catch (Exception locked) {
				Thread.sleep(500);
			}
		}
		// Never put the new jar next to the old one: two copies of the mod would stop the game from starting.
		if (deleted && !Files.exists(finalJar, LinkOption.NOFOLLOW_LINKS)) {
			Files.copy(part, finalJar, StandardCopyOption.COPY_ATTRIBUTES);
		}
	}
}
