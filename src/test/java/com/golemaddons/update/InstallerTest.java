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
	void locateOnlyAcceptsAGolemAddonsJarDirectlyInMods() throws Exception {
		Path mods = modsWithCurrent("0.1.4");
		assertNotNull(UpdateInstaller.locate(List.of(mods.resolve("golemaddons-0.1.4.jar")), mods).installer());
		assertEquals(UpdateInstaller.Unavailable.NOT_A_JAR, UpdateInstaller.locate(List.of(mods), mods).unavailable());
		assertEquals(UpdateInstaller.Unavailable.NOT_IN_MODS,
				UpdateInstaller.locate(List.of(mods.resolve("fabric-api.jar")), mods).unavailable());
		Path elsewhere = TestJars.write(temp.resolve("golemaddons-0.1.4.jar"), TestJars.modJar("golemaddons", "0.1.4"));
		assertEquals(UpdateInstaller.Unavailable.NOT_IN_MODS, UpdateInstaller.locate(List.of(elsewhere), mods).unavailable());
	}

	@Test
	void stagesThenSwapsOnlyTheRunningJar() throws Exception {
		Path mods = modsWithCurrent("0.1.4");
		byte[] jar = TestJars.modJar("golemaddons", "0.1.5");
		files.put("/golemaddons-0.1.5.jar", jar);
		UpdateInstaller installer = UpdateInstaller.locate(List.of(mods.resolve("golemaddons-0.1.4.jar")), mods).installer();

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
		UpdateInstaller installer = UpdateInstaller.locate(List.of(mods.resolve("golemaddons-0.1.4.jar")), mods).installer();

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
