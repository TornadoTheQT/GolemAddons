package com.golemaddons.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

/**
 * Downloads one file to disk with every check done while streaming: each redirect must pass the URL policy, the
 * size may not exceed what was announced (nor the hard cap), and the SHA-256 must match. On any failure the
 * partial file is deleted.
 */
public final class Downloader {
	private static final int MAX_REDIRECTS = 5;

	public enum Problem { NETWORK, BLOCKED_REDIRECT, HTTP_STATUS, TOO_LARGE, SIZE_MISMATCH, CHECKSUM }

	/** A failed download; {@link #problem()} says why. */
	public static final class DownloadException extends Exception {
		private final Problem problem;

		DownloadException(Problem problem, String detail) {
			super(problem + (detail == null ? "" : ": " + detail), null, false, false);
			this.problem = problem;
		}

		public Problem problem() {
			return problem;
		}
	}

	private final HttpClient http;
	private final Predicate<URI> allowed;
	private final String userAgent;

	public Downloader(HttpClient http, Predicate<URI> allowed, String userAgent) {
		this.http = http;
		this.allowed = allowed;
		this.userAgent = userAgent;
	}

	/** Downloads {@code uri} to {@code target} (which must not exist yet) and returns once it is fully verified. */
	public void download(URI uri, long expectedSize, String expectedSha256, Path target, LongConsumer progress) throws DownloadException {
		if (expectedSize <= 0 || expectedSize > UpdateSource.MAX_JAR_BYTES) throw new DownloadException(Problem.TOO_LARGE, null);
		URI current = uri;
		try {
			for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
				if (!allowed.test(current)) throw new DownloadException(Problem.BLOCKED_REDIRECT, String.valueOf(current.getHost()));
				HttpRequest request = HttpRequest.newBuilder(current).timeout(Duration.ofSeconds(60))
						.header("User-Agent", userAgent).header("Accept", "application/octet-stream").GET().build();
				HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
				int status = response.statusCode();
				if (status >= 300 && status < 400) {
					response.body().close();
					String location = response.headers().firstValue("Location").orElse(null);
					if (location == null) throw new DownloadException(Problem.HTTP_STATUS, String.valueOf(status));
					current = current.resolve(location);
					continue;
				}
				if (status != 200) {
					response.body().close();
					throw new DownloadException(Problem.HTTP_STATUS, String.valueOf(status));
				}
				try (InputStream in = response.body()) {
					write(in, expectedSize, expectedSha256, target, progress);
				}
				return;
			}
			throw new DownloadException(Problem.BLOCKED_REDIRECT, "too many redirects");
		} catch (IOException e) {
			deleteQuietly(target);
			throw new DownloadException(Problem.NETWORK, e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			deleteQuietly(target);
			throw new DownloadException(Problem.NETWORK, "interrupted");
		} catch (DownloadException e) {
			deleteQuietly(target);
			throw e;
		}
	}

	private static void write(InputStream in, long expectedSize, String expectedSha256, Path target, LongConsumer progress)
			throws IOException, DownloadException {
		MessageDigest sha;
		try {
			sha = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		long total = 0;
		byte[] buffer = new byte[16 * 1024];
		// CREATE_NEW: never write through an existing file or symlink someone placed there.
		try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
				LinkOption.NOFOLLOW_LINKS)) {
			int n;
			while ((n = in.read(buffer)) != -1) {
				total += n;
				if (total > expectedSize) throw new DownloadException(Problem.SIZE_MISMATCH, "more than " + expectedSize + " bytes");
				sha.update(buffer, 0, n);
				out.write(buffer, 0, n);
				progress.accept(total);
			}
		}
		if (total != expectedSize) throw new DownloadException(Problem.SIZE_MISMATCH, total + " of " + expectedSize + " bytes");
		String actual = HexFormat.of().formatHex(sha.digest());
		if (!MessageDigest.isEqual(actual.getBytes(), expectedSha256.getBytes())) {
			throw new DownloadException(Problem.CHECKSUM, null);
		}
	}

	static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// Cleaned up on the next launch instead.
		}
	}
}
