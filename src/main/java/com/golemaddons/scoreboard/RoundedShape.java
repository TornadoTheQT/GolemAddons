package com.golemaddons.scoreboard;

/**
 * A rounded rectangle cut into vertical columns: 1 px wide across the corners (so the curve is smooth) and wider
 * strips in between. Painting column by column gives rounded corners with plain fills. Pure; cached per size.
 */
public final class RoundedShape {
	public final int w;
	public final int h;
	public final int r;
	/** Column i spans x0[i]..x1[i] (exclusive) and top[i]..bottom[i] (exclusive). */
	public final int[] x0;
	public final int[] x1;
	public final int[] top;
	public final int[] bottom;
	private final int strip;

	private RoundedShape(int w, int h, int r, int strip) {
		this.w = w;
		this.h = h;
		this.r = r;
		this.strip = strip;
		int middle = Math.max(0, w - 2 * r);
		int strips = middle == 0 ? 0 : Math.max(1, (middle + strip - 1) / strip);
		int n = 2 * r + strips;
		x0 = new int[n];
		x1 = new int[n];
		top = new int[n];
		bottom = new int[n];
		int i = 0;
		for (int x = 0; x < r; x++, i++) set(i, x, x + 1, inset(x));
		for (int s = 0; s < strips; s++, i++) {
			int a = r + middle * s / strips;
			int b = r + middle * (s + 1) / strips;
			set(i, a, b, 0);
		}
		for (int x = w - r; x < w; x++, i++) set(i, x, x + 1, inset(w - 1 - x));
	}

	private void set(int i, int a, int b, int inset) {
		x0[i] = a;
		x1[i] = b;
		top[i] = inset;
		bottom[i] = h - inset;
	}

	/** How far the curve pulls column {@code x} (counted from the nearest side) in from the top and bottom. */
	private int inset(int x) {
		double dx = r - x - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0, (double) r * r - dx * dx)));
	}

	public int columns() {
		return x0.length;
	}

	/** Whether the w x h box at (x, y) lies fully inside the shape. */
	public boolean contains(float x, float y, float bw, float bh) {
		if (x < 0 || y < 0 || x + bw > w || y + bh > h) return false;
		if (r == 0) return true;
		return inCorner(x, y) && inCorner(x + bw, y) && inCorner(x, y + bh) && inCorner(x + bw, y + bh);
	}

	private boolean inCorner(float x, float y) {
		float cx = x < r ? r : x > w - r ? w - r : x;
		float cy = y < r ? r : y > h - r ? h - r : y;
		float dx = x - cx;
		float dy = y - cy;
		return dx * dx + dy * dy <= (float) r * r + 0.5f;
	}

	private static RoundedShape cached;

	/** The shape for this size (the last one is kept, since a board rarely changes size). */
	public static RoundedShape of(int w, int h, int radius, int strip) {
		int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		RoundedShape c = cached;
		if (c != null && c.w == w && c.h == h && c.r == r && c.strip == strip) return c;
		RoundedShape s = new RoundedShape(w, h, r, strip);
		cached = s;
		return s;
	}
}
