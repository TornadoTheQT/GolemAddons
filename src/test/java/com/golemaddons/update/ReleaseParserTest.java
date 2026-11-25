package com.golemaddons.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ReleaseParserTest {
	private static final String DIGEST = "d2b659c7f2522e76d532ede8e967bf9f9a3c24be0dc8d5aa56ebdf4b50d6bb4a";

	static String release(String tag, boolean prerelease, String assetName, String url, String digest) {
		return """
				{"tag_name":"%s","draft":false,"prerelease":%s,"body":"Notes\\r\\nline 2","assets":[
				 {"name":"other.txt","size":5,"browser_download_url":"https://github.com/x/y"},
				 {"name":"%s","size":315109,%s"browser_download_url":"%s"}]}"""
				.formatted(tag, prerelease, assetName, digest == null ? "" : "\"digest\":\"" + digest + "\",", url);
	}

	static String good(String version) {
		return release("v" + version, false, "golemaddons-" + version + ".jar",
				UpdateSource.DOWNLOAD_PREFIX + "v" + version + "/golemaddons-" + version + ".jar", "sha256:" + DIGEST);
	}

	private static ReleaseParser.Problem problemOf(String json) {
		return assertThrows(ReleaseParser.ParseException.class, () -> ReleaseParser.parse(JsonParser.parseString(json))).problem();
	}

	@Test
	void parsesTheRealShape() throws Exception {
		Release r = ReleaseParser.parse(JsonParser.parseString(good("0.1.5")));
		assertEquals("0.1.5", r.version());
		assertEquals("v0.1.5", r.tag());
		assertEquals("golemaddons-0.1.5.jar", r.assetName());
		assertEquals(315109, r.size());
		assertEquals(DIGEST, r.sha256());
		assertEquals("Notes\nline 2", r.notes());
		assertEquals("https://github.com/TornadoTheQT/GolemAddons/releases/tag/v0.1.5", r.pageUrl());
		assertFalse(r.prerelease());
	}

	@Test
	void missingOrInvalidDigestMeansNoChecksum() throws Exception {
		String url = UpdateSource.DOWNLOAD_PREFIX + "v0.1.5/golemaddons-0.1.5.jar";
		assertNull(ReleaseParser.parse(JsonParser.parseString(release("v0.1.5", false, "golemaddons-0.1.5.jar", url, null))).sha256());
		assertNull(ReleaseParser.parse(JsonParser.parseString(release("v0.1.5", false, "golemaddons-0.1.5.jar", url, "md5:abc"))).sha256());
	}

	@Test
	void rejectsAssetsServedFromAnywhereElse() {
		assertEquals(ReleaseParser.Problem.NO_ASSET, problemOf(release("v0.1.5", false, "golemaddons-0.1.5.jar",
				"https://evil.example/golemaddons-0.1.5.jar", "sha256:" + DIGEST)));
		// A jar for a different version than the tag.
		assertEquals(ReleaseParser.Problem.NO_ASSET, problemOf(release("v0.1.5", false, "golemaddons-0.1.4.jar",
				UpdateSource.DOWNLOAD_PREFIX + "v0.1.5/golemaddons-0.1.4.jar", "sha256:" + DIGEST)));
	}

	@Test
	void rejectsOddTagsDraftsAndNonObjects() {
		assertEquals(ReleaseParser.Problem.NO_RELEASE, problemOf(good("0.1.5").replace("v0.1.5\"", "../../evil\"")));
		assertEquals(ReleaseParser.Problem.NO_RELEASE, problemOf(good("0.1.5").replace("\"draft\":false", "\"draft\":true")));
		assertEquals(ReleaseParser.Problem.MALFORMED, problemOf("[]"));
		assertEquals(ReleaseParser.Problem.MALFORMED, problemOf("{\"tag_name\":\"v1.0.0\"}"));
	}

	@Test
	void newestPicksTheHighestVersionAndRespectsPrereleases() throws Exception {
		String pre = release("v0.3.0-beta.1", true, "golemaddons-0.3.0-beta.1.jar",
				UpdateSource.DOWNLOAD_PREFIX + "v0.3.0-beta.1/golemaddons-0.3.0-beta.1.jar", "sha256:" + DIGEST);
		String json = "[" + good("0.1.9") + "," + pre + "," + good("0.1.10") + ",{\"broken\":true}]";
		assertEquals("0.1.10", ReleaseParser.newest(JsonParser.parseString(json), false).version());
		assertEquals("0.3.0-beta.1", ReleaseParser.newest(JsonParser.parseString(json), true).version());
		assertThrows(ReleaseParser.ParseException.class, () -> ReleaseParser.newest(JsonParser.parseString("[]"), true));
	}

	@Test
	void versionsCompareSemantically() {
		assertTrue(Versions.isNewer("0.1.10", "0.1.9"));
		assertTrue(Versions.isNewer("v0.2.0", "0.1.4"));
		assertFalse(Versions.isNewer("0.1.4", "0.1.4"));
		assertFalse(Versions.isNewer("0.1.3", "0.1.4"));
		assertFalse(Versions.isNewer("0.2.0-beta.1", "0.2.0"));
		assertTrue(Versions.isNewer("0.1.5", "0.1.4+dev"));
		assertFalse(Versions.isNewer("garbage", "0.1.4"));
		assertFalse(Versions.isNewer("0.1.5", "not a version"));
	}

	@Test
	void downloadPolicyAllowsOnlyGithubOverHttps() {
		assertTrue(UpdateSource.allowedDownload(java.net.URI.create("https://github.com/TornadoTheQT/GolemAddons/releases/download/v1/x.jar")));
		assertTrue(UpdateSource.allowedDownload(java.net.URI.create("https://release-assets.githubusercontent.com/a/b?sig=1")));
		assertFalse(UpdateSource.allowedDownload(java.net.URI.create("http://github.com/x")));
		assertFalse(UpdateSource.allowedDownload(java.net.URI.create("https://github.com.evil.example/x")));
		assertFalse(UpdateSource.allowedDownload(java.net.URI.create("https://user@github.com/x")));
		assertFalse(UpdateSource.allowedDownload(java.net.URI.create("https://github.com:8443/x")));
	}
}
