package com.golemaddons.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

class CapePngTest {
	/** Signature + a valid IHDR + an IEND; enough for the header checks. */
	static byte[] png(int width, int height, int depth, int colorType) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
		ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height)
				.put((byte) depth).put((byte) colorType).put((byte) 0).put((byte) 0).put((byte) 0);
		chunk(out, "IHDR", ihdr.array());
		chunk(out, "IEND", new byte[0]);
		return out.toByteArray();
	}

	private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
		out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		out.writeBytes(typeBytes);
		out.writeBytes(data);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
	}

	@Test
	void acceptsVanillaAndHdSizes() {
		for (int w : new int[] {64, 128, 256, 512}) {
			CapePng.Result r = CapePng.check(png(w, w / 2, 8, 6));
			assertTrue(r.valid(), "width " + w);
			assertEquals(w, r.width());
		}
	}

	@Test
	void rejectsOtherSizesIncludingBombs() {
		assertEquals(CapePng.Problem.BAD_SIZE, CapePng.check(png(64, 64, 8, 6)).problem());
		assertEquals(CapePng.Problem.BAD_SIZE, CapePng.check(png(1024, 512, 8, 6)).problem());
		assertEquals(CapePng.Problem.BAD_SIZE, CapePng.check(png(100000, 50000, 8, 6)).problem(), "huge declared size");
		assertEquals(CapePng.Problem.BAD_SIZE, CapePng.check(png(-64, -32, 8, 6)).problem());
	}

	@Test
	void rejectsNonPngAndBrokenHeaders() {
		assertEquals(CapePng.Problem.NOT_PNG, CapePng.check("GIF89a............................".getBytes(StandardCharsets.US_ASCII)).problem());
		assertEquals(CapePng.Problem.NOT_PNG, CapePng.check(new byte[10]).problem());
		byte[] bad = png(64, 32, 8, 6);
		bad[20] ^= 1; // corrupt the height without fixing the CRC
		assertEquals(CapePng.Problem.BAD_HEADER, CapePng.check(bad).problem());
		assertEquals(CapePng.Problem.BAD_HEADER, CapePng.check(png(64, 32, 3, 6)).problem(), "invalid depth for RGBA");
		assertEquals(CapePng.Problem.NOT_PNG, CapePng.check(Arrays.copyOf(png(64, 32, 8, 6), 20)).problem(), "truncated");
	}

	@Test
	void rejectsOversizedFiles() {
		byte[] big = Arrays.copyOf(png(64, 32, 8, 6), CapePng.MAX_BYTES + 1);
		assertEquals(CapePng.Problem.TOO_LARGE, CapePng.check(big).problem());
	}
}
