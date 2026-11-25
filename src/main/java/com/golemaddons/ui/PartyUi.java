package com.golemaddons.ui;

import com.golemaddons.partyfinder.PartyModels.Party;
import com.golemaddons.partyfinder.PartyModels.Role;
import com.golemaddons.partyfinder.PartyModels.Status;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

/** Small pieces shared by the party finder screens. */
final class PartyUi {
	private PartyUi() {
	}

	/**
	 * A player's face: their real skin when they're in your tab list (almost always, since parties are per server),
	 * otherwise the default skin for their UUID. Never downloads anything.
	 */
	static void head(GuiGraphicsExtractor graphics, UUID uuid, int x, int y, int size) {
		ClientPacketListener net = Minecraft.getInstance().getConnection();
		PlayerInfo info = net == null || uuid == null ? null : net.getPlayerInfo(uuid);
		PlayerSkin skin = info != null ? info.getSkin() : uuid != null ? DefaultPlayerSkin.get(uuid) : DefaultPlayerSkin.getDefaultSkin();
		PlayerFaceExtractor.extractRenderState(graphics, skin, x, y, size);
	}

	static Component status(Party p) {
		return Component.translatable("golemaddons.pf.status." + p.status().id).withColor(statusColor(p.status()) & 0xFFFFFF);
	}

	static int statusColor(Status s) {
		return switch (s) {
			case OPEN -> UiText.GREEN;
			case FULL -> UiText.YELLOW;
			case IN_PROGRESS -> UiText.GOLD;
		};
	}

	static Component role(Role r) {
		return r == null ? Component.translatable("golemaddons.pf.role.any")
				: Component.translatable(r.translationKey()).withColor(r.color & 0xFFFFFF);
	}

	/** "3m ago" style. */
	static Component ago(long createdAt) {
		long s = Math.max(0, (System.currentTimeMillis() - createdAt) / 1000);
		if (s < 60) return Component.translatable("golemaddons.pf.ago.now");
		if (s < 3600) return Component.translatable("golemaddons.pf.ago.minutes", s / 60);
		return Component.translatable("golemaddons.pf.ago.hours", s / 3600);
	}

	/** Cuts {@code text} to {@code width}, ending with "…" when cut. */
	static String fit(Font font, String text, int width) {
		if (font.width(text) <= width) return text;
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
	}

	/** A small toggle pill used for tabs and filters. Works with the mouse, Tab and Enter. */
	static final class Chip extends AbstractButton {
		private final int color;
		private final BooleanSupplier selected;
		private final Runnable action;

		Chip(int x, int y, int w, int h, Component label, int color, BooleanSupplier selected, Runnable action) {
			super(x, y, w, h, label);
			this.color = color;
			this.selected = selected;
			this.action = action;
		}

		static int widthFor(Font font, Component label) {
			return font.width(label) + 12;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			action.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
			Font font = Minecraft.getInstance().font;
			boolean on = selected.getAsBoolean();
			int x = getX();
			int y = getY();
			int w = getWidth();
			int h = getHeight();
			int bg = on ? UiText.withAlpha(color, 0.30f) : isHoveredOrFocused() ? 0x40FFFFFF : 0x60000000;
			g.fill(x, y, x + w, y + h, bg);
			g.outline(x, y, w, h, on ? color : isHoveredOrFocused() ? 0xA0FFFFFF : 0x40FFFFFF);
			if (on) g.fill(x, y + h - 2, x + w, y + h, color);
			int text = !active ? 0xFF606060 : on ? UiText.WHITE : UiText.GRAY;
			g.centeredText(font, getMessage(), x + w / 2, y + (h - 8) / 2, text);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
