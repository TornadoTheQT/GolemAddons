package com.golemaddons.cosmetics;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** The cosmetics server address: https, or plain http only to this machine for {@code wrangler dev}. */
public final class ApiUrl {
	private ApiUrl() {
	}

	/** The normalized base URI, or null if the value isn't an acceptable server address. */
	public static URI parse(String value) {
		if (value == null || value.isBlank() || value.length() > 256) {
			return null;
		}
		try {
			URI uri = new URI(value.strip());
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			if (uri.isOpaque() || host.isEmpty() || uri.getRawUserInfo() != null || uri.getRawQuery() != null
					|| uri.getRawFragment() != null) {
				return null;
			}
			boolean local = host.equals("localhost") || host.equals("127.0.0.1");
			if (!scheme.equals("https") && !(scheme.equals("http") && local)) {
				return null;
			}
			return uri;
		} catch (URISyntaxException e) {
			return null;
		}
	}
}
