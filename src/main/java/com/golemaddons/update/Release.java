package com.golemaddons.update;

/**
 * A GitHub release of the mod that has an installable jar.
 *
 * @param version   the version from the tag, without a leading "v" (e.g. "0.1.5")
 * @param tag       the tag as published (e.g. "v0.1.5")
 * @param notes     release notes, untrusted plain text
 * @param assetUrl  where the jar downloads from (always under {@link UpdateSource#DOWNLOAD_PREFIX})
 * @param size      the jar's size in bytes as GitHub reports it
 * @param sha256    lowercase hex SHA-256 GitHub publishes for the jar, or null if it published none
 */
public record Release(String version, String tag, String notes, boolean prerelease, String assetName, String assetUrl, long size,
		String sha256) {
	/** The release page, built from the validated tag only. */
	public String pageUrl() {
		return UpdateSource.RELEASE_PAGE_PREFIX + tag;
	}
}
