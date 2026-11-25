package com.golemaddons.ui;

import com.golemaddons.autosell.InventoryScan;
import com.golemaddons.autosell.ItemMatcher;
import com.golemaddons.autosell.RuleValidator;
import com.golemaddons.autosell.SellRule;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Add/edit one rule, with live validation and a preview of every stack in the player's inventory it would match.
 * The preview is the main protection against a regex that matches more than intended.
 */
final class RuleEditScreen extends Screen {
	private static final int CONTENT_WIDTH = 310;
	private static final int PREVIEW_TOP = 196;
	private static final int PREVIEW_ROW = 18;
	private static final int PREVIEW_REFRESH_TICKS = 10;

	private record PreviewEntry(ItemStack stack, String name, String itemId, boolean protectedSlot) {
	}

	private final Screen parent;
	private final SellRule draft;
	private final boolean protectHotbar;
	private final Consumer<SellRule> onSave;

	private RuleValidator.Result nameResult;
	private RuleValidator.ItemIdResult idResult;
	private ItemMatcher previewMatcher = ItemMatcher.EMPTY;
	private final List<PreviewEntry> preview = new ArrayList<>();
	private GolemButton saveButton;
	private int ticks;

	RuleEditScreen(Screen parent, SellRule draft, boolean protectHotbar, Consumer<SellRule> onSave) {
		super(Component.translatable(draft.namePattern.isEmpty() ? "golemaddons.rule.title.add" : "golemaddons.rule.title.edit"));
		this.parent = parent;
		this.draft = draft;
		this.protectHotbar = protectHotbar;
		this.onSave = onSave;
	}

	private int left() {
		return (width - CONTENT_WIDTH) / 2;
	}

