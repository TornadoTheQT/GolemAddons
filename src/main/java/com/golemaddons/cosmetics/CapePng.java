package com.golemaddons.cosmetics;

import java.util.zip.CRC32;

/**
 * Checks a cape PNG's header before anything decodes it. Bounding the size in IHDR bounds the decoded image, which
 * is the defence against decompression bombs. Plain Java, unit tested; the Worker has the same rules.
 */
public final class CapePng {
	public static final int MAX_BYTES = 256 * 1024;
	private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

	public enum Problem { TOO_LARGE, NOT_PNG, BAD_HEADER, BAD_SIZE }

	public record Result(int width, int height, Problem problem) {
		public boolean valid() {
			return problem == null;
		}
	}

	private CapePng() {
	}

	/** Allowed sizes: 2:1, 64x32 up to 512x256 in powers of two (the vanilla cape layout, optionally HD). */
	public static boolean allowedSize(int width, int height) {
		return height * 2 == width && (width == 64 || width == 128 || width == 256 || width == 512);
	}

	public static Result check(byte[] data) {
		if (data == null || data.length < 8 + 25) {
			return fail(Problem.NOT_PNG);
		}
		if (data.length > MAX_BYTES) {
			return fail(Problem.TOO_LARGE);
		}
		for (int i = 0; i < SIGNATURE.length; i++) {
			if (data[i] != SIGNATURE[i]) return fail(Problem.NOT_PNG);
		}
		// First chunk must be IHDR: length 13, type, 13 data bytes, CRC over type + data.
		if (readInt(data, 8) != 13 || data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') {
			return fail(Problem.BAD_HEADER);
		}
		CRC32 crc = new CRC32();
		crc.update(data, 12, 17);
		if ((int) crc.getValue() != readInt(data, 29)) {
			return fail(Problem.BAD_HEADER);
		}
		int width = readInt(data, 16);
		int height = readInt(data, 20);
		int bitDepth = data[24] & 0xFF;
		int colorType = data[25] & 0xFF;
		int compression = data[26] & 0xFF;
		int filter = data[27] & 0xFF;
		int interlace = data[28] & 0xFF;
		if (!validDepth(colorType, bitDepth) || compression != 0 || filter != 0 || interlace > 1) {
			return fail(Problem.BAD_HEADER);
		}
		if (!allowedSize(width, height)) {
			return new Result(width, height, Problem.BAD_SIZE);
		}
		return new Result(width, height, null);
	}

	/** Bit depths allowed for each color type, per the PNG spec. */
	private static boolean validDepth(int colorType, int depth) {
		return switch (colorType) {
			case 0 -> depth == 1 || depth == 2 || depth == 4 || depth == 8 || depth == 16;
			case 3 -> depth == 1 || depth == 2 || depth == 4 || depth == 8;
			case 2, 4, 6 -> depth == 8 || depth == 16;
			default -> false;
		};
	}

	private static int readInt(byte[] data, int offset) {
		return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16) | ((data[offset + 2] & 0xFF) << 8)
				| (data[offset + 3] & 0xFF);
	}

	private static Result fail(Problem problem) {
		return new Result(0, 0, problem);
	}
}
