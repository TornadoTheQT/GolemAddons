package com.golemaddons.update;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts a verified release in the mods folder so it is used from the next start.
 *
 * <p>The new jar is downloaded as {@code golemaddons-<version>.jar.part} (Fabric ignores it under that name) and only
 * becomes a {@code .jar} after the game stops, at the same moment the old jar is removed, so there are never two
 * copies of the mod. Every file this touches is inside the mods folder, and the only jar it ever deletes is the one
 * the running mod was loaded from, after checking that it really is GolemAddons.
 */
public final class UpdateInstaller {
	private static final Logger LOGGER = LoggerFactory.getLogger("golemaddons");
	private static final Pattern PART = Pattern.compile("^golemaddons-[0-9A-Za-z.+-]{1,48}\\.jar\\.part$");
	private static final Pattern JAR = Pattern.compile("^golemaddons-[0-9A-Za-z.+-]{1,48}\\.jar$");

	/** Why installing isn't possible from this copy of the game. */
	public enum Unavailable {
		/** Running from a development build or a folder, not a jar. */
		NOT_A_JAR,
		/** The jar isn't in the game's mods folder or one folder below it (a launcher-managed or nested copy). */
		NOT_IN_MODS
	}

	private final Path modsDir;
	private final Path currentJar;
	private Path stagedPart;
	private Path stagedFinal;
	private boolean swapped;

	private UpdateInstaller(Path modsDir, Path currentJar) {
		this.modsDir = modsDir;
		this.currentJar = currentJar;
	}

	/** An installer for the running jar, or a reason why there can't be one. */
	public record Located(UpdateInstaller installer, Unavailable unavailable) {
	}

	/**
	 * {@code origin} is where the running mod was loaded from; {@code modsDir} the game's mods folder. The jar may sit
	 * in the mods folder itself or one folder below it: Lunar Client keeps mods in {@code mods/fabric-<version>/}.
	 * Updates are installed next to the running jar, in whichever of the two it is.
	 */
	public static Located locate(List<Path> origin, Path modsDir) {
		if (origin.size() != 1) return new Located(null, Unavailable.NOT_A_JAR);
		try {
			Path jar = origin.get(0);
			if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || !jar.getFileName().toString().endsWith(".jar")) {
				return new Located(null, Unavailable.NOT_A_JAR);
			}
			Path realMods = modsDir.toRealPath();
			// The jar itself was checked not to be a symlink above; resolving its parents (e.g. macOS /var -> /private/var)
			// is needed to compare it with the real mods folder.
			Path realJar = jar.toRealPath();
			Path folder = realJar.getParent();
			boolean inMods = realMods.equals(folder) || (folder != null && realMods.equals(folder.getParent()));
			if (!inMods || !JarCheck.isGolemAddons(realJar)) {
				return new Located(null, Unavailable.NOT_IN_MODS);
			}
			return new Located(new UpdateInstaller(folder, realJar), null);
		} catch (IOException e) {
			return new Located(null, Unavailable.NOT_IN_MODS);
		}
	}

	/** The folder updates are installed into (where the running jar is). */
	public Path folder() {
		return modsDir;
	}

	/** Deletes leftover {@code golemaddons-*.jar.part} files from an earlier update. Call once at startup. */
	public void cleanUpLeftovers() {
		cleanUpLeftovers(modsDir);
	}

	static void cleanUpLeftovers(Path modsDir) {
		List<Path> stale = new ArrayList<>();
		try (DirectoryStream<Path> dir = Files.newDirectoryStream(modsDir)) {
			for (Path p : dir) {
				if (PART.matcher(p.getFileName().toString()).matches() && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) stale.add(p);
			}
		} catch (IOException e) {
			return;
		}
		stale.forEach(Downloader::deleteQuietly);
	}

	/** Path inside the mods folder for a file name made from a validated version; rejects anything that escapes it. */
	static Path inside(Path modsDir, String fileName) {
		Path p = modsDir.resolve(fileName).normalize();
		if (!modsDir.equals(p.getParent())) throw new IllegalArgumentException("outside the mods folder: " + fileName);
		return p;
	}

	/** Downloads, verifies and stages the release. Blocking; call off the main thread. */
	public synchronized void stage(Release release, Downloader downloader, LongConsumer progress) throws Downloader.DownloadException, InstallException {
		if (swapped) throw new InstallException("already updated");
		if (!ReleaseParser.TAG.matcher(release.version()).matches()) throw new InstallException("bad version");
		Path part = inside(modsDir, "golemaddons-" + release.version() + ".jar.part");
		Path finalJar = inside(modsDir, "golemaddons-" + release.version() + ".jar");
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
	}

	public synchronized boolean staged() {
		return stagedPart != null && !swapped;
	}

	/** Swaps the jars if an update is staged. Called when the game stops; safe to call more than once. */
	public synchronized void swapIfStaged() {
		if (stagedPart == null || swapped) return;
		swapped = true;
		if (!Files.isRegularFile(stagedPart, LinkOption.NOFOLLOW_LINKS)) return;
		try {
			Files.delete(currentJar);
		} catch (IOException locked) {
			// Windows keeps the running jar locked until the game exits: finish in a helper process.
			removeOlderCopies();
			launchHelper();
			return;
		}
		try {
			try {
				Files.move(stagedPart, stagedFinal, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(stagedPart, stagedFinal);
			}
			LOGGER.info("Installed {}; it will be used from the next start.", stagedFinal.getFileName());
			removeOlderCopies();
		} catch (IOException e) {
			LOGGER.error("Could not move {} into place. Rename it to {} yourself to finish the update.",
					stagedPart.getFileName(), stagedFinal.getFileName(), e);
		}
	}

	/**
	 * Other GolemAddons jars left in the same folder (say a 0.1.4 next to the 0.1.6 that was running) would load
	 * instead of, or alongside, the update. Only files named golemaddons-*.jar that really are GolemAddons are removed.
	 */
	private void removeOlderCopies() {
		List<Path> old = new ArrayList<>();
		try (DirectoryStream<Path> dir = Files.newDirectoryStream(modsDir)) {
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
				LOGGER.info("Removed the older copy {}.", p.getFileName());
			} catch (IOException e) {
				LOGGER.warn("Could not remove the older copy {}; delete it yourself.", p.getFileName());
			}
		}
	}

	private void launchHelper() {
		String java = ProcessHandle.current().info().command()
				.orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
		try {
			new ProcessBuilder(java, "-cp", stagedPart.toString(), SwapHelper.class.getName(),
					Long.toString(ProcessHandle.current().pid()), currentJar.toString(), stagedPart.toString(), stagedFinal.toString())
					.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			LOGGER.info("Update to {} will finish once the game has closed.", stagedFinal.getFileName());
		} catch (IOException e) {
			LOGGER.error("Could not finish the update. Delete {} and rename {} to {} yourself.", currentJar.getFileName(),
					stagedPart.getFileName(), stagedFinal.getFileName(), e);
		}
	}

	/** Installing failed for a reason other than the download itself. */
	public static final class InstallException extends Exception {
		InstallException(String message) {
			super(message, null, false, false);
		}
	}
}
