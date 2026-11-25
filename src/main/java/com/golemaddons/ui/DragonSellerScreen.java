package com.golemaddons.ui;

import com.golemaddons.autosell.InventoryScan;
import com.golemaddons.autosell.ItemMatcher;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.dragon.DragonCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Pick which dragons, pieces and extra drops the Dragon Seller sells, as checklists: colored name on the left,
 * checkbox on the right. Each section uses as many columns as fit, filled top to bottom, inside centered panels.
 * Also holds the dragon hitbox toggle.
 */
final class DragonSellerScreen extends EditorScreen {
	private static final int ROW = 16;
	/** Row height used when the normal one would push the list into the footer (small window or big GUI scale). */
	private static final int COMPACT_ROW = 12;
	private static final int TOP_ROW = 20;
	private static final int PREVIEW_REFRESH_TICKS = 10;

	private record Item(String label, int rgb, boolean value, Component tooltip, Consumer<Boolean> onChange) {
	}

	private record Section(Component label, List<Item> items) {
	}

	/** Measured layout of one section. */
	private record Grid(int rowWidth, int cols, int rows) {
		int width() {
			return cols * rowWidth + (cols - 1) * GAP;
		}
	}

	private final List<CheckRow> rows = new ArrayList<>();
	/** Rebuilt only when a checkbox changes, so the periodic refresh just rescans the inventory. */
	private ItemMatcher previewMatcher = ItemMatcher.EMPTY;
	private int itemTypes;
	private int stacksNow;
	private int ticks;
	private int hintY;
	private int summaryY;

	DragonSellerScreen(Screen parent) {
		super(Component.translatable("golemaddons.dragon.title"), parent);
	}

	private GolemConfig.DragonSeller settings() {
		return working.dragonSeller;
	}

	private List<Section> sections() {
		GolemConfig.DragonSeller s = settings();
		List<Item> dragons = new ArrayList<>();
		for (DragonCatalog.DragonType d : DragonCatalog.DragonType.values()) {
			dragons.add(new Item(d.displayName, d.color, s.sellsDragon(d),
					Component.translatable("golemaddons.dragon.tooltip.dragon", d.displayName), v -> s.setDragon(d, v)));
		}
		List<Item> pieces = new ArrayList<>();
		for (DragonCatalog.Piece p : DragonCatalog.Piece.values()) {
			pieces.add(new Item(p.displayName, UiText.ACCENT_2, s.sellsPiece(p),
					Component.translatable("golemaddons.dragon.tooltip.piece", p.displayName), v -> s.setPiece(p, v)));
		}
		List<Item> extras = new ArrayList<>();
		for (DragonCatalog.Extra e : DragonCatalog.Extra.values()) {
			extras.add(new Item(e.itemName, UiText.ACCENT, s.sellsExtra(e),
					Component.translatable("golemaddons.dragon.tooltip.extra", e.itemName), v -> s.setExtra(e, v)));
		}
		return List.of(
				new Section(Component.translatable("golemaddons.dragon.section.dragons"), dragons),
				new Section(Component.translatable("golemaddons.dragon.section.pieces"), pieces),
				new Section(Component.translatable("golemaddons.dragon.section.extras"), extras));
	}

	/** Columns filled top to bottom; as many as fit, then balanced so the last column isn't nearly empty. */
	private Grid measure(Section section, int maxInner) {
		int rowWidth = 0;
		for (Item item : section.items()) {
			rowWidth = Math.max(rowWidth, CheckRow.widthFor(font, item.label()));
		}
		int n = section.items().size();
		int cols = Math.clamp((maxInner + GAP) / (rowWidth + GAP), 1, n);
		int rowsPerCol = (n + cols - 1) / cols;
		cols = (n + rowsPerCol - 1) / rowsPerCol;
		return new Grid(rowWidth, cols, rowsPerCol);
	}

	private static int blockHeight(List<Grid> grids, int rowHeight) {
		int block = TOP_ROW + 4 + 12;
		for (Grid grid : grids) block += PANEL_LABEL + grid.rows() * rowHeight + PANEL_PAD + PANEL_GAP;
		return block + 10;
	}

