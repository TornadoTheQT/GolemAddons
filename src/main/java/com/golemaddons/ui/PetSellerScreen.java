package com.golemaddons.ui;

import com.golemaddons.autosell.InventoryScan;
import com.golemaddons.autosell.PetRarity;
import com.golemaddons.autosell.PetSelling;
import com.golemaddons.config.GolemConfig;
import java.util.EnumSet;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Pet seller: pick the rarities to sell (by the colour of the pet's name) and the highest level to sell. */
final class PetSellerScreen extends EditorScreen {
	private static final int ROW = 20;
	private int infoY;
	private int infoX;

	PetSellerScreen(Screen parent) {
		super(Component.translatable("golemaddons.pets.title"), parent);
	}

	@Override
	protected void build() {
		GolemConfig.PetSeller p = working.petSeller;
		int panelW = maxContentWidth(420);
		int inner = panelW - 2 * PANEL_PAD - 2;
		int half = (inner - GAP * 2) / 2;
		int px = (width - panelW) / 2;
		int ix = px + PANEL_PAD + 2;
		int rx = ix + inner - half;
		int panelH = PANEL_LABEL + 2 + (ROW + 4) + 3 * (ROW + 4) + 4 + GolemSlider.HEIGHT + 3 + 12 + PANEL_PAD;
		int y = centeredTop(panelH + 12);
		panels.add(new Panel(px, y, panelW, panelH, Component.translatable("golemaddons.pets.panel")));
		int rowY = y + PANEL_LABEL + 2;
		addRenderableWidget(GolemCycle.onOffBuilder(p.enabled)
				.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.pets.enabled.tooltip")))
				.create(ix, rowY, inner, ROW, Component.translatable("golemaddons.pets.enabled"), (b, v) -> p.enabled = v));
		rowY += ROW + 4;
		PetRarity[] all = PetRarity.values();
		for (int i = 0; i < all.length; i++) {
			PetRarity r = all[i];
			int x = i % 2 == 0 ? ix : rx;
			addRenderableWidget(GolemCycle.onOffBuilder(p.sells(r))
					.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.pets.rarity.tooltip", Component.translatable(r.key()))))
					.create(x, rowY, half, ROW, Component.translatable(r.key()).withColor(r.color), (b, v) -> p.set(r, v)));
			if (i % 2 == 1) rowY += ROW + 4;
		}
		rowY += 4;
		GolemSlider level = addRenderableWidget(new GolemSlider(ix, rowY, inner, Component.translatable("golemaddons.pets.max_level"), 1,
				GolemConfig.PetSeller.MAX_LEVEL, 1, 100, () -> p.maxLevel, v -> p.maxLevel = (int) v,
				v -> String.format(Locale.ROOT, "Lvl %.0f", v), UiText.GOLD));
		level.setTooltip(Tooltip.create(Component.translatable("golemaddons.pets.max_level.tooltip")));
		rowY += GolemSlider.HEIGHT + 3;
		infoX = ix;
		infoY = rowY;
		addFooter(saveButton(), cancelButton());
	}

	/** Pets in your inventory (outside the hotbar if it is protected) that the current picks would sell. */
	private int wouldSell() {
		if (minecraft.player == null) return -1;
		GolemConfig.PetSeller p = working.petSeller;
		if (!p.enabled) return 0;
		EnumSet<PetRarity> set = EnumSet.noneOf(PetRarity.class);
		for (PetRarity r : PetRarity.values()) if (p.sells(r)) set.add(r);
		PetSelling pets = PetSelling.of(set, p.maxLevel);
		Inventory inv = minecraft.player.getInventory();
		int count = 0;
		int from = working.selling.protectHotbar ? Inventory.getSelectionSize() : 0;
		for (int i = from; i < Inventory.INVENTORY_SIZE; i++) {
			if (i == inv.getSelectedSlot()) continue;
			if (pets.matches(inv.getItem(i))) count++;
		}
		return count;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int n = wouldSell();
		Component info = n < 0 ? Component.translatable("golemaddons.pets.info.no_world")
				: Component.translatable("golemaddons.pets.info", n);
		graphics.text(font, info, infoX, infoY, n > 0 ? UiText.GOLD : UiText.GRAY);
	}
}
