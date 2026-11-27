package com.golemaddons.chat;

import com.golemaddons.config.ConfigManager;
import com.golemaddons.config.GolemConfig;
import java.util.List;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

/**
 * Hides the slayer messages that repeat every fight. Only chat lines are hidden (never the action bar), and other
 * features still see hidden lines: the boss helpers listen to cancelled messages too.
 */
public final class ChatCleanerFeature {
	private static volatile GolemConfig.ChatCleaner settings = new GolemConfig.ChatCleaner();
	private static volatile List<String> custom = List.of();

	private ChatCleanerFeature() {
	}

	public static void register(ConfigManager manager) {
		manager.addListener(c -> {
			settings = c.chatCleaner.copy();
			custom = SlayerChatFilter.customFilters(c.chatCleaner.customFilters);
		});
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (overlay) return true;
			try {
				return !shouldHide(message.getString(), settings, custom);
			} catch (RuntimeException e) {
				return true; // never lose a message because of a filter problem
			}
		});
	}

	static boolean shouldHide(String text, GolemConfig.ChatCleaner s, List<String> customFilters) {
		if (!s.enabled || text == null) return false;
		String plain = com.golemaddons.boss.Sidebar.clean(text);
		SlayerChatFilter.Category c = SlayerChatFilter.match(plain);
		if (c != null && s.hides(c)) return true;
		return SlayerChatFilter.matchesCustom(plain, customFilters);
	}
}
