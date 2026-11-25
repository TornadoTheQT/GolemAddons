package com.golemaddons.config;

/** Test-only access to package-private config methods. */
public final class GolemConfigAccess {
	private GolemConfigAccess() {
	}

	public static void sanitize(GolemConfig config) {
		config.sanitize();
	}
}