	@Override
	protected void init() {
		int x = left();

		EditBox label = new GolemEditBox(font, x, 36, CONTENT_WIDTH, 20, Component.translatable("golemaddons.rule.label"));
		label.setMaxLength(64);
		label.setValue(draft.label);
		label.setResponder(value -> draft.label = value);
		addRenderableWidget(label);

		EditBox pattern = new GolemEditBox(font, x, 76, CONTENT_WIDTH, 20, Component.translatable("golemaddons.rule.pattern"));
		pattern.setMaxLength(RuleValidator.MAX_PATTERN_LENGTH);
		pattern.setValue(draft.namePattern);
		pattern.setHint(Component.translatable("golemaddons.rule.pattern.hint"));
		pattern.setResponder(value -> {
			draft.namePattern = value;
			revalidate();
		});
		addRenderableWidget(pattern);

		EditBox itemId = new GolemEditBox(font, x, 126, CONTENT_WIDTH, 20, Component.translatable("golemaddons.rule.item_id"));
		itemId.setMaxLength(100);
		itemId.setValue(draft.itemId);
		itemId.setHint(Component.translatable("golemaddons.rule.item_id.hint"));
		itemId.setResponder(value -> {
			draft.itemId = value;
			revalidate();
		});
		addRenderableWidget(itemId);

		addRenderableWidget(GolemCycle.onOffBuilder(draft.caseInsensitive)
				.create(x, 162, CONTENT_WIDTH / 2 - 2, 20, Component.translatable("golemaddons.rule.case_insensitive"), (b, value) -> {
					draft.caseInsensitive = value;
					revalidate();
				}));
		addRenderableWidget(GolemCycle.onOffBuilder(draft.enabled)
				.create(x + CONTENT_WIDTH / 2 + 2, 162, CONTENT_WIDTH / 2 - 2, 20, Component.translatable("golemaddons.rule.enabled"),
						(b, value) -> draft.enabled = value));

		saveButton = addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.config.save"), b -> save())
				.bounds(x, height - 28, CONTENT_WIDTH / 2 - 2, 20).build());
		addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.config.cancel"), b -> onClose())
				.bounds(x + CONTENT_WIDTH / 2 + 2, height - 28, CONTENT_WIDTH / 2 - 2, 20).build());

		setInitialFocus(pattern);
		revalidate();
	}

	private void revalidate() {
		nameResult = RuleValidator.validateNamePattern(draft.namePattern, draft.caseInsensitive);
		idResult = RuleValidator.normalizeItemId(draft.itemId);
		// Compile once per edit; the periodic preview refresh only rescans the inventory.
		previewMatcher = nameResult.valid() && idResult.valid() ? ItemMatcher.compileSingle(draft) : ItemMatcher.EMPTY;
		if (saveButton != null) {
			saveButton.active = nameResult.valid() && idResult.valid();
		}
		refreshPreview();
	}

	private void refreshPreview() {
		preview.clear();
		if (minecraft == null || minecraft.player == null || !nameResult.valid() || !idResult.valid()) {
			return;
		}
		ItemMatcher matcher = previewMatcher;
		Inventory inventory = minecraft.player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			if (InventoryScan.stackMatches(matcher, stack)) {
				boolean hotbar = i < Inventory.getSelectionSize();
				boolean isProtected = i == inventory.getSelectedSlot() || (protectHotbar && hotbar);
				preview.add(new PreviewEntry(stack.copy(), stack.getHoverName().getString(), InventoryScan.itemId(stack), isProtected));
			}
		}
	}

	@Override
	public void tick() {
		if (++ticks % PREVIEW_REFRESH_TICKS == 0) {
			refreshPreview();
		}
	}

	private void save() {
		if (!nameResult.valid() || !idResult.valid()) {
			return;
		}
		draft.itemId = idResult.itemId();
		onSave.accept(draft);
		minecraft.setScreen(parent);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int x = left();

		graphics.centeredText(font, title, width / 2, 10, UiText.WHITE);
		graphics.text(font, Component.translatable("golemaddons.rule.label"), x, 25, UiText.GRAY);
		graphics.text(font, Component.translatable("golemaddons.rule.pattern"), x, 65, UiText.GRAY);
		graphics.text(font, Component.translatable("golemaddons.rule.item_id"), x, 115, UiText.GRAY);

		if (nameResult.valid()) {
			graphics.text(font, Component.translatable("golemaddons.rule.pattern.ok"), x, 100, UiText.GREEN);
		} else if (nameResult.problem() != RuleValidator.Problem.BLANK) {
			graphics.text(font, UiText.problem(nameResult), x, 100, UiText.RED);
		}
		if (!idResult.valid()) {
			graphics.text(font, UiText.problem(idResult.problem(), idResult.itemId()), x, 150, UiText.RED);
		} else if (!idResult.itemId().isEmpty()) {
			graphics.text(font, Component.translatable("golemaddons.rule.item_id.ok", idResult.itemId()), x, 150, UiText.GRAY);
		}

		if (!nameResult.valid() || !idResult.valid()) {
			return;
		}
		int sellable = (int) preview.stream().filter(p -> !p.protectedSlot()).count();
		graphics.text(font, Component.translatable("golemaddons.rule.preview", sellable), x, PREVIEW_TOP - 10, UiText.WHITE);
		if (preview.isEmpty()) {
			graphics.text(font, Component.translatable("golemaddons.rule.preview.none"), x, PREVIEW_TOP + 4, UiText.GRAY);
			return;
		}
		int maxRows = Math.max(1, (height - 36 - PREVIEW_TOP) / PREVIEW_ROW);
		int shown = Math.min(preview.size(), preview.size() > maxRows ? maxRows - 1 : maxRows);
		for (int i = 0; i < shown; i++) {
			PreviewEntry entry = preview.get(i);
			int y = PREVIEW_TOP + i * PREVIEW_ROW;
			graphics.item(entry.stack(), x, y);
			graphics.itemDecorations(font, entry.stack(), x, y);
			String text = font.plainSubstrByWidth(entry.name() + "  " + entry.itemId(), CONTENT_WIDTH - 110);
			graphics.text(font, text, x + 20, y + 4, entry.protectedSlot() ? UiText.GRAY : UiText.WHITE);
			if (entry.protectedSlot()) {
				Component tag = Component.translatable("golemaddons.rule.preview.protected");
				graphics.text(font, tag, x + CONTENT_WIDTH - font.width(tag), y + 4, UiText.YELLOW);
			}
		}
		if (shown < preview.size()) {
			graphics.text(font, Component.translatable("golemaddons.rule.preview.more", preview.size() - shown),
					x + 20, PREVIEW_TOP + shown * PREVIEW_ROW + 4, UiText.GRAY);
		}
	}
}
