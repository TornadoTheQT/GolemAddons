package com.golemaddons.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replaces the jar the running mod was actually loaded from with a verified release, so the new version is used from
 * the next start.
 *
 * <p>The folder is wherever that jar really is (any launcher: a plain {@code mods} folder, Lunar's
 * {@code mods/fabric-<version>/}, a Prism or Modrinth instance), found from the loaded file itself rather than
 * guessed from the game folder. The new jar is downloaded beside it as {@code golemaddons-<version>.jar.part}
 * (ignored by Fabric) and only becomes a {@code .jar} after the game stops, at the moment the old jar is removed, so
 * there are never two loadable copies. The only jars ever deleted are files named {@code golemaddons-*.jar} in that
 * folder that really are GolemAddons.
 *
 * <p>Windows keeps a running jar locked, so there a small helper process finishes the swap once the game has exited.
 * If anything stops the swap from finishing (the helper is killed, a file is still locked), the downloaded jar stays
 * as {@code .part} and the swap is tried again the next time the game closes. Every step is written to
 * {@code golemaddons-update.log} in that folder.
 */
public final class UpdateInstaller {
	private static final Logger LOGGER = LoggerFactory.getLogger("golemaddons");
	static final Pattern PART = Pattern.compile("^golemaddons-([0-9A-Za-z.+-]{1,48})\\.jar\\.part$");
	private static final Pattern NEW = Pattern.compile("^golemaddons-[0-9A-Za-z.+-]{1,48}\\.jar\\.new$");
	private static final Pattern JAR = Pattern.compile("^golemaddons-[0-9A-Za-z.+-]{1,48}\\.jar$");
	static final String LOG_NAME = "golemaddons-update.log";
	private static final long MAX_LOG_BYTES = 256 * 1024;

	/** Why installing isn't possible from this copy of the game. */
	public enum Unavailable {
		/** Running from a development build or a folder, not a jar file. */
		NOT_A_JAR,
		/** The folder the jar is in can't be written to. */
		NOT_WRITABLE
	}

	private final Path folder;
	private final Path currentJar;
	private final String runningVersion;
	private Path stagedPart;
	private Path stagedFinal;
	private boolean swapped;

	private UpdateInstaller(Path folder, Path currentJar, String runningVersion) {
		this.folder = folder;
		this.currentJar = currentJar;
		this.runningVersion = runningVersion;
	}

	/** An installer for the running jar, or a reason why there can't be one. */
	public record Located(UpdateInstaller installer, Unavailable unavailable) {
	}

