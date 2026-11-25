package com.golemaddons.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads a jar's {@code fabric.mod.json} without extracting anything, to tell what mod (and version) it is. */
public final class JarCheck {
	private static final int MAX_MOD_JSON = 64 * 1024;

	/** The mod id and version a jar declares. */
	public record ModInfo(String id, String version) {
	}

	private JarCheck() {
	}

	/** The jar's mod id and version, or null if it isn't a readable Fabric mod jar or has suspicious entry names. */
	public static ModInfo read(Path jar) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				String name = entries.nextElement().getName();
				if (name.startsWith("/") || name.startsWith("\\") || name.contains("..") || name.contains(":")) return null;
			}
			ZipEntry entry = zip.getEntry("fabric.mod.json");
			if (entry == null || entry.getSize() > MAX_MOD_JSON) return null;
			try (InputStream in = zip.getInputStream(entry)) {
				byte[] bytes = in.readNBytes(MAX_MOD_JSON + 1);
				if (bytes.length > MAX_MOD_JSON) return null;
				JsonElement root = JsonParser.parseReader(new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
				if (!root.isJsonObject()) return null;
				var o = root.getAsJsonObject();
				if (!o.has("id") || !o.has("version")) return null;
				return new ModInfo(o.get("id").getAsString(), o.get("version").getAsString());
			}
		} catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException e) {
			return null;
		}
	}

	/** True when the jar is GolemAddons (any version). */
	public static boolean isGolemAddons(Path jar) {
		ModInfo info = read(jar);
		return info != null && "golemaddons".equals(info.id());
	}
}
