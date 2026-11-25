package com.golemaddons.ui;

import com.golemaddons.autosell.RuleValidator;
import com.golemaddons.autosell.SellRule;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Mining Auto-Sell: the user's own regex rules, in a centered, paged list. */
final class MiningAutoSellScreen extends EditorScreen {
	private static final int ROW_HEIGHT = 24;
	private static final int TOGGLE_WIDTH = 40;

	private int page;
	private int listX;
	private int listTop;
	private int listWidth;
	private int textRight;
	/** Cached so patterns aren't re-validated every frame. */
	private final Map<SellRule, Boolean> ruleValid = new IdentityHashMap<>();

	MiningAutoSellScreen(Screen parent) {
		super(Component.translatable("golemaddons.mining.title"), parent);
	}

	private List<SellRule> rules() {
		return working.miningAutoSell.rules;
	}

	private int rowsPerPage() {
		int available = height - HEADER_HEIGHT - FOOTER_HEIGHT - 12 - PANEL_LABEL - PANEL_PAD - PANEL_GAP - 20 - 12;
		return Math.clamp(available / ROW_HEIGHT, 1, 8);
	}

	private int pageCount() {
		return Math.max(1, (rules().size() + rowsPerPage() - 1) / rowsPerPage());
	}

	@Override
	protected void build() {
		page = Math.min(page, pageCount() - 1);
		ruleValid.clear();
		int rowsShown = Math.max(1, Math.min(rowsPerPage(), rules().size()));

		int panelW = maxContentWidth(440);
		int panelH = PANEL_LABEL + rowsShown * ROW_HEIGHT + PANEL_PAD;
		int block = 12 + panelH + PANEL_GAP + 20;
		int px = (width - panelW) / 2;
		int top = centeredTop(block) + 12;
		panels.add(new Panel(px, top, panelW, panelH, Component.translatable("golemaddons.config.rules")));

		listX = px + PANEL_PAD + 2;
		listWidth = panelW - 2 * PANEL_PAD - 2;
		listTop = top + PANEL_LABEL + 2;

		Component edit = Component.translatable("golemaddons.config.rule.edit");
		Component delete = Component.translatable("golemaddons.config.rule.delete");
		int editW = buttonWidth(edit, 44);
		int deleteW = buttonWidth(delete, 50);
		int deleteX = listX + listWidth - deleteW;
		int editX = deleteX - GAP - editW;
		textRight = editX - 6;

		List<SellRule> rules = rules();
		int first = page * rowsPerPage();
		for (int i = first; i < Math.min(rules.size(), first + rowsPerPage()); i++) {
			SellRule rule = rules.get(i);
			int y = listTop + (i - first) * ROW_HEIGHT;
			ruleValid.put(rule, RuleValidator.validateNamePattern(rule.namePattern, rule.caseInsensitive).valid()
					&& RuleValidator.normalizeItemId(rule.itemId).valid());
			addRenderableWidget(CycleButton.onOffBuilder(rule.enabled).displayOnlyValue()
					.create(listX, y, TOGGLE_WIDTH, 20, Component.translatable("golemaddons.config.rule.enabled"), (button, value) -> rule.enabled = value));
			addRenderableWidget(Button.builder(edit, b -> editRule(rule)).bounds(editX, y, editW, 20).build());
			addRenderableWidget(Button.builder(delete, b -> confirmDelete(rule)).bounds(deleteX, y, deleteW, 20).build());
		}

		int rowY = top + panelH + PANEL_GAP;
		Component add = Component.translatable("golemaddons.config.add_rule");
		int addW = buttonWidth(add, 120);
		addRenderableWidget(Button.builder(add, b -> addRule()).bounds((width - addW) / 2, rowY, addW, 20).build());
		if (pageCount() > 1) {
			Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1))
					.bounds(px, rowY, 20, 20).build());
			Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1))
					.bounds(px + panelW - 20, rowY, 20, 20).build());
			prev.active = page > 0;
			next.active = page < pageCount() - 1;
		}

		addFooter(saveButton(), cancelButton());
	}

	private void changePage(int delta) {
		page = Math.clamp(page + delta, 0, pageCount() - 1);
		rebuildWidgets();
	}

	private void addRule() {
		minecraft.setScreen(new RuleEditScreen(this, new SellRule(true, "", "", "", false), working.selling.protectHotbar, saved -> {
			rules().add(saved);
			page = Integer.MAX_VALUE; // clamped to the last page on init
		}));
	}

	private void editRule(SellRule rule) {
		minecraft.setScreen(new RuleEditScreen(this, rule.copy(), working.selling.protectHotbar, saved -> {
			int index = rules().indexOf(rule);
			if (index >= 0) {
				rules().set(index, saved);
			}
		}));
	}

	private void confirmDelete(SellRule rule) {
		minecraft.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				rules().remove(rule);
			}
			minecraft.setScreen(this);
		}, Component.translatable("golemaddons.config.delete.title"),
				Component.translatable("golemaddons.config.delete.message", rule.displayName())));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		Panel panel = panels.get(0);
		graphics.centeredText(font, Component.translatable("golemaddons.mining.hint"), width / 2, panel.y() - 12, UiText.GRAY);

		int pages = pageCount();
		if (pages > 1) {
			Component pageText = Component.translatable("golemaddons.config.page", page + 1, pages);
			graphics.text(font, pageText, panel.x() + panel.w() - PANEL_PAD - font.width(pageText), panel.y() + 4, UiText.GRAY);
		}

		List<SellRule> rules = rules();
		if (rules.isEmpty()) {
			graphics.text(font, Component.translatable("golemaddons.config.rules.empty"), listX, listTop + 6, UiText.GRAY);
			return;
		}
		int first = page * rowsPerPage();
		int textX = listX + TOGGLE_WIDTH + 8;
		for (int i = first; i < Math.min(rules.size(), first + rowsPerPage()); i++) {
			SellRule rule = rules.get(i);
			int y = listTop + (i - first) * ROW_HEIGHT + 6;
			boolean valid = ruleValid.getOrDefault(rule, false);
			Component status = Component.translatable(valid ? "golemaddons.config.rule.ok" : "golemaddons.config.rule.invalid");
			int statusWidth = font.width(status);
			String name = font.plainSubstrByWidth(rule.displayName(), textRight - textX - statusWidth - 8);
			graphics.text(font, name, textX, y, rule.enabled ? UiText.WHITE : UiText.GRAY);
			graphics.text(font, status, textRight - statusWidth, y, valid ? UiText.GREEN : UiText.RED);
		}
	}
}
