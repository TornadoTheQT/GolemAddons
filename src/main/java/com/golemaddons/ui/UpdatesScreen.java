package com.golemaddons.ui;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.update.Release;
import com.golemaddons.update.UpdateChecker;
import com.golemaddons.update.Versions;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Update checker: current and latest version, release notes, install / skip, and the update settings. */
final class UpdatesScreen extends EditorScreen {
	private static final int ROW = 20;

	private final UpdateChecker checker = UpdateChecker.instance();
	private GolemButton checkButton;
	private GolemButton installButton;
	private GolemButton pageButton;
	private GolemButton skipButton;
	private UpdateChecker.State shownState;
	private Release shownRelease;
	private int textX;
	private int statusY;
	private int notesX0;
	private int notesY0;
	private int notesX1;
	private int notesY1;
	private int notesScroll;

	UpdatesScreen(Screen parent) {
		super(Component.translatable("golemaddons.updates.title"), parent);
	}

	@Override
	protected void build() {
		GolemConfig.Updates s = working.updates;
		int panelW = maxContentWidth(440);
		int inner = panelW - 2 * PANEL_PAD - 2;
		int px = (width - panelW) / 2;
		int ix = px + PANEL_PAD + 2;
		textX = ix;

		int top = HEADER_HEIGHT + 6;
		int bottom = height - FOOTER_HEIGHT - 6;
		int versionH = PANEL_LABEL + 24 + ROW + PANEL_PAD;
		int settingsH = PANEL_LABEL + ROW + 4 + ROW + PANEL_PAD + 2;
		int y = top;

		panels.add(new Panel(px, y, panelW, versionH, Component.translatable("golemaddons.updates.panel.version")));
		statusY = y + PANEL_LABEL + 1;
		int rowY = y + PANEL_LABEL + 24;
		int quarter = (inner - GAP * 6) / 4;
		checkButton = addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.updates.check"), b -> checker.check(true))
				.bounds(ix, rowY, quarter, ROW).build());
		installButton = addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.updates.install"), b -> checker.install())
				.bounds(ix + quarter + GAP * 2, rowY, quarter, ROW).build());
		pageButton = addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.updates.page"), b -> {
			Release r = checker.latest();
			if (r != null) ConfirmLinkScreen.confirmLinkNow(this, r.pageUrl());
		}).bounds(ix + 2 * (quarter + GAP * 2), rowY, quarter, ROW).build());
		skipButton = addRenderableWidget(GolemButton.builder(Component.translatable("golemaddons.updates.skip"), b -> skip())
				.tooltip(Tooltip.create(Component.translatable("golemaddons.updates.skip.tooltip")))
				.bounds(ix + inner - quarter, rowY, quarter, ROW).build());
		y += versionH + PANEL_GAP;

		panels.add(new Panel(px, y, panelW, settingsH, Component.translatable("golemaddons.updates.panel.settings")));
		rowY = y + PANEL_LABEL + 2;
		int half = (inner - GAP * 2) / 2;
		addRenderableWidget(GolemCycle.onOffBuilder(s.checkOnStartup).create(ix, rowY, half, ROW,
				Component.translatable("golemaddons.updates.on_startup"), (b, v) -> s.checkOnStartup = v));
		addRenderableWidget(GolemCycle.onOffBuilder(s.includePrereleases)
				.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.updates.prereleases.tooltip")))
				.create(ix + inner - half, rowY, half, ROW, Component.translatable("golemaddons.updates.prereleases"),
						(b, v) -> s.includePrereleases = v));
		rowY += ROW + 4;
		GolemCycle<Boolean> auto = addRenderableWidget(GolemCycle.onOffBuilder(s.autoUpdate)
				.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.updates.auto.tooltip")))
				.create(ix, rowY, inner, ROW, Component.translatable("golemaddons.updates.auto"), (b, v) -> s.autoUpdate = v));
		auto.active = checker.installUnavailable() == null;
		y += settingsH + PANEL_GAP;

		panels.add(new Panel(px, y, panelW, Math.max(PANEL_LABEL + 14, bottom - y), Component.translatable("golemaddons.updates.panel.notes")));
		notesX0 = ix;
		notesY0 = y + PANEL_LABEL + 2;
		notesX1 = ix + inner;
		notesY1 = Math.max(notesY0 + 10, bottom - PANEL_PAD);

		addFooter(saveButton(), cancelButton());
		refreshButtons();
	}

	private void skip() {
		Release r = checker.latest();
		if (r == null) return;
		working.updates.skippedVersion = r.version();
		// Saved right away so the reminder stops even if the player then presses Cancel.
		GolemConfig next = GolemAddonsClient.config().get().copy();
		next.updates.skippedVersion = r.version();
		GolemAddonsClient.config().save(next);
		refreshButtons();
	}

	private void refreshButtons() {
		UpdateChecker.State state = checker.state();
		Release r = checker.latest();
		boolean newer = r != null && Versions.isNewer(r.version(), checker.currentVersion());
		boolean busy = state == UpdateChecker.State.CHECKING || state == UpdateChecker.State.DOWNLOADING;
		checkButton.active = !busy && state != UpdateChecker.State.STAGED;
		installButton.active = newer && !busy && state != UpdateChecker.State.STAGED && r.sha256() != null
				&& checker.installUnavailable() == null;
		pageButton.active = r != null;
		skipButton.active = newer && !r.version().equals(GolemAddonsClient.config().get().updates.skippedVersion);
		if (newer && r.sha256() == null) {
			installButton.setTooltip(Tooltip.create(Component.translatable("golemaddons.updates.install.no_checksum")));
		} else if (checker.installUnavailable() != null) {
			installButton.setTooltip(Tooltip.create(Component.translatable("golemaddons.updates.unavailable."
					+ checker.installUnavailable().name().toLowerCase(java.util.Locale.ROOT))));
		} else {
			installButton.setTooltip(Tooltip.create(Component.translatable("golemaddons.updates.install.tooltip")));
		}
		shownState = state;
		shownRelease = r;
	}

	@Override
	public void tick() {
		if (checker.state() != shownState || checker.latest() != shownRelease) {
			notesScroll = 0;
			refreshButtons();
		}
	}

	private Component statusLine() {
		Release r = checker.latest();
		return switch (checker.state()) {
			case IDLE -> Component.translatable("golemaddons.updates.status.idle");
			case CHECKING -> Component.translatable("golemaddons.updates.status.checking");
			case UP_TO_DATE -> Component.translatable("golemaddons.updates.status.up_to_date");
			case AVAILABLE -> Component.translatable("golemaddons.updates.status.available", r.version());
			case DOWNLOADING -> Component.translatable("golemaddons.updates.status.downloading",
					r == null ? 0 : Math.min(100, checker.downloaded() * 100 / Math.max(1, r.size())));
			case STAGED -> Component.translatable("golemaddons.updates.status.staged", r.version());
			case ERROR -> Component.translatable("golemaddons.updates.error." + checker.error(), checker.errorDetail());
		};
	}

	private int statusColor() {
		return switch (checker.state()) {
			case UP_TO_DATE, STAGED -> UiText.GREEN;
			case AVAILABLE -> UiText.ACCENT_2;
			case ERROR -> UiText.RED;
			default -> UiText.GRAY;
		};
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x >= notesX0 && x < notesX1 && y >= notesY0 && y < notesY1) {
			notesScroll = Math.max(0, notesScroll - (int) Math.signum(scrollY) * 3);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		Release r = checker.latest();
		graphics.text(font, Component.translatable("golemaddons.updates.current", checker.currentVersion(),
				r == null ? "?" : r.version()), textX, statusY, UiText.WHITE);
		graphics.text(font, statusLine(), textX, statusY + 11, statusColor());

		// Release notes are untrusted text: drawn as literal strings only, never as components with click events.
		String notes = r == null ? "" : r.notes();
		if (notes.isBlank()) {
			graphics.text(font, Component.translatable("golemaddons.updates.notes.none"), notesX0, notesY0, UiText.DARK_GRAY);
			return;
		}
		List<FormattedCharSequence> lines = font.split(Component.literal(notes), notesX1 - notesX0);
		int visible = Math.max(1, (notesY1 - notesY0) / 10);
		notesScroll = Math.min(notesScroll, Math.max(0, lines.size() - visible));
		for (int i = 0; i < visible && notesScroll + i < lines.size(); i++) {
			graphics.text(font, lines.get(notesScroll + i), notesX0, notesY0 + i * 10, UiText.GRAY, false);
		}
		if (lines.size() > visible) {
			int barH = Math.max(6, (notesY1 - notesY0) * visible / lines.size());
			int barY = notesY0 + (notesY1 - notesY0 - barH) * notesScroll / Math.max(1, lines.size() - visible);
			graphics.fill(notesX1 + 2, barY, notesX1 + 4, barY + barH, 0x80FFFFFF);
		}
	}
}
