package com.golemaddons.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ReleaseNotesTest {
	@Test
	void markdownBecomesPlainStyledText() {
		List<Component> lines = ReleaseNotes.lines("Mod for **Minecraft 26.1.2**.\r\n\r\n\r\n### New\r\n- **Terminals (QoL):** `/gatestterms` and [the README](https://x.y/z)\r\n");
		assertEquals(List.of("Mod for Minecraft 26.1.2.", "", "New", "  • Terminals (QoL): /gatestterms and the README"),
				lines.stream().map(Component::getString).toList());
		assertEquals(Boolean.TRUE, lines.get(2).getStyle().isBold());
	}

	@Test
	void notesNeverCarryClickOrHoverEvents() {
		for (Component line : ReleaseNotes.lines("[click me](https://evil.example) **[x](y)**")) {
			line.visit((style, text) -> {
				assertNull(style.getClickEvent());
				assertNull(style.getHoverEvent());
				return java.util.Optional.empty();
			}, net.minecraft.network.chat.Style.EMPTY);
		}
	}
}