	/**
	 * Finds the jar the running mod was loaded from among {@code candidates} (Fabric's origin paths, then the class's
	 * code source): the first that is a jar file declaring the GolemAddons mod id. Symlinks are followed to the real
	 * file, which is the one replaced.
	 */
	public static Located locate(List<Path> candidates, String runningVersion) {
		for (Path candidate : candidates) {
			if (candidate == null) continue;
			try {
				if (!Files.isRegularFile(candidate) || !candidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
				Path real = candidate.toRealPath();
				JarCheck.ModInfo info = JarCheck.read(real);
				if (info == null || !"golemaddons".equals(info.id())) continue;
				Path folder = real.getParent();
				if (folder == null || !Files.isWritable(folder)) return new Located(null, Unavailable.NOT_WRITABLE);
				return new Located(new UpdateInstaller(folder, real, runningVersion), null);
			} catch (IOException | RuntimeException e) {
				// try the next candidate
			}
		}
		return new Located(null, Unavailable.NOT_A_JAR);
	}

	/** The folder updates are installed into: where the running jar is. */
	public Path folder() {
		return folder;
	}

	public Path currentJar() {
		return currentJar;
	}

	/**
	 * Called once at startup. A {@code .part} of a newer version means an earlier update never finished: it is staged
	 * again so the swap is retried when the game closes. Any other {@code golemaddons-*.jar.part} is a leftover and is
	 * deleted. Returns the version picked up again, or null.
	 */
	public synchronized String resumeOrCleanUp() {
		List<Path> parts = new ArrayList<>();
		try (DirectoryStream<Path> dir = Files.newDirectoryStream(folder)) {
			for (Path p : dir) {
				String name = p.getFileName().toString();
				if (PART.matcher(name).matches() && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) parts.add(p);
				// A half-finished helper copy; the .part it came from is still here to retry with.
				if (NEW.matcher(name).matches() && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) Downloader.deleteQuietly(p);
			}
		} catch (IOException e) {
			return null;
		}
		Path best = null;
		String bestVersion = null;
		for (Path p : parts) {
			Matcher m = PART.matcher(p.getFileName().toString());
			m.matches();
			String version = m.group(1);
			JarCheck.ModInfo info = JarCheck.read(p);
			boolean resumable = info != null && "golemaddons".equals(info.id()) && version.equals(info.version())
					&& Versions.isNewer(version, runningVersion)
					&& !Files.exists(folder.resolve("golemaddons-" + version + ".jar"), LinkOption.NOFOLLOW_LINKS);
			if (resumable && (bestVersion == null || Versions.isNewer(version, bestVersion))) {
				best = p;
				bestVersion = version;
			}
		}
		for (Path p : parts) {
			if (!p.equals(best)) Downloader.deleteQuietly(p);
		}
		if (best == null) return null;
		stagedPart = best;
		stagedFinal = inside(folder, "golemaddons-" + bestVersion + ".jar");
		log("Found an unfinished update to " + bestVersion + "; it will be finished when the game closes.");
		return bestVersion;
	}

	/** Leftover cleanup only (no resume), for a folder on its own. */
	static void cleanUpLeftovers(Path folder) {
		try (DirectoryStream<Path> dir = Files.newDirectoryStream(folder)) {
			for (Path p : dir) {
				if (PART.matcher(p.getFileName().toString()).matches() && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
					Downloader.deleteQuietly(p);
				}
			}
		} catch (IOException ignored) {
			// nothing to clean
		}
	}

	/** Path inside the folder for a file name made from a validated version; rejects anything that escapes it. */
	static Path inside(Path folder, String fileName) {
		Path p = folder.resolve(fileName).normalize();
		if (!folder.equals(p.getParent())) throw new IllegalArgumentException("outside the mods folder: " + fileName);
		return p;
	}

	/** Downloads, verifies and stages the release. Blocking; call off the main thread. */
	public synchronized void stage(Release release, Downloader downloader, LongConsumer progress) throws Downloader.DownloadException, InstallException {
		if (swapped) throw new InstallException("already updated");
		if (!ReleaseParser.TAG.matcher(release.version()).matches()) throw new InstallException("bad version");
		Path part = inside(folder, "golemaddons-" + release.version() + ".jar.part");
		Path finalJar = inside(folder, "golemaddons-" + release.version() + ".jar");
		if (finalJar.equals(currentJar)) throw new InstallException("that version is the one running");
		if (Files.exists(finalJar, LinkOption.NOFOLLOW_LINKS)) throw new InstallException(finalJar.getFileName() + " already exists");
		if (release.sha256() == null) throw new InstallException("no checksum");
		Downloader.deleteQuietly(part);
		downloader.download(java.net.URI.create(release.assetUrl()), release.size(), release.sha256(), part, progress);
		JarCheck.ModInfo info = JarCheck.read(part);
		if (info == null || !"golemaddons".equals(info.id()) || !release.version().equals(info.version())) {
			Downloader.deleteQuietly(part);
			throw new InstallException("the download is not GolemAddons " + release.version());
		}
		if (stagedPart != null && !stagedPart.equals(part)) Downloader.deleteQuietly(stagedPart);
		stagedPart = part;
		stagedFinal = finalJar;
		log("Downloaded and verified " + part.getFileName() + "; " + currentJar.getFileName() + " will be replaced when the game closes.");
	}

	public synchronized boolean staged() {
		return stagedPart != null && !swapped;
	}

	/** Swaps the jars if an update is staged. Called when the game stops; safe to call more than once. */
	public synchronized void swapIfStaged() {
		if (stagedPart == null || swapped) return;
		swapped = true;
		if (!Files.isRegularFile(stagedPart, LinkOption.NOFOLLOW_LINKS)) {
			log("The downloaded update " + stagedPart.getFileName() + " is gone; nothing to install.");
			return;
		}
		removeOlderCopies();
		try {
			Files.deleteIfExists(currentJar);
		} catch (IOException locked) {
			// Windows keeps the running jar locked until the game exits: finish in a helper process.
			log("Could not delete " + currentJar + " while the game runs (" + locked.getClass().getSimpleName()
					+ "); finishing after the game exits.");
			launchHelper();
			return;
		}
		try {
			try {
				Files.move(stagedPart, stagedFinal, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(stagedPart, stagedFinal);
			}
			log("Replaced " + currentJar.getFileName() + " with " + stagedFinal.getFileName() + " in " + folder + ".");
		} catch (IOException e) {
			log("Deleted " + currentJar.getFileName() + " but could not rename " + stagedPart.getFileName() + " to "
					+ stagedFinal.getFileName() + " (" + e + "). Rename it yourself to finish the update.");
		}
	}

	/**
	 * Other GolemAddons jars in the same folder (say a 0.1.4 next to the 0.1.6 that was running) would load instead
	 * of, or alongside, the update. Only files named golemaddons-*.jar that really are GolemAddons are removed.
	 */
	private void removeOlderCopies() {
		List<Path> old = new ArrayList<>();
		try (DirectoryStream<Path> dir = Files.newDirectoryStream(folder)) {
			for (Path p : dir) {
				if (p.equals(stagedFinal) || p.equals(currentJar) || !JAR.matcher(p.getFileName().toString()).matches()) continue;
				if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && JarCheck.isGolemAddons(p)) old.add(p);
			}
		} catch (IOException e) {
			return;
		}
		for (Path p : old) {
			try {
				Files.delete(p);
				log("Removed the older copy " + p.getFileName() + ".");
			} catch (IOException e) {
				log("Could not remove the older copy " + p.getFileName() + " (" + e.getClass().getSimpleName() + "); delete it yourself.");
			}
		}
	}

	private void launchHelper() {
		List<String> command = new ArrayList<>(List.of(javaExecutable(), "-cp", stagedPart.toString(), SwapHelper.class.getName(),
				Long.toString(ProcessHandle.current().pid()), currentJar.toString(), stagedPart.toString(), stagedFinal.toString(),
				folder.resolve(LOG_NAME).toString()));
		try {
			new ProcessBuilder(command).directory(folder.toFile())
					.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			log("Started the update helper (" + command.getFirst() + ").");
		} catch (IOException e) {
			log("Could not start the update helper (" + e + "). The update will be tried again when the game next closes.");
		}
	}

	/** The Java the game runs on; on Windows its windowless {@code javaw.exe}, so no console flashes up. */
	static String javaExecutable() {
		Path java = ProcessHandle.current().info().command().map(Path::of)
				.filter(Files::isRegularFile)
				.orElseGet(() -> {
					Path bin = Path.of(System.getProperty("java.home"), "bin");
					Path exe = bin.resolve("java.exe");
					return Files.isRegularFile(exe) ? exe : bin.resolve("java");
				});
		String name = java.getFileName().toString().toLowerCase(Locale.ROOT);
		if (name.equals("java.exe")) {
			Path javaw = java.resolveSibling("javaw.exe");
			if (Files.isRegularFile(javaw)) return javaw.toString();
		}
		return java.toString();
	}

	/** One line in the game log and in {@code golemaddons-update.log} next to the jar. */
	private void log(String message) {
		LOGGER.info("[update] {}", message);
		appendLog(folder.resolve(LOG_NAME), message);
	}

	static void appendLog(Path log, String message) {
		try {
			if (Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS) && Files.size(log) > MAX_LOG_BYTES) Files.delete(log);
			if (Files.isSymbolicLink(log)) return;
			Files.writeString(log, LocalDateTime.now().withNano(0) + "  " + message + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
			// the log is a convenience
		}
	}

	/** Installing failed for a reason other than the download itself. */
	public static final class InstallException extends Exception {
		InstallException(String message) {
			super(message, null, false, false);
		}
	}
}
