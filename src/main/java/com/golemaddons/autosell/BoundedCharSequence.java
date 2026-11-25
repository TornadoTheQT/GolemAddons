package com.golemaddons.autosell;

/**
 * Wraps the text a regex runs against and throws once the regex engine has read too many characters.
 * Java's regex engine has no timeout, so this is what stops a catastrophic pattern such as {@code (a+)+$}
 * from freezing the client.
 */
final class BoundedCharSequence implements CharSequence {
	static final class BudgetExceededException extends RuntimeException {
		BudgetExceededException() {
			super("Regex step budget exceeded", null, false, false);
		}
	}

	private final String text;
	private final int[] remaining;

	BoundedCharSequence(String text, int budget) {
		this(text, new int[] {budget});
	}

	private BoundedCharSequence(String text, int[] remaining) {
		this.text = text;
		this.remaining = remaining;
	}

	@Override
	public char charAt(int index) {
		if (--remaining[0] < 0) {
			throw new BudgetExceededException();
		}
		return text.charAt(index);
	}

	@Override
	public int length() {
		return text.length();
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		// Shares the same budget so the engine can't escape the limit through sub-sequences.
		return new BoundedCharSequence(text.substring(start, end), remaining);
	}

	@Override
	public String toString() {
		return text;
	}
}
