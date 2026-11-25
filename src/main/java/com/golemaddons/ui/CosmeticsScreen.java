package com.golemaddons.ui;

import com.golemaddons.config.GolemConfig;
import com.golemaddons.cosmetics.ApiUrl;
import com.golemaddons.cosmetics.CapeUrlRules;
import com.golemaddons.cosmetics.CosmeticName;
import com.golemaddons.cosmetics.CosmeticText;
import com.golemaddons.cosmetics.CosmeticsFeature;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Custom name and cape (applied straight to the cosmetics server, no commands needed), what to show, which server,
 * and a live preview of your player walking with your nametag and cape. Compact enough for small windows: help is
 * in tooltips and one status line under the panels shows errors and results.
 */
final class CosmeticsScreen extends EditorScreen {
	private static final int PREVIEW_W = 104;
	private static final int ROW = 20;
	private static final int SMALL_GAP = 6;

	private EditBox nameBox;
	private EditBox capeBox;
	private GolemButton nameApply;
	private GolemButton nameReset;
	private GolemButton capeApply;
	private GolemButton capeReset;
	private CosmeticName.Result typedName = CosmeticName.parse("");
	private CapeUrlRules.Result typedCape = CapeUrlRules.check("", null);
	private boolean urlValid = true;

	private int previewX0;
	private int previewY0;
	private int previewX1;
	private int previewY1;
	private int urlStatusY;
	private int statusY;
	private int textX;

	CosmeticsScreen(Screen parent) {
		super(Component.translatable("golemaddons.cosmetics.title"), parent);
	}

	private static CosmeticsFeature feature() {
		return CosmeticsFeature.instance();
	}

