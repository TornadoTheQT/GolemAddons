package com.golemaddons.cosmetics;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cheap checks on a cape URL before it is sent to the cosmetics server. The client never downloads this URL; the
 * Worker does, and repeats these checks (plus redirects, size and image checks) on every hop. See
 * worker/src/validate.ts.
 */
public final class CapeUrlRules {
	public static final int MAX_LENGTH = 512;

	public enum Problem { EMPTY, TOO_LONG, MALFORMED, NOT_HTTPS, BAD_PORT, HAS_CREDENTIALS, IP_ADDRESS, PRIVATE_HOST, BAD_HOST }

	public record Result(String url, Problem problem) {
		public boolean valid() {
			return problem == null;
		}
	}

	private static final Pattern HOST_CHARS = Pattern.compile("[a-z0-9.-]+");
	/** Every label numeric (decimal, octal or hex): forms like 2130706433, 0x7f.1 or 127.1 that resolve to an IP. */
	private static final Pattern NUMERIC_HOST = Pattern.compile("(?:0x[0-9a-f]*|[0-9]+)(?:\\.(?:0x[0-9a-f]*|[0-9]+))*\\.?");
	private static final List<String> PRIVATE_SUFFIXES = List.of(".localhost", ".local", ".internal", ".lan", ".home.arpa",
			".intranet", ".corp", ".private");

	private CapeUrlRules() {
	}

	/** @param ownHost the cosmetics server's host, which may not be used as a cape source (may be null) */
	public static Result check(String input, String ownHost) {
		if (input == null || input.isBlank()) {
			return new Result(null, Problem.EMPTY);
		}
		String url = input.strip();
		if (url.length() > MAX_LENGTH) {
			return new Result(null, Problem.TOO_LONG);
		}
		for (int i = 0; i < url.length(); i++) {
			char c = url.charAt(i);
			if (c <= 0x20 || c >= 0x7F) {
				return new Result(null, Problem.MALFORMED); // spaces, controls, raw unicode: must be percent-encoded
			}
		}
		URI uri;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			return new Result(null, Problem.MALFORMED);
		}
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque()) {
			return new Result(null, Problem.NOT_HTTPS);
		}
		if (uri.getRawUserInfo() != null || uri.getRawAuthority() == null || uri.getRawAuthority().contains("@")) {
			return new Result(null, Problem.HAS_CREDENTIALS);
		}
		if (uri.getPort() != -1 && uri.getPort() != 443) {
			return new Result(null, Problem.BAD_PORT);
		}
		String host = uri.getHost();
		if (host == null) {
			// URI gives no host for authorities it can't parse as a server, such as "0x7f.1" (an IP in disguise).
			String authority = uri.getRawAuthority();
			if (authority.startsWith("[")) {
				return new Result(null, Problem.IP_ADDRESS);
			}
			int colon = authority.lastIndexOf(':');
			host = colon >= 0 ? authority.substring(0, colon) : authority;
			Problem p = checkHost(host, ownHost);
			return new Result(null, p != null ? p : Problem.BAD_HOST);
		}
		Problem hostProblem = checkHost(host, ownHost);
		return hostProblem == null ? new Result(uri.toString(), null) : new Result(null, hostProblem);
	}

	static Problem checkHost(String rawHost, String ownHost) {
		if (rawHost.startsWith("[")) {
			return Problem.IP_ADDRESS;
		}
		String host = rawHost.toLowerCase(Locale.ROOT);
		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		if (host.isEmpty() || !HOST_CHARS.matcher(host).matches() || host.startsWith(".") || host.contains("..")) {
			return Problem.BAD_HOST;
		}
		if (NUMERIC_HOST.matcher(host).matches()) {
			return Problem.IP_ADDRESS;
		}
		String lastLabel = host.substring(host.lastIndexOf('.') + 1);
		if (!lastLabel.isEmpty() && lastLabel.chars().allMatch(Character::isDigit)) {
			return Problem.IP_ADDRESS; // no real TLD is numeric
		}
		if (!host.contains(".") || host.equals("localhost")) {
			return Problem.PRIVATE_HOST;
		}
		for (String suffix : PRIVATE_SUFFIXES) {
			if (host.endsWith(suffix)) {
				return Problem.PRIVATE_HOST;
			}
		}
		if (ownHost != null && host.equals(ownHost.toLowerCase(Locale.ROOT))) {
			return Problem.PRIVATE_HOST;
		}
		return null;
	}
}
