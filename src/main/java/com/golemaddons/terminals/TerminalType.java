package com.golemaddons.terminals;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The six Floor 7 terminals, recognised by their chest title. Each knows its chest size and the block of slots the
 * puzzle uses, which is all the compact GUI shows.
 */
public enum TerminalType {
	/** Ten numbered red panes (two rows of five), clicked from 1 upwards; a clicked pane turns lime. */
	ORDER("golemaddons.terminals.type.order", 4, 1, 2, 2, 5),
	/** Red and lime panes; every red one must be clicked green. */
	PANES("golemaddons.terminals.type.panes", 5, 1, 3, 1, 7),
	/** "What starts with: 'X'?" - pick every item whose name starts with the letter. */
	STARTS_WITH("golemaddons.terminals.type.starts_with", 5, 1, 3, 1, 7),
	/** "Select all the RED items!" - pick every item of that colour. */
	COLORS("golemaddons.terminals.type.colors", 6, 1, 4, 1, 7),
	/** Nine panes that cycle red, orange, yellow, green, blue; make them all one colour. */
	RUBIX("golemaddons.terminals.type.rubix", 5, 1, 3, 3, 3),
	/**
	 * A pane sweeps along one of three tracks; click the row's button when it lines up with the marker. Rows 0-4:
	 * the markers above and below the tracks, so the GUI has one row of padding on each side.
	 */
	MELODY("golemaddons.terminals.type.melody", 6, 0, 5, 0, 9);

	private static final Pattern STARTS = Pattern.compile("^What starts with: '([A-Za-z0-9])'\\?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern COLOR = Pattern.compile("^Select all the ([A-Za-z ]+?) items!?$", Pattern.CASE_INSENSITIVE);

	public final String key;
	/** Chest rows; the chest has {@code rows * 9} slots. */
	public final int rows;
	/** The block of slots the compact GUI draws: first row, row count, first column, column count. */
	public final int gridRow;
	public final int gridRows;
	public final int gridColumn;
	public final int gridColumns;

	TerminalType(String key, int rows, int gridRow, int gridRows, int gridColumn, int gridColumns) {
		this.key = key;
		this.rows = rows;
		this.gridRow = gridRow;
		this.gridRows = gridRows;
		this.gridColumn = gridColumn;
		this.gridColumns = gridColumns;
	}

	public int slots() {
		return rows * 9;
	}

	/** The terminal a chest title belongs to, or null for any other chest. */
	public static TerminalType of(String rawTitle) {
		String title = plain(rawTitle);
		if (title.equalsIgnoreCase("Click in order!")) return ORDER;
		if (title.equalsIgnoreCase("Correct all the panes!")) return PANES;
		if (STARTS.matcher(title).matches()) return STARTS_WITH;
		if (COLOR.matcher(title).matches()) return COLORS;
		if (title.equalsIgnoreCase("Change all to same color!")) return RUBIX;
		if (title.equalsIgnoreCase("Click the button on time!")) return MELODY;
		return null;
	}

	/** The letter a Starts With title asks for, lower case, or null. */
	static String letter(String rawTitle) {
		Matcher m = STARTS.matcher(plain(rawTitle));
		return m.matches() ? m.group(1).toLowerCase(Locale.ROOT) : null;
	}

	/** The colour a Select All title asks for, lower case with single spaces ("light blue"), or null. */
	static String color(String rawTitle) {
		Matcher m = COLOR.matcher(plain(rawTitle));
		return m.matches() ? m.group(1).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT) : null;
	}

	/** Strips formatting codes and normalises curly quotes, which servers use in the Starts With title. */
	static String plain(String raw) {
		if (raw == null) return "";
		StringBuilder out = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '§') {
				i++;
				continue;
			}
			out.append(c == '‘' || c == '’' ? '\'' : c);
		}
		return out.toString().trim();
	}
}