	@Override
	protected void build() {
		GolemConfig.Cosmetics settings = working.cosmetics;
		int contentW = maxContentWidth(480);
		boolean showPreview = contentW >= 340;
		int columnW = showPreview ? contentW - PREVIEW_W - SMALL_GAP : contentW;
		int left = (width - contentW) / 2;
		int px = showPreview ? left + PREVIEW_W + SMALL_GAP : left;
		int inner = columnW - 2 * PANEL_PAD - 2;
		int ix = px + PANEL_PAD + 2;
		textX = ix;

		int fieldH = PANEL_LABEL + ROW + PANEL_PAD;
		int settingsH = PANEL_LABEL + ROW + 4 + ROW + 12 + PANEL_PAD;
		int total = 2 * fieldH + settingsH + 2 * SMALL_GAP;
		int y = centeredTop(total + 12);
		int top = y;

		Component apply = Component.translatable("golemaddons.cosmetics.apply");
		Component reset = Component.translatable("golemaddons.cosmetics.reset");
		int applyW = buttonWidth(apply, 44);
		int resetW = buttonWidth(reset, 44);
		int boxW = inner - applyW - resetW - 2 * GAP;

		// Name
		panels.add(new Panel(px, y, columnW, fieldH, Component.translatable("golemaddons.cosmetics.panel.name")));
		int rowY = y + PANEL_LABEL + 2;
		nameBox = new GolemEditBox(font, ix, rowY, boxW, ROW, Component.translatable("golemaddons.cosmetics.panel.name"));
		nameBox.setMaxLength(CosmeticName.MAX_RAW);
		nameBox.setHint(Component.translatable("golemaddons.cosmetics.name.hint"));
		nameBox.setTooltip(Tooltip.create(Component.translatable("golemaddons.cosmetics.name.help")));
		CosmeticsFeature.Cosmetic own = feature() == null ? null : feature().own();
		nameBox.setValue(own != null && own.rawName() != null ? own.rawName() : "");
		nameBox.setResponder(v -> typedName = CosmeticName.parse(v));
		typedName = CosmeticName.parse(nameBox.getValue());
		addRenderableWidget(nameBox);
		nameApply = addRenderableWidget(GolemButton.builder(apply, b -> applyName()).bounds(ix + boxW + GAP, rowY, applyW, ROW).build());
		nameReset = addRenderableWidget(GolemButton.builder(reset, b -> send("name", null))
				.tooltip(Tooltip.create(Component.translatable("golemaddons.cosmetics.name.reset_tooltip")))
				.bounds(ix + inner - resetW, rowY, resetW, ROW).build());
		y += fieldH + SMALL_GAP;

		// Cape
		panels.add(new Panel(px, y, columnW, fieldH, Component.translatable("golemaddons.cosmetics.panel.cape")));
		rowY = y + PANEL_LABEL + 2;
		capeBox = new GolemEditBox(font, ix, rowY, boxW, ROW, Component.translatable("golemaddons.cosmetics.panel.cape"));
		capeBox.setMaxLength(CapeUrlRules.MAX_LENGTH);
		capeBox.setHint(Component.translatable("golemaddons.cosmetics.cape.hint"));
		capeBox.setTooltip(Tooltip.create(Component.translatable("golemaddons.cosmetics.cape.help")));
		capeBox.setResponder(v -> typedCape = CapeUrlRules.check(v, null));
		addRenderableWidget(capeBox);
		capeApply = addRenderableWidget(GolemButton.builder(apply, b -> applyCape()).bounds(ix + boxW + GAP, rowY, applyW, ROW).build());
		capeReset = addRenderableWidget(GolemButton.builder(reset, b -> send("capeUrl", null))
				.tooltip(Tooltip.create(Component.translatable("golemaddons.cosmetics.cape.reset_tooltip")))
				.bounds(ix + inner - resetW, rowY, resetW, ROW).build());
		y += fieldH + SMALL_GAP;

		// Settings: show toggles, then the server
		panels.add(new Panel(px, y, columnW, settingsH, Component.translatable("golemaddons.cosmetics.panel.settings")));
		int third = (inner - 2 * GAP) / 3;
		rowY = y + PANEL_LABEL + 2;
		addRenderableWidget(GolemCycle.onOffBuilder(settings.enabled)
				.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.cosmetics.enabled.tooltip")))
				.create(ix, rowY, third, ROW, Component.translatable("golemaddons.cosmetics.enabled"), (b, v) -> settings.enabled = v));
		addRenderableWidget(GolemCycle.onOffBuilder(settings.showOthers)
				.create(ix + third + GAP, rowY, third, ROW, Component.translatable("golemaddons.cosmetics.show_others"), (b, v) -> settings.showOthers = v));
		addRenderableWidget(GolemCycle.onOffBuilder(settings.showOwn)
				.create(ix + 2 * (third + GAP), rowY, inner - 2 * (third + GAP), ROW, Component.translatable("golemaddons.cosmetics.show_own"),
						(b, v) -> settings.showOwn = v));
		rowY += ROW + 4;
		EditBox urlBox = new GolemEditBox(font, ix, rowY, inner, ROW, Component.translatable("golemaddons.cosmetics.panel.server"));
		urlBox.setMaxLength(256);
		urlBox.setValue(settings.apiBaseUrl);
		urlBox.setHint(Component.translatable("golemaddons.cosmetics.server.hint"));
		urlBox.setTooltip(Tooltip.create(Component.translatable("golemaddons.cosmetics.panel.server")));
		urlValid = settings.apiBaseUrl.isEmpty() || ApiUrl.parse(settings.apiBaseUrl) != null;
		urlBox.setResponder(value -> {
			String v = value.strip();
			urlValid = v.isEmpty() || ApiUrl.parse(v) != null;
			settings.apiBaseUrl = urlValid ? v : "";
		});
		addRenderableWidget(urlBox);
		urlStatusY = rowY + ROW + 3;
		y += settingsH;
		statusY = y + 4;

		if (showPreview) {
			previewX0 = left;
			previewY0 = top;
			previewX1 = left + PREVIEW_W;
			previewY1 = y;
			panels.add(new Panel(previewX0, previewY0, PREVIEW_W, previewY1 - previewY0, Component.translatable("golemaddons.cosmetics.panel.preview")));
		} else {
			previewX1 = previewX0;
		}

		addFooter(saveButton(), cancelButton());
		updateButtons();
	}

