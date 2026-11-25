package com.golemaddons.update;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds small fake mod jars for the updater tests. */
final class TestJars {
	private TestJars() {
	}

	static byte[] modJar(String id, String version, String... extraEntries) throws IOException {
		var bytes = new java.io.ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			for (String extra : extraEntries) {
				zip.putNextEntry(new ZipEntry(extra));
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

	static Path write(Path file, byte[] bytes) throws IOException {
		try (OutputStream out = Files.newOutputStream(file)) {
			out.write(bytes);
		}
		return file;
	}

	static String sha256(byte[] bytes) throws Exception {
		return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
