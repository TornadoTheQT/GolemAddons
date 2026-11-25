package com.golemaddons.autosell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuickSellCommandTest {
	@Test
	void normalizesSlashAndWhitespace() {
		assertEquals("shop", QuickSellCommand.normalize("shop"));
		assertEquals("shop", QuickSellCommand.normalize(" /shop "));
		assertEquals("shop sell", QuickSellCommand.normalize("/shop sell"));
		assertEquals("npc:shop", QuickSellCommand.normalize("npc:shop"));
	}

	@Test
	void blankMeansOff() {
		assertEquals("", QuickSellCommand.normalize(""));
		assertEquals("", QuickSellCommand.normalize("   "));
		assertEquals("", QuickSellCommand.normalize("/"));
		assertEquals("", QuickSellCommand.normalize(null));
	}

	@ParameterizedTest
	@ValueSource(strings = {"shop\nsay hi", "shop\u0000", "//shop", "shop;say hi", "shop sell§c", "é", "shop\tsell"})
	void unsafeOrOddCommandsAreRejected(String raw) {
		assertNull(QuickSellCommand.normalize(raw), raw);
	}

	@Test
	void tooLongIsRejected() {
		assertNull(QuickSellCommand.normalize("a".repeat(QuickSellCommand.MAX_LENGTH + 1)));
	}
}
