package com.golemaddons.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cape textures, one per image hash however many players use it. Loading (disk cache or the Worker, hash and
 * header checks, PNG decode) runs on the cosmetics executor; only the GPU upload runs on the render thread.
 * Textures not drawn for {@value #IDLE_RELEASE_MS} ms, or beyond {@value #MAX_TEXTURES}, are released.
 */
final class CapeTextures {
	private static final Logger LOGGER = LoggerFactory.getLogger("golemaddons");
	static final int MAX_TEXTURES = 64;
	static final long IDLE_RELEASE_MS = 10 * 60_000L;
	private static final long FIRST_RETRY_MS = 60_000L;
	private static final long MAX_RETRY_MS = 30 * 60_000L;
	private static final long DISK_CACHE_BYTES = 20L * 1024 * 1024;

	/** Mutable per-hash state. {@code asset} is set once uploaded; the render thread only reads volatile fields. */
	private static final class Slot {
		final String hash;
		volatile ClientAsset.ResourceTexture asset;
		volatile boolean loading;
		volatile long retryAt;
		volatile long lastUsed;
		int failures;

		Slot(String hash) {
			this.hash = hash;
		}
	}

	private final Map<String, Slot> slots = new ConcurrentHashMap<>();
	private final Executor executor;
	private final Supplier<CosmeticsApi> api;
	private final Path diskCache;
	private final LongSupplier clock;
	private boolean diskCacheUsable = true;

	CapeTextures(Executor executor, Supplier<CosmeticsApi> api, Path diskCache, LongSupplier clock) {
		this.executor = executor;
		this.api = api;
		this.diskCache = diskCache;
		this.clock = clock;
	}

	/**
	 * The texture for this hash if it is ready, else null (and a load is started if none is running and no retry is
	 * pending). Render thread; no allocation once the slot exists.
	 */
	ClientAsset.ResourceTexture get(String hash) {
		Slot slot = slots.get(hash);
		long now = clock.getAsLong();
		if (slot == null) {
			slot = slots.computeIfAbsent(hash, Slot::new);
		}
		slot.lastUsed = now;
		ClientAsset.ResourceTexture asset = slot.asset;
		if (asset == null && !slot.loading && now >= slot.retryAt) {
			startLoad(slot);
		}
		return asset;
	}

	private void startLoad(Slot slot) {
		CosmeticsApi current = api.get();
		if (current == null) {
			return;
		}
		slot.loading = true;
		try {
			executor.execute(() -> load(slot, current));
		} catch (RuntimeException e) {
			slot.loading = false; // executor shut down or full; try again later
			slot.retryAt = clock.getAsLong() + FIRST_RETRY_MS;
		}
	}

	private void load(Slot slot, CosmeticsApi current) {
		NativeImage image = null;
		try {
			byte[] data = readDisk(slot.hash);
			if (data == null) {
				data = current.cape(slot.hash);
				verify(slot.hash, data);
				writeDisk(slot.hash, data);
			}
			image = NativeImage.read(data);
			if (!CapePng.allowedSize(image.getWidth(), image.getHeight())) {
				throw new IOException("decoded size differs from header");
			}
			NativeImage ready = image;
			image = null;
			Minecraft.getInstance().execute(() -> upload(slot, ready));
		} catch (Throwable t) {
			if (image != null) image.close();
			slot.failures++;
			slot.retryAt = clock.getAsLong() + Math.min(MAX_RETRY_MS, FIRST_RETRY_MS << Math.min(10, slot.failures - 1));
			slot.loading = false;
			if (slot.failures == 1) {
				LOGGER.info("GolemAddons: couldn't load cape {}: {}", slot.hash, t.toString());
			}
		}
	}

	/** Render thread. */
	private void upload(Slot slot, NativeImage image) {
		try {
			if (slots.get(slot.hash) != slot) {
				image.close(); // released while loading
				return;
			}
			Identifier id = textureId(slot.hash);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "golemaddons cape " + slot.hash, image));
			slot.asset = new ClientAsset.ResourceTexture(id, id);
			slot.failures = 0;
		} catch (Throwable t) {
			image.close();
			slot.retryAt = clock.getAsLong() + MAX_RETRY_MS;
			LOGGER.warn("GolemAddons: couldn't upload cape {}", slot.hash, t);
		} finally {
			slot.loading = false;
		}
	}

	/** Client thread, every few seconds: release idle textures and keep the total under the cap. */
	void tick() {
		long now = clock.getAsLong();
		List<Slot> ready = new ArrayList<>();
		for (Slot slot : slots.values()) {
			if (slot.loading) continue;
			if (now - slot.lastUsed > IDLE_RELEASE_MS) {
				release(slot);
			} else if (slot.asset != null) {
				ready.add(slot);
			}
		}
		if (ready.size() > MAX_TEXTURES) {
			ready.sort((a, b) -> Long.compare(a.lastUsed, b.lastUsed));
			for (int i = 0; i < ready.size() - MAX_TEXTURES; i++) release(ready.get(i));
		}
	}

	/** Client thread. Releases everything (feature turned off or API changed). */
	void clear() {
		for (Slot slot : new ArrayList<>(slots.values())) release(slot);
	}

	private void release(Slot slot) {
		slots.remove(slot.hash, slot);
		if (slot.asset != null) {
			slot.asset = null;
			Minecraft.getInstance().getTextureManager().release(textureId(slot.hash));
		}
	}

	static void verify(String hash, byte[] data) throws IOException {
		CapePng.Result png = CapePng.check(data);
		if (!png.valid()) {
			throw new IOException("bad cape image: " + png.problem());
		}
		if (!Hashes.sha256Hex(data).equals(hash)) {
			throw new IOException("cape hash mismatch");
		}
	}

	private static Identifier textureId(String hash) {
		return Identifier.fromNamespaceAndPath("golemaddons", "capes/" + hash);
	}

	private byte[] readDisk(String hash) {
		if (!diskCacheUsable) return null;
		Path file = diskCache.resolve(hash + ".png");
		try {
			if (!Files.isRegularFile(file) || Files.size(file) > CapePng.MAX_BYTES) return null;
			byte[] data = Files.readAllBytes(file);
			verify(hash, data);
			Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
			return data;
		} catch (IOException | RuntimeException e) {
			try {
				Files.deleteIfExists(file);
			} catch (IOException ignored) {
				// unwritable cache; we'll just download
			}
			return null;
		}
	}

	private void writeDisk(String hash, byte[] data) {
		if (!diskCacheUsable) return;
		try {
			Files.createDirectories(diskCache);
			Path tmp = Files.createTempFile(diskCache, hash, ".tmp");
			Files.write(tmp, data);
			Files.move(tmp, diskCache.resolve(hash + ".png"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			trimDisk();
		} catch (IOException | RuntimeException e) {
			diskCacheUsable = false; // keep working from memory only
			LOGGER.info("GolemAddons: cape disk cache disabled: {}", e.toString());
		}
	}

	/** Keeps the disk cache under its size cap, oldest first. Executor thread. */
	private void trimDisk() throws IOException {
		List<Path> files;
		try (Stream<Path> list = Files.list(diskCache)) {
			files = new ArrayList<>(list.filter(p -> p.getFileName().toString().endsWith(".png")).toList());
		}
		long total = 0;
		Map<Path, Long> sizes = new java.util.HashMap<>();
		Map<Path, Long> times = new java.util.HashMap<>();
		for (Path p : files) {
			long size = Files.size(p);
			sizes.put(p, size);
			times.put(p, Files.getLastModifiedTime(p).toMillis());
			total += size;
		}
		if (total <= DISK_CACHE_BYTES) return;
		files.sort((a, b) -> Long.compare(times.get(a), times.get(b)));
		for (Path p : files) {
			if (total <= DISK_CACHE_BYTES * 3 / 4) break;
			Files.deleteIfExists(p);
			total -= sizes.get(p);
		}
	}
}
