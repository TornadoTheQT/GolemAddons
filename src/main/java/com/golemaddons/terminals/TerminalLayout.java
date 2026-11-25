package com.golemaddons.terminals;

/**
 * Where the compact terminal GUI puts each slot: a grid of square cells, centred in the given area, with room for
 * the title above and the status below. Pure, so click mapping is tested exactly.
 */
public record TerminalLayout(TerminalType type, int x, int y, int cell, int gap) {
	public static final int BASE_CELL = 18;
	public static final int PAD = 8;
	public static final int HEADER = 16;
	public static final int FOOTER = 16;

	/** Fits the grid into the area: {@code scale} sets the cell size, shrunk if the panel would not fit. */
	public static TerminalLayout of(TerminalType type, int areaX, int areaY, int areaW, int areaH, float scale, int gap) {
		int columns = type.gridColumns;
		int rows = type.gridRows;
		int g = Math.max(0, gap);
		int cell = Math.max(8, Math.round(BASE_CELL * scale));
		int fitW = (areaW - 2 * PAD - (columns - 1) * g) / columns;
		int fitH = (areaH - HEADER - FOOTER - 2 * PAD - (rows - 1) * g) / rows;
		cell = Math.max(8, Math.min(cell, Math.min(fitW, fitH)));
		int gridW = columns * cell + (columns - 1) * g;
		int gridH = rows * cell + (rows - 1) * g;
		int x = areaX + (areaW - gridW) / 2;
		int y = areaY + (areaH - gridH + HEADER - FOOTER) / 2;
		return new TerminalLayout(type, x, y, cell, g);
	}

	public int gridWidth() {
		return type.gridColumns * cell + (type.gridColumns - 1) * gap;
	}

	public int gridHeight() {
		return type.gridRows * cell + (type.gridRows - 1) * gap;
	}

	/** The panel around the grid, title and status. */
	public int panelX() {
		return x - PAD;
	}

	public int panelY() {
		return y - PAD - HEADER;
	}

	public int panelW() {
		return gridWidth() + 2 * PAD;
	}

	public int panelH() {
		return gridHeight() + 2 * PAD + HEADER + FOOTER;
	}

	public boolean shows(int slot) {
		int row = slot / 9;
		int column = slot % 9;
		return slot >= 0 && row >= type.gridRow && row < type.gridRow + type.gridRows
				&& column >= type.gridColumn && column < type.gridColumn + type.gridColumns;
	}

	public int cellX(int slot) {
		return x + (slot % 9 - type.gridColumn) * (cell + gap);
	}

	public int cellY(int slot) {
		return y + (slot / 9 - type.gridRow) * (cell + gap);
	}

	/** The chest slot under the point, or -1 for the gaps between cells and anywhere outside the grid. */
	public int slotAt(double mx, double my) {
		if (mx < x || my < y) return -1;
		int column = (int) Math.floor((mx - x) / (cell + gap));
		int row = (int) Math.floor((my - y) / (cell + gap));
		if (column >= type.gridColumns || row >= type.gridRows) return -1;
		if (mx - x - column * (cell + gap) >= cell || my - y - row * (cell + gap) >= cell) return -1;
		return (row + type.gridRow) * 9 + column + type.gridColumn;
	}
}