	private void applyName() {
		if (typedName.valid()) send("name", typedName.raw());
	}

	private void applyCape() {
		CapeUrlRules.Result result = CapeUrlRules.check(capeBox.getValue(), null);
		if (result.valid()) send("capeUrl", result.url());
	}

	private void send(String key, String value) {
		CosmeticsFeature f = feature();
		if (f == null) return;
		Map<String, String> change = new HashMap<>();
		change.put(key, value);
		f.update(change);
		updateButtons();
	}

	private void updateButtons() {
		CosmeticsFeature f = feature();
		boolean ready = f != null && f.active() && !f.updating();
		CosmeticsFeature.Cosmetic own = f == null ? null : f.own();
		String currentName = own == null ? null : own.rawName();
		nameApply.active = ready && typedName.valid() && !typedName.raw().equals(currentName);
		nameReset.active = ready && currentName != null;
		capeApply.active = ready && typedCape.valid();
		capeReset.active = ready && own != null && own.capeHash() != null;
	}

	@Override
	public void tick() {
		updateButtons();
	}

	/** The nametag for the preview: the typed name if valid, else your current custom name, else your username. */
	private Component previewName() {
		if (typedName.valid()) {
			return CosmeticText.build(typedName.runs());
		}
		CosmeticsFeature f = feature();
		CosmeticsFeature.Cosmetic own = f == null ? null : f.own();
		if (own != null && own.name() != null) {
			return own.name();
		}
		return Component.literal(minecraft.getUser().getName());
	}

	/** One line under the panels: input errors first, then why changes can't be made, then the last result. */
	private Component status(CosmeticsFeature f) {
		if (!nameBox.getValue().isBlank() && !typedName.valid()) {
			return Component.translatable("golemaddons.cosmetics.name." + typedName.problem().name().toLowerCase(Locale.ROOT),
					typedName.detail()).withColor(UiText.RED & 0xFFFFFF);
		}
		if (!capeBox.getValue().isBlank() && !typedCape.valid()) {
			return Component.translatable("golemaddons.cosmetics.cape." + typedCape.problem().name().toLowerCase(Locale.ROOT))
					.withColor(UiText.RED & 0xFFFFFF);
		}
		if (f == null || !f.active()) {
			return Component.translatable(f != null && f.hasBuiltInServer() ? "golemaddons.cosmetics.error.disabled"
					: "golemaddons.cosmetics.error.no_server").withColor(UiText.YELLOW & 0xFFFFFF);
		}
		if (f.updating()) {
			return Component.translatable("golemaddons.cosmetics.updating");
		}
		return f.lastUpdateMessage();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		CosmeticsFeature f = feature();

		if (previewX1 > previewX0) {
			PlayerPreview.extract(graphics, minecraft, previewX0 + 3, previewY0 + PANEL_LABEL + 2, previewX1 - 3, previewY1 - 3, previewName());
		}

		boolean builtIn = f != null && f.hasBuiltInServer();
		Component urlStatus;
		int urlColor;
		if (!urlValid) {
			urlStatus = Component.translatable("golemaddons.cosmetics.server.invalid");
			urlColor = UiText.RED;
		} else if (working.cosmetics.apiBaseUrl.isEmpty()) {
			urlStatus = Component.translatable(builtIn ? "golemaddons.cosmetics.server.builtin" : "golemaddons.cosmetics.server.none");
			urlColor = builtIn ? UiText.GRAY : UiText.YELLOW;
		} else {
			urlStatus = Component.translatable("golemaddons.cosmetics.server.override");
			urlColor = UiText.GRAY;
		}
		graphics.text(font, urlStatus, textX, urlStatusY, urlColor, false);

		Component status = status(f);
		if (status != null) {
			graphics.centeredText(font, status, width / 2, statusY, UiText.GRAY);
		}
	}
}