	@Override
	protected void build() {
		rows.clear();
		GolemConfig.DragonSeller s = settings();
		List<Section> sections = sections();
		int maxInner = maxContentWidth(560) - 2 * PANEL_PAD;

		List<Grid> grids = new ArrayList<>();
		int innerWidth = Math.min(maxInner, 300);
		for (Section section : sections) {
			Grid grid = measure(section, maxInner);
			grids.add(grid);
			innerWidth = Math.max(innerWidth, grid.width());
		}
		int panelW = innerWidth + 2 * PANEL_PAD;
		int available = height - HEADER_HEIGHT - FOOTER_HEIGHT - 12;
		int rowHeight = blockHeight(grids, ROW) <= available ? ROW : COMPACT_ROW;

		int px = (width - panelW) / 2;
		int y = centeredTop(blockHeight(grids, rowHeight));

		// Top row: the seller's master switch and the hitbox switch, side by side.
		int half = (panelW - GAP * 2) / 2;
		addRenderableWidget(CycleButton.onOffBuilder(s.enabled)
				.create(px, y, half, TOP_ROW, Component.translatable("golemaddons.dragon.enabled"), (b, value) -> {
					s.enabled = value;
					rebuildWidgets();
				}));
		addRenderableWidget(CycleButton.onOffBuilder(working.render.dragonHitboxes)
				.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.hitboxes.tooltip")))
				.create(px + panelW - half, y, half, TOP_ROW, Component.translatable("golemaddons.hitboxes.label"),
						(b, value) -> working.render.dragonHitboxes = value));
		y += TOP_ROW + 4;
		hintY = y;
		y += 12;

		for (int i = 0; i < sections.size(); i++) {
			Section section = sections.get(i);
			Grid grid = grids.get(i);
			int panelH = PANEL_LABEL + grid.rows() * rowHeight + PANEL_PAD;
			panels.add(new Panel(px, y, panelW, panelH, section.label()));
			int gx = px + PANEL_PAD + (innerWidth - grid.width()) / 2;
			int gy = y + PANEL_LABEL + 2;
			List<Item> items = section.items();
			for (int t = 0; t < items.size(); t++) {
				int col = t / grid.rows();
				int row = t % grid.rows();
				addRow(items.get(t), gx + col * (grid.rowWidth() + GAP), gy + row * rowHeight, grid.rowWidth(), rowHeight);
			}
			y += panelH + PANEL_GAP;
		}
		summaryY = y;

		for (CheckRow row : rows) {
			row.active = s.enabled;
		}

		addFooter(
				Button.builder(Component.translatable("golemaddons.dragon.reset"), b -> {
					s.resetToDefaults();
					rebuildWidgets();
				}).tooltip(Tooltip.create(Component.translatable("golemaddons.dragon.reset.tooltip"))),
				saveButton(),
				cancelButton());

		settingsChanged();
	}

	private void addRow(Item item, int x, int y, int w, int h) {
		CheckRow row = new CheckRow(x, y, w, h, item.label(), item.rgb(), item.value(), v -> {
			item.onChange().accept(v);
			settingsChanged();
		});
		row.setTooltip(Tooltip.create(item.tooltip()));
		rows.add(addRenderableWidget(row));
	}

	private void settingsChanged() {
		List<String> names = DragonCatalog.itemNames(settings());
		itemTypes = names.size();
		previewMatcher = names.isEmpty() ? ItemMatcher.EMPTY : ItemMatcher.compileEnabled(DragonCatalog.rulesFor(settings()));
		refreshPreview();
	}

	private void refreshPreview() {
		stacksNow = 0;
		if (minecraft == null || minecraft.player == null || previewMatcher.isEmpty()) {
			return;
		}
		Inventory inventory = minecraft.player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			boolean isProtected = i == inventory.getSelectedSlot() || (working.selling.protectHotbar && i < Inventory.getSelectionSize());
			if (!isProtected && InventoryScan.stackMatches(previewMatcher, inventory.getItem(i))) {
				stacksNow++;
			}
		}
	}

	@Override
	public void tick() {
		if (++ticks % PREVIEW_REFRESH_TICKS == 0) {
			refreshPreview();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		boolean on = settings().enabled;
		graphics.centeredText(font, Component.translatable(on ? "golemaddons.dragon.hint" : "golemaddons.dragon.preview.off"),
				width / 2, hintY, on ? UiText.GRAY : UiText.YELLOW);
		if (on) {
			graphics.centeredText(font, Component.translatable("golemaddons.dragon.preview", itemTypes, stacksNow),
					width / 2, summaryY, stacksNow > 0 ? UiText.GREEN : UiText.GRAY);
		}
	}
}
