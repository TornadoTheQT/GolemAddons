package com.golemaddons.update;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Download verification, jar checks and the jar swap, against a local HTTP server and a temporary mods folder. */
class InstallerTest {
	@TempDir
	Path temp;

	private HttpServer server;
	private final Map<String, byte[]> files = new ConcurrentHashMap<>();
	private final Map<String, String> redirects = new ConcurrentHashMap<>();
	private Downloader downloader;

	@BeforeEach
	void start() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			String to = redirects.get(path);
			if (to != null) {
				exchange.getResponseHeaders().add("Location", to);
				exchange.sendResponseHeaders(302, -1);
			} else if (files.containsKey(path)) {
				byte[] body = files.get(path);
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			} else {
				exchange.sendResponseHeaders(404, -1);
			}
			exchange.close();
		});
		server.start();
		// Tests allow the local server only; production allows only GitHub over https (see ReleaseParserTest).
		downloader = new Downloader(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
				uri -> "127.0.0.1".equals(uri.getHost()) && uri.getPort() == server.getAddress().getPort(), "GolemAddons/test");
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private URI url(String path) {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
	}

	private Release release(String version, byte[] jar, String sha) {
		return new Release(version, "v" + version, "", false, "golemaddons-" + version + ".jar",
				url("/golemaddons-" + version + ".jar").toString(), jar.length, sha);
	}

	private Path modsWithCurrent(String version) throws Exception {
		Path mods = Files.createDirectory(temp.resolve("mods"));
		TestJars.write(mods.resolve("golemaddons-" + version + ".jar"), TestJars.modJar("golemaddons", version));
		TestJars.write(mods.resolve("fabric-api.jar"), TestJars.modJar("fabric-api", "1.0.0"));
		return mods;
	}

	@Test
	void downloadsFollowAllowedRedirectsAndVerifyTheChecksum() throws Exception {
		byte[] jar = TestJars.modJar("golemaddons", "0.1.5");
		files.put("/real.jar", jar);
		redirects.put("/start", url("/real.jar").toString());
		Path out = temp.resolve("out.part");
		downloader.download(url("/start"), jar.length, TestJars.sha256(jar), out, n -> { });
		assertArrayEquals(jar, Files.readAllBytes(out));
	}

	@Test
	void wrongChecksumWrongSizeOrForeignRedirectLeaveNoFile() throws Exception {
		byte[] jar = TestJars.modJar("golemaddons", "0.1.5");
		files.put("/real.jar", jar);
		Path out = temp.resolve("out.part");

		var e = assertThrows(Downloader.DownloadException.class,
				() -> downloader.download(url("/real.jar"), jar.length, "0".repeat(64), out, n -> { }));
		assertEquals(Downloader.Problem.CHECKSUM, e.problem());
		assertFalse(Files.exists(out));

		e = assertThrows(Downloader.DownloadException.class,
				() -> downloader.download(url("/real.jar"), jar.length - 10, TestJars.sha256(jar), out, n -> { }));
		assertEquals(Downloader.Problem.SIZE_MISMATCH, e.problem());
		assertFalse(Files.exists(out));

		redirects.put("/away", "https://evil.example/x.jar");
		e = assertThrows(Downloader.DownloadException.class,
				() -> downloader.download(url("/away"), jar.length, TestJars.sha256(jar), out, n -> { }));
		assertEquals(Downloader.Problem.BLOCKED_REDIRECT, e.problem());

		e = assertThrows(Downloader.DownloadException.class,
				() -> downloader.download(url("/real.jar"), UpdateSource.MAX_JAR_BYTES + 1, TestJars.sha256(jar), out, n -> { }));
		assertEquals(Downloader.Problem.TOO_LARGE, e.problem());
	}

	@Test
	void jarCheckReadsTheModIdAndRejectsZipSlipNames() throws Exception {
		Path good = TestJars.write(temp.resolve("a.jar"), TestJars.modJar("golemaddons", "0.1.5"));
		assertEquals(new JarCheck.ModInfo("golemaddons", "0.1.5"), JarCheck.read(good));
		Path slip = TestJars.write(temp.resolve("b.jar"), TestJars.modJar("golemaddons", "0.1.5", "../../evil.class"));
		assertNull(JarCheck.read(slip));
		Path notZip = TestJars.write(temp.resolve("c.jar"), "hello".getBytes());
		assertNull(JarCheck.read(notZip));
		assertFalse(JarCheck.isGolemAddons(TestJars.write(temp.resolve("d.jar"), TestJars.modJar("other", "1.0.0"))));
	}

	@Test
	void locateFindsTheLoadedJarWhereverItIs() throws Exception {
		Path mods = modsWithCurrent("0.1.4");
		assertEquals(mods.resolve("golemaddons-0.1.4.jar").toRealPath(),
				UpdateInstaller.locate(List.of(mods.resolve("golemaddons-0.1.4.jar")), "0.1.4").installer().currentJar());
		assertEquals(UpdateInstaller.Unavailable.NOT_A_JAR, UpdateInstaller.locate(List.of(mods), "0.1.4").unavailable());
		assertEquals(UpdateInstaller.Unavailable.NOT_A_JAR,
				UpdateInstaller.locate(List.of(mods.resolve("fabric-api.jar")), "0.1.4").unavailable(), "another mod's jar is never used");
		// Any folder at all, however deep, as long as it is the jar that was loaded.
		Path deep = Files.createDirectories(temp.resolve("Some Launcher/instances/my pack/minecraft/mods/extra"));
		Path elsewhere = TestJars.write(deep.resolve("GolemAddons (1).jar"), TestJars.modJar("golemaddons", "0.1.4"));
		UpdateInstaller installer = UpdateInstaller.locate(List.of(mods, elsewhere), "0.1.4").installer();
		assertEquals(elsewhere.toRealPath(), installer.currentJar(), "the first candidate that is our jar wins");
		assertEquals(deep.toRealPath(), installer.folder());
	}

	@Test
	void aRenamedJarAnywhereIsReplacedAndTheUpdateLandsBesideIt() throws Exception {
		Path folder = Files.createDirectories(temp.resolve("instance/mods"));
		Path running = TestJars.write(folder.resolve("GolemAddons-latest.jar"), TestJars.modJar("golemaddons", "0.1.9"));
		UpdateInstaller installer = UpdateInstaller.locate(List.of(running), "0.1.9").installer();
		byte[] jar = TestJars.modJar("golemaddons", "0.1.10");
		files.put("/golemaddons-0.1.10.jar", jar);
		installer.stage(release("0.1.10", jar, TestJars.sha256(jar)), downloader, n -> { });
		installer.swapIfStaged();
		assertFalse(Files.exists(running));
		assertArrayEquals(jar, Files.readAllBytes(folder.resolve("golemaddons-0.1.10.jar")));
		assertTrue(Files.readString(folder.resolve(UpdateInstaller.LOG_NAME)).contains("Replaced GolemAddons-latest.jar"));
	}

	@Test
	void anUnfinishedUpdateIsResumedAtTheNextStart() throws Exception {
		Path mods = modsWithCurrent("0.1.4");
		TestJars.write(mods.resolve("golemaddons-0.1.6.jar.part"), TestJars.modJar("golemaddons", "0.1.6"));
		TestJars.write(mods.resolve("golemaddons-0.1.5.jar.part"), TestJars.modJar("golemaddons", "0.1.5"));
		TestJars.write(mods.resolve("golemaddons-0.1.3.jar.part"), TestJars.modJar("golemaddons", "0.1.3"));
		TestJars.write(mods.resolve("golemaddons-0.1.7.jar.part"), TestJars.modJar("someone-else", "0.1.7"));
		Files.writeString(mods.resolve("golemaddons-0.1.6.jar.new"), "half copied");
		UpdateInstaller installer = UpdateInstaller.locate(List.of(mods.resolve("golemaddons-0.1.4.jar")), "0.1.4").installer();

		assertEquals("0.1.6", installer.resumeOrCleanUp(), "the newest real GolemAddons download is picked up again");
		assertTrue(installer.staged());
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.5.jar.part")));
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.3.jar.part")));
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.7.jar.part")), "a mislabelled download is a leftover");
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.6.jar.new")));

		installer.swapIfStaged();
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.4.jar")));
		assertTrue(Files.exists(mods.resolve("golemaddons-0.1.6.jar")));
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.6.jar.part")));
	}

	/** Lunar Client keeps mods one folder down (mods/fabric-26.1.2/); updates go next to the running jar there. */
	@Test
	void lunarStyleSubfolderInstallsNextToTheRunningJarAndClearsOldCopies() throws Exception {
		Path mods = Files.createDirectories(temp.resolve("lunar/mods"));
		Path sub = Files.createDirectories(mods.resolve("fabric-26.1.2"));
		TestJars.write(sub.resolve("golemaddons-0.1.4.jar"), TestJars.modJar("golemaddons", "0.1.4"));
		Path running = TestJars.write(sub.resolve("golemaddons-0.1.6.jar"), TestJars.modJar("golemaddons", "0.1.6"));
		TestJars.write(sub.resolve("fabric-api.jar"), TestJars.modJar("fabric-api", "1.0.0"));
		TestJars.write(sub.resolve("golemaddons-notes.jar"), TestJars.modJar("someone-else", "1.0.0"));

		UpdateInstaller installer = UpdateInstaller.locate(List.of(running), "0.1.6").installer();
		assertNotNull(installer);
		assertEquals(sub.toRealPath(), installer.folder());

		byte[] jar = TestJars.modJar("golemaddons", "0.1.7");
		files.put("/golemaddons-0.1.7.jar", jar);
		installer.stage(release("0.1.7", jar, TestJars.sha256(jar)), downloader, n -> { });
		installer.swapIfStaged();
		assertArrayEquals(jar, Files.readAllBytes(sub.resolve("golemaddons-0.1.7.jar")));
		assertFalse(Files.exists(running));
		assertFalse(Files.exists(sub.resolve("golemaddons-0.1.4.jar")), "an older copy left beside it is removed too");
		assertTrue(Files.exists(sub.resolve("fabric-api.jar")));
		assertTrue(Files.exists(sub.resolve("golemaddons-notes.jar")), "only jars that really are GolemAddons are removed");
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.7.jar")));
	}

	/** The Windows path: the helper process (run here in-process, the game already gone) finishes the swap. */
	@Test
	void theHelperReplacesTheOldJarOnceTheGameHasExited() throws Exception {
		Path folder = Files.createDirectories(temp.resolve("win/mods"));
		Path old = TestJars.write(folder.resolve("golemaddons-0.1.9.jar"), TestJars.modJar("golemaddons", "0.1.9"));
		byte[] jar = TestJars.modJar("golemaddons", "0.1.10");
		Path part = TestJars.write(folder.resolve("golemaddons-0.1.10.jar.part"), jar);
		Path log = folder.resolve(UpdateInstaller.LOG_NAME);
		long gone = Long.MAX_VALUE; // no such process
		SwapHelper.main(new String[] {Long.toString(gone), old.toString(), part.toString(), folder.resolve("golemaddons-0.1.10.jar").toString(),
				log.toString()});
		assertFalse(Files.exists(old));
		assertArrayEquals(jar, Files.readAllBytes(folder.resolve("golemaddons-0.1.10.jar")));
		assertFalse(Files.exists(folder.resolve("golemaddons-0.1.10.jar.new")));
		assertTrue(Files.readString(log).contains("[helper] Replaced golemaddons-0.1.9.jar"));

		// Paths outside the jar's own folder are refused outright.
		Path other = Files.createDirectories(temp.resolve("elsewhere"));
		Path victim = TestJars.write(other.resolve("victim.jar"), TestJars.modJar("x", "1"));
		SwapHelper.main(new String[] {Long.toString(gone), victim.toString(), part.toString(), folder.resolve("golemaddons-0.1.11.jar").toString(),
				log.toString()});
		assertTrue(Files.exists(victim));
	}

	@Test
	void aDownloadThatIsNotNewerIsJustCleanedUp() throws Exception {
		Path mods = modsWithCurrent("0.1.6");
		TestJars.write(mods.resolve("golemaddons-0.1.6.jar.part"), TestJars.modJar("golemaddons", "0.1.6"));
		UpdateInstaller installer = UpdateInstaller.locate(List.of(mods.resolve("golemaddons-0.1.6.jar")), "0.1.6").installer();
		assertNull(installer.resumeOrCleanUp());
		assertFalse(installer.staged());
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.6.jar.part")));
	}

	@Test
	void stagesThenSwapsOnlyTheRunningJar() throws Exception {
		Path mods = modsWithCurrent("0.1.4");
		byte[] jar = TestJars.modJar("golemaddons", "0.1.5");
		files.put("/golemaddons-0.1.5.jar", jar);
		UpdateInstaller installer = UpdateInstaller.locate(List.of(mods.resolve("golemaddons-0.1.4.jar")), "0.1.4").installer();

		installer.stage(release("0.1.5", jar, TestJars.sha256(jar)), downloader, n -> { });
		assertTrue(installer.staged());
		// Until the game stops there is only one loadable copy of the mod.
		assertTrue(Files.exists(mods.resolve("golemaddons-0.1.4.jar")));
		assertTrue(Files.exists(mods.resolve("golemaddons-0.1.5.jar.part")));
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.5.jar")));

		installer.swapIfStaged();
		installer.swapIfStaged(); // idempotent
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.4.jar")));
		assertArrayEquals(jar, Files.readAllBytes(mods.resolve("golemaddons-0.1.5.jar")));
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.5.jar.part")));
		assertTrue(Files.exists(mods.resolve("fabric-api.jar")), "other mods are never touched");
	}

	@Test
	void refusesUnverifiableOrMislabelledDownloads() throws Exception {
		Path mods = modsWithCurrent("0.1.4");
		UpdateInstaller installer = UpdateInstaller.locate(List.of(mods.resolve("golemaddons-0.1.4.jar")), "0.1.4").installer();

		byte[] jar = TestJars.modJar("golemaddons", "0.1.5");
		files.put("/golemaddons-0.1.5.jar", jar);
		assertThrows(UpdateInstaller.InstallException.class, () -> installer.stage(release("0.1.5", jar, null), downloader, n -> { }));

		// The file's own metadata says it's a different mod: rejected and removed.
		byte[] impostor = TestJars.modJar("not-golemaddons", "0.1.6");
		files.put("/golemaddons-0.1.6.jar", impostor);
		assertThrows(UpdateInstaller.InstallException.class,
				() -> installer.stage(release("0.1.6", impostor, TestJars.sha256(impostor)), downloader, n -> { }));
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.6.jar.part")));
		assertFalse(installer.staged());

		installer.swapIfStaged();
		assertTrue(Files.exists(mods.resolve("golemaddons-0.1.4.jar")), "nothing staged, nothing deleted");
	}

	@Test
	void pathsCannotEscapeTheModsFolderAndLeftoversAreCleaned() throws Exception {
		Path mods = modsWithCurrent("0.1.4");
		assertThrows(IllegalArgumentException.class, () -> UpdateInstaller.inside(mods, "../escape.jar"));
		assertThrows(IllegalArgumentException.class, () -> UpdateInstaller.inside(mods, "sub/dir.jar"));
		Files.writeString(mods.resolve("golemaddons-0.1.5.jar.part"), "x");
		Files.writeString(mods.resolve("someone-else.jar.part"), "x");
		UpdateInstaller.cleanUpLeftovers(mods);
		assertFalse(Files.exists(mods.resolve("golemaddons-0.1.5.jar.part")));
		assertTrue(Files.exists(mods.resolve("someone-else.jar.part")));
		assertTrue(Files.exists(mods.resolve("golemaddons-0.1.4.jar")));
	}
}
