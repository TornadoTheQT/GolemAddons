package com.golemaddons.update;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Where updates come from. Fixed at compile time on purpose: nothing in the config, chat, a server or the cosmetics
 * worker can point the updater anywhere else.
 */
public final class UpdateSource {
	public static final String OWNER = "TornadoTheQT";
	public static final String REPO = "GolemAddons";
	public static final URI LATEST = URI.create("https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest");
	public static final URI LIST = URI.create("https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases?per_page=20");
	public static final String DOWNLOAD_PREFIX = "https://github.com/" + OWNER + "/" + REPO + "/releases/download/";
	public static final String RELEASE_PAGE_PREFIX = "https://github.com/" + OWNER + "/" + REPO + "/releases/tag/";
	/** Hosts a download may redirect through (GitHub serves release files from its asset CDN). */
	public static final Set<String> DOWNLOAD_HOSTS = Set.of("github.com", "objects.githubusercontent.com",
			"release-assets.githubusercontent.com");
	/** A real jar is ~300 KB; anything near this is not ours. */
	public static final long MAX_JAR_BYTES = 20L * 1024 * 1024;

	private UpdateSource() {
	}

	/** True for an https URL on an allowed download host, with no user info and the default port. */
	public static boolean allowedDownload(URI uri) {
		return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
				&& (uri.getPort() == -1 || uri.getPort() == 443) && uri.getHost() != null
				&& DOWNLOAD_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
	}
}
