package com.golemaddons.ui;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * A second-level hub (Selling, QoL, Visuals): the same look as the main menu, with one tile per feature in the
 * category. Back and Escape return to the main menu.
 */
final class CategoryHubScreen extends Screen {
	private static final float TITLE_SCALE = 2.5f;
	private static final float TITLE_Y = 40f;
	private static final float CARD_Y = 104f;
	private static final float CARD_W = 128f;
	private static final float CARD_H = 172f;
	private static final float CARD_GAP = 10f;

	/**
	 * One tile. {@code open} builds the screen to show (given this hub as its parent), or returns null when the tile
	 * acts in place (like a toggle) and the hub should just stay open.
	 */
	record Tile(String key, ItemStack icon, int accent, Supplier<HubCard.Status> status, Function<Screen, Screen> open) {
	}

	private final Screen parent;
	private final int accent;
	private final List<Tile> tiles;
	private final long openedAt = Util.getMillis();
	private final HubBackdrop backdrop = new HubBackdrop();
	private HubCanvas canvas;

	CategoryHubScreen(Screen parent, Component title, int accent, List<Tile> tiles) {
		super(title);
		this.parent = parent;
		this.accent = accent;
		this.tiles = tiles;
	}

	@Override
	protected void init() {
		canvas = HubCanvas.of(minecraft, width, height);
		float x = (HubCanvas.WIDTH - (tiles.size() * CARD_W + (tiles.size() - 1) * CARD_GAP)) / 2f;
		for (int i = 0; i < tiles.size(); i++) {
			Tile tile = tiles.get(i);
			addRenderableWidget(new HubCard(canvas, x, CARD_Y, CARD_W, CARD_H, Component.translatable(tile.key()),
					Component.translatable(tile.key() + ".desc"), tile.icon(), tile.accent(), i, openedAt, tile.status(), () -> {
						Screen next = tile.open().apply(this);
						if (next != null) minecraft.setScreen(next);
					}));
			x += CARD_W + CARD_GAP;
		}
		addRenderableWidget(HubCard.button(canvas, (HubCanvas.WIDTH - 140) / 2f, 306f, 140f, 24f,
				Component.translatable("gui.back"), UiText.ACCENT, tiles.size(), openedAt, this::onClose));
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	private float elapsed() {
		return (Util.getMillis() - openedAt) / 1000f;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		backdrop.extract(graphics, width, height, canvas.scale(), elapsed());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		float t = elapsed();
		graphics.pose().pushMatrix();
		graphics.pose().translate(canvas.originX(), canvas.originY());
		graphics.pose().scale(canvas.scale(), canvas.scale());
		float cx = HubCanvas.WIDTH / 2f;
		float intro = Math.clamp(t / 0.35f, 0f, 1f);

		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, TITLE_Y + (1f - intro) * -8f);
		graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
		graphics.centeredText(font, title, 0, 0, UiText.withAlpha(UiText.lerpColor(0xFFEDE9FF, accent, 0.35f), intro));
		graphics.pose().popMatrix();

		float line = 1f - (1f - Math.clamp((t - 0.15f) / 0.4f, 0f, 1f)) * (1f - Math.clamp((t - 0.15f) / 0.4f, 0f, 1f));
		int half = Math.round(line * (font.width(title) * TITLE_SCALE / 2f + 12f));
		int lineY = Math.round(TITLE_Y + 9 * TITLE_SCALE + 6);
		int icx = Math.round(cx);
		graphics.fill(icx - half, lineY, icx + half, lineY + 2, UiText.withAlpha(accent, 0.9f * line));
		graphics.pose().popMatrix();
	}
}
