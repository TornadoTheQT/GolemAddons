package com.golemaddons.ui;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.partyfinder.PartyFinderFeature;
import com.golemaddons.partyfinder.PartyModels.DragonType;
import com.golemaddons.partyfinder.PartyModels.JoinRequest;
import com.golemaddons.partyfinder.PartyModels.Member;
import com.golemaddons.partyfinder.PartyModels.Party;
import com.golemaddons.partyfinder.PartyModels.Role;
import com.golemaddons.partyfinder.PartyModels.Status;
import com.golemaddons.partyfinder.PartyQuery;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * Browse parties on the server you're on: GDrag/NDrag tabs, search, sorting, open-only, and a
 * smoothly scrolling, paged list. Click a row to show its members; the button on the right joins (or manages,
 * leaves, or cancels). Filters are saved when the screen closes.
 */
public final class PartyFinderScreen extends EditorScreen {
	private static final int ROW_H = 30;
	private static final int EXPANDED_EXTRA = 20;
	private static final int PAGE_SIZE = 20;
	private static final int JOIN_W = 56;

	private record RowHit(Party party, int y0, int y1, int jx0, int jy0, int jx1, int jy1) {
	}

	private record JoinState(Component label, boolean enabled, int color, Component reason, Runnable action) {
	}

	private final PartyFinderFeature feature = PartyFinderFeature.instance();
	private final GolemConfig.PartyFinder prefs = working.partyFinder;
	private final Set<String> expanded = new HashSet<>();
	private final List<RowHit> rows = new ArrayList<>();
	private String searchText = "";
	private int page;
	private float scroll;
	private float scrollTarget;
	private long lastFrame;
	private List<Party> filtered = List.of();
	private int filteredRevision = -1;
	private boolean filterDirty = true;
	private String modeKey = "";

	private int listX0;
	private int listY0;
	private int listX1;
	private int listY1;
	private int pageBarY;
	private GolemButton prevPage;
	private GolemButton nextPage;

	public PartyFinderScreen(Screen parent) {
		super(Component.translatable("golemaddons.pf.title"), parent);
	}

	@Override
	public void added() {
		super.added();
		if (feature != null) feature.screenOpened();
	}

	@Override
	public void removed() {
		super.removed();
		if (feature != null) feature.screenClosed();
		savePrefs();
	}

	/** Saves only the finder's preferences, into the live config, so nothing else is overwritten. */
	private void savePrefs() {
		GolemConfig next = GolemAddonsClient.config().get().copy();
		GolemConfig.PartyFinder p = next.partyFinder;
		p.typeFilter = prefs.typeFilter;
		p.sort = prefs.sort;
		p.openOnly = prefs.openOnly;
		GolemAddonsClient.config().save(next);
	}

	@Override
	protected void build() {
		modeKey = modeKey();
		int contentW = maxContentWidth(580);
		int left = (width - contentW) / 2;
		int right = left + contentW;
		int y = HEADER_HEIGHT + 6;

		// Row 1: dragon type tabs, search, open-only, sort.
		int x = left;
		for (String type : List.of("all", "gdrag", "ndrag")) {
			DragonType t = DragonType.byId(type);
			Component label = t == null ? Component.translatable("golemaddons.pf.type.all") : Component.literal(t.label);
			int w = PartyUi.Chip.widthFor(font, label) + 4;
			addRenderableWidget(new PartyUi.Chip(x, y, w, 18, label, t == null ? UiText.ACCENT : t.color,
					() -> prefs.typeFilter.equals(type), () -> {
						prefs.typeFilter = type;
						invalidate();
					}));
			x += w + 2;
		}
		int sortW = Math.min(130, Math.max(96, contentW / 5));
		GolemCycle<PartyQuery.Sort> sort = GolemCycle.builder(
						(PartyQuery.Sort s) -> Component.translatable("golemaddons.pf.sort." + s.id), PartyQuery.Sort.byId(prefs.sort))
				.withValues(PartyQuery.Sort.values())
				.create(right - sortW, y, sortW, 18, Component.translatable("golemaddons.pf.sort"), (b, v) -> {
					prefs.sort = v.id;
					invalidate();
				});
		addRenderableWidget(sort);
		Component openLabel = Component.translatable("golemaddons.pf.open_only");
		int openW = PartyUi.Chip.widthFor(font, openLabel);
		PartyUi.Chip openOnly = new PartyUi.Chip(right - sortW - 4 - openW, y, openW, 18, openLabel, UiText.GREEN,
				() -> prefs.openOnly, () -> {
					prefs.openOnly = !prefs.openOnly;
					invalidate();
				});
		openOnly.setTooltip(Tooltip.create(Component.translatable("golemaddons.pf.open_only.tooltip")));
		addRenderableWidget(openOnly);
		int searchX = x + 4;
		int searchW = Math.max(40, right - sortW - openW - 8 - searchX);
		EditBox search = new GolemEditBox(font, searchX, y, searchW, 18, Component.translatable("golemaddons.pf.search"));
		search.setMaxLength(32);
		search.setHint(Component.translatable("golemaddons.pf.search.hint"));
		search.setTooltip(Tooltip.create(Component.translatable("golemaddons.pf.search.tooltip")));
		search.setValue(searchText);
		search.setResponder(v -> {
			searchText = v;
			invalidate();
		});
		addRenderableWidget(search);
		y += 18;

		// The list and its page bar.
		listX0 = left;
		listX1 = right;
		listY0 = y + 4;
		listY1 = height - FOOTER_HEIGHT - 20;
		panels.add(new Panel(listX0, listY0, listX1 - listX0, listY1 - listY0, Component.translatable("golemaddons.pf.parties")));
		pageBarY = listY1 + 3;
		prevPage = addRenderableWidget(GolemButton.builder(Component.literal("◀"), b -> setPage(page - 1))
				.bounds(listX0, pageBarY, 20, 14).build());
		nextPage = addRenderableWidget(GolemButton.builder(Component.literal("▶"), b -> setPage(page + 1))
				.bounds(listX1 - 20, pageBarY, 20, 14).build());

		boolean inParty = feature != null && (feature.leading() != null || feature.memberOf() != null);
		addFooter(
				GolemButton.builder(Component.translatable(inParty ? "golemaddons.pf.my_party" : "golemaddons.pf.create"),
						b -> minecraft.setScreen(new MyPartyScreen(this))),
				GolemButton.builder(Component.translatable("golemaddons.pf.refresh"), b -> {
					if (feature != null) feature.screenOpened();
				}).tooltip(Tooltip.create(Component.translatable("golemaddons.pf.refresh.tooltip"))),
				GolemButton.builder(Component.translatable("gui.done"), b -> onClose()));
	}

	private void invalidate() {
		filterDirty = true;
		page = 0;
		scrollTarget = 0;
	}

	private void setPage(int p) {
		page = p;
		scroll = scrollTarget = 0;
	}

	/** Whether you lead / belong to a party: the footer label depends on it. */
	private String modeKey() {
		if (feature == null) return "";
		return (feature.leading() != null) + ":" + (feature.memberOf() != null);
	}

	@Override
	public void tick() {
		if (!modeKey().equals(modeKey)) rebuildWidgets();
	}

	private List<Party> filtered() {
		if (feature == null) return List.of();
		if (filterDirty || filteredRevision != feature.revision()) {
			filtered = PartyQuery.apply(feature.parties(), new PartyQuery.Filter(DragonType.byId(prefs.typeFilter), searchText,
					EnumSet.noneOf(Role.class), prefs.openOnly, PartyQuery.Sort.byId(prefs.sort)));
			filteredRevision = feature.revision();
			filterDirty = false;
		}
		return filtered;
	}

	// ---- drawing ----

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);

		int ix0 = listX0 + PANEL_PAD;
		int ix1 = listX1 - PANEL_PAD - 4;
		int iy0 = listY0 + PANEL_LABEL + 2;
		int iy1 = listY1 - 3;
		rows.clear();

		Component empty = emptyState();
		PartyQuery.Page<Party> current = PartyQuery.page(filtered(), page, PAGE_SIZE);
		page = current.page();
		prevPage.active = page > 0;
		nextPage.active = page < current.pages() - 1;
		Component pageText = Component.translatable("golemaddons.pf.page", page + 1, current.pages(), current.total());
		g.centeredText(font, pageText, (listX0 + listX1) / 2, pageBarY + 3, UiText.GRAY);
		connectionBadge(g);

		if (empty != null) {
			drawEmpty(g, empty, (ix0 + ix1) / 2, (iy0 + iy1) / 2);
			return;
		}

		// Smooth scrolling: ease towards the target every frame.
		long now = Util.getMillis();
		float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		int contentH = 0;
		for (Party p : current.items()) contentH += rowHeight(p);
		float maxScroll = Math.max(0, contentH - (iy1 - iy0));
		scrollTarget = Math.clamp(scrollTarget, 0f, maxScroll);
		scroll += (scrollTarget - scroll) * Math.min(1f, dt * 14f);
		if (Math.abs(scrollTarget - scroll) < 0.3f) scroll = scrollTarget;

		boolean mouseInList = mouseX >= ix0 && mouseX < ix1 && mouseY >= iy0 && mouseY < iy1;
		Party hovered = null;
		JoinState hoveredJoin = null;
		g.enableScissor(ix0, iy0, ix1 + 4, iy1);
		int y = iy0 - Math.round(scroll);
		for (Party p : current.items()) {
			int h = rowHeight(p);
			if (y + h >= iy0 && y <= iy1) {
				JoinState join = joinState(p);
				RowHit hit = drawRow(g, p, join, ix0, y, ix1, h, mouseInList ? mouseX : -1, mouseY);
				rows.add(hit);
				if (mouseInList && mouseY >= hit.y0() && mouseY < hit.y1()) {
					boolean onJoin = mouseX >= hit.jx0() && mouseX < hit.jx1() && mouseY >= hit.jy0() && mouseY < hit.jy1();
					if (onJoin) hoveredJoin = join;
					else hovered = p;
				}
			}
			y += h;
		}
		g.disableScissor();

		if (maxScroll > 0) {
			int track = iy1 - iy0;
			int thumb = Math.max(12, Math.round(track * (track / (float) contentH)));
			int ty = iy0 + Math.round((track - thumb) * (scroll / maxScroll));
			g.fill(ix1 + 1, iy0, ix1 + 3, iy1, 0x30FFFFFF);
			g.fill(ix1 + 1, ty, ix1 + 3, ty + thumb, UiText.withAlpha(UiText.ACCENT, 0.9f));
		}

		if (hoveredJoin != null && hoveredJoin.reason() != null) {
			g.setTooltipForNextFrame(hoveredJoin.reason(), mouseX, mouseY);
		} else if (hovered != null) {
			g.setTooltipForNextFrame(font, rowTooltip(hovered), Optional.empty(), mouseX, mouseY);
		}
	}

	private void connectionBadge(GuiGraphicsExtractor g) {
		if (feature == null) return;
		Component text;
		int color;
		switch (feature.connection()) {
			case ONLINE -> {
				text = Component.translatable("golemaddons.pf.conn.online");
				color = UiText.GREEN;
			}
			case CONNECTING -> {
				text = Component.translatable("golemaddons.pf.conn.connecting");
				color = UiText.YELLOW;
			}
			case FAILED -> {
				text = Component.translatable("golemaddons.pf.conn.failed");
				color = UiText.RED;
			}
			default -> {
				text = Component.translatable("golemaddons.pf.conn.off");
				color = UiText.DARK_GRAY;
			}
		}
		int w = font.width(text);
		int x = listX1 - PANEL_PAD - w - 10;
		g.fill(x, listY0 + 5, x + 5, listY0 + 10, color);
		g.text(font, text, x + 8, listY0 + 4, UiText.GRAY, false);
	}

	/** What to show instead of the list, or null to show the list. */
	private Component emptyState() {
		if (feature == null || !feature.enabled()) return Component.translatable("golemaddons.pf.empty.disabled");
		if (!feature.onServer()) return Component.translatable("golemaddons.pf.empty.no_server");
		if (feature.connection() == PartyFinderFeature.Connection.FAILED && !feature.loaded()) {
			Component why = feature.problem();
			return why == null ? Component.translatable("golemaddons.pf.error.network") : why;
		}
		if (!feature.loaded()) {
			int dots = (int) (Util.getMillis() / 350 % 4);
			return Component.translatable("golemaddons.pf.empty.loading").append(".".repeat(dots));
		}
		if (feature.parties().isEmpty()) return Component.translatable("golemaddons.pf.empty.none");
		if (filtered().isEmpty()) return Component.translatable("golemaddons.pf.empty.filtered");
		return null;
	}

	private void drawEmpty(GuiGraphicsExtractor g, Component text, int cx, int cy) {
		boolean loading = feature != null && feature.onServer() && feature.enabled() && !feature.loaded()
				&& feature.connection() != PartyFinderFeature.Connection.FAILED;
		if (loading) {
			// A small spinner: eight dots, one bright, going round.
			long t = Util.getMillis() / 90;
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4;
				int dx = (int) Math.round(Math.cos(a) * 7);
				int dy = (int) Math.round(Math.sin(a) * 7);
				float bright = ((t - i) % 8 + 8) % 8 / 8f;
				g.fill(cx + dx - 1, cy - 18 + dy - 1, cx + dx + 1, cy - 18 + dy + 1, UiText.withAlpha(UiText.ACCENT, 1f - bright * 0.8f));
			}
		}
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, Math.max(80, listX1 - listX0 - 40));
		int y = cy - lines.size() * 5;
		for (net.minecraft.util.FormattedCharSequence line : lines) {
			g.centeredText(font, line, cx, y, UiText.GRAY);
			y += 10;
		}
	}

	private int rowHeight(Party p) {
		return expanded.contains(p.id()) ? ROW_H + EXPANDED_EXTRA : ROW_H;
	}

	private RowHit drawRow(GuiGraphicsExtractor g, Party p, JoinState join, int x0, int y, int x1, int h, int mouseX, int mouseY) {
		UUID me = feature.you();
		boolean hover = mouseX >= 0 && mouseY >= y && mouseY < y + h - 2;
		int bottom = y + h - 2;
		g.fill(x0, y, x1, bottom, hover ? 0x40FFFFFF : 0x60101018);
		g.fill(x0, y, x0 + 2, bottom, p.type().color);
		if (p.leaderId().equals(me)) g.outline(x0, y, x1 - x0, h - 2, UiText.withAlpha(UiText.ACCENT, 0.9f));
		else if (p.hasMember(me)) g.outline(x0, y, x1 - x0, h - 2, UiText.withAlpha(UiText.ACCENT_2, 0.9f));

		boolean open = expanded.contains(p.id());
		g.text(font, open ? "▾" : "▸", x0 + 5, y + 11, UiText.DARK_GRAY, false);
		PartyUi.head(g, p.leaderId(), x0 + 13, y + 6, 16);
		if (!p.leaderOnline()) g.fill(x0 + 13, y + 6, x0 + 29, y + 22, 0x90000000);

		int jx1 = x1 - 6;
		int jx0 = jx1 - JOIN_W;
		int jy0 = y + 7;
		int jy1 = jy0 + 16;

		// Right-hand info: status and size on line 1, free places on line 2.
		MutableComponent line1Right = PartyUi.status(p).copy().append(Component.literal("  " + p.size() + "/" + p.max()).withColor(UiText.WHITE & 0xFFFFFF));
		int free = Math.max(0, p.max() - p.size());
		Component rolesText = p.status() == Status.OPEN && free > 0
				? Component.translatable("golemaddons.pf.places_free", free).withColor(UiText.GRAY & 0xFFFFFF) : Component.empty();
		int infoW = Math.max(font.width(line1Right), font.width(rolesText));
		int infoX = jx0 - 8 - infoW;
		g.text(font, line1Right, jx0 - 8 - font.width(line1Right), y + 5, UiText.WHITE, false);
		g.text(font, rolesText, jx0 - 8 - font.width(rolesText), y + 17, UiText.WHITE, false);

		int textX = x0 + 34;
		int textW = Math.max(20, infoX - textX - 6);
		String typeTag = " " + p.type().label;
		String by = "  " + p.leaderName();
		int nameW = Math.max(10, textW - font.width(typeTag) - font.width(by));
		String name = PartyUi.fit(font, p.name(), nameW);
		g.text(font, name, textX, y + 5, UiText.WHITE, true);
		int tx = textX + font.width(name);
		g.text(font, typeTag, tx, y + 5, p.type().color, false);
		g.text(font, PartyUi.fit(font, by, Math.max(0, textX + textW - tx - font.width(typeTag))), tx + font.width(typeTag), y + 5,
				p.leaderOnline() ? UiText.GRAY : UiText.DARK_GRAY, false);
		String desc = p.desc().isEmpty() ? "" : PartyUi.fit(font, p.desc(), textW);
		if (desc.isEmpty()) {
			g.text(font, Component.translatable("golemaddons.pf.no_desc"), textX, y + 17, UiText.DARK_GRAY, false);
		} else {
			g.text(font, desc, textX, y + 17, UiText.GRAY, false);
		}

		// Join button.
		boolean onJoin = mouseX >= jx0 && mouseX < jx1 && mouseY >= jy0 && mouseY < jy1;
		int bg = !join.enabled() ? 0x50000000 : onJoin ? UiText.withAlpha(join.color(), 0.55f) : UiText.withAlpha(join.color(), 0.28f);
		g.fill(jx0, jy0, jx1, jy1, bg);
		g.outline(jx0, jy0, JOIN_W, 16, join.enabled() ? join.color() : 0x40FFFFFF);
		g.centeredText(font, join.label(), (jx0 + jx1) / 2, jy0 + 4, join.enabled() ? UiText.WHITE : 0xFF707070);

		if (open) {
			int ey = y + ROW_H - 2;
			int ex = textX;
			ex = drawMember(g, p.leaderId(), "★ " + p.leaderName(), null, ex, ey, x1);
			for (Member m : p.members()) ex = drawMember(g, m.uuid(), m.name(), null, ex, ey, x1);
			for (int i = p.size(); i < p.max() && ex < x1 - 30; i++) {
				g.outline(ex, ey + 2, 12, 12, 0x40FFFFFF);
				ex += 18;
			}
		}
		return new RowHit(p, y, bottom, jx0, jy0, jx1, jy1);
	}

	private int drawMember(GuiGraphicsExtractor g, UUID uuid, String name, Role role, int x, int y, int limit) {
		if (x > limit - 40) return x;
		PartyUi.head(g, uuid, x, y + 2, 12);
		String shown = PartyUi.fit(font, name, 70);
		g.text(font, shown, x + 15, y + 4, UiText.WHITE, false);
		int end = x + 15 + font.width(shown);
		if (role != null) {
			Component r = PartyUi.role(role);
			g.text(font, r, end + 4, y + 4, UiText.WHITE, false);
			end += 4 + font.width(r);
		}
		return end + 10;
	}

	private List<Component> rowTooltip(Party p) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(p.name()).withColor(UiText.WHITE & 0xFFFFFF)
				.append(Component.literal("  " + p.type().label).withColor(p.type().color & 0xFFFFFF)));
		if (!p.desc().isEmpty()) lines.add(Component.literal(p.desc()).withColor(UiText.GRAY & 0xFFFFFF));
		lines.add(Component.translatable("golemaddons.pf.tooltip.leader", p.leaderName(), PartyUi.ago(p.createdAt()))
				.withColor(UiText.GRAY & 0xFFFFFF));
		if (!p.leaderOnline()) lines.add(Component.translatable("golemaddons.pf.tooltip.leader_offline").withColor(UiText.YELLOW & 0xFFFFFF));
		if (p.autoAccept()) lines.add(Component.translatable("golemaddons.pf.tooltip.auto_accept").withColor(UiText.ACCENT_2 & 0xFFFFFF));
		lines.add(Component.translatable(expanded.contains(p.id()) ? "golemaddons.pf.tooltip.collapse" : "golemaddons.pf.tooltip.expand")
				.withColor(UiText.DARK_GRAY & 0xFFFFFF));
		return lines;
	}

	private JoinState joinState(Party p) {
		UUID me = feature.you();
		if (p.leaderId().equals(me)) {
			return new JoinState(Component.translatable("golemaddons.pf.manage"), true, UiText.ACCENT, null,
					() -> minecraft.setScreen(new MyPartyScreen(this)));
		}
		if (p.hasMember(me)) {
			return new JoinState(Component.translatable("golemaddons.pf.leave"), true, UiText.RED,
					Component.translatable("golemaddons.pf.leave.tooltip"), feature::leave);
		}
		JoinRequest out = feature.outgoing();
		if (out != null && out.party().equals(p.id())) {
			return new JoinState(Component.translatable("golemaddons.pf.cancel"), true, UiText.YELLOW,
					Component.translatable("golemaddons.pf.cancel.tooltip"), feature::cancelRequest);
		}
		Component join = Component.translatable("golemaddons.pf.join");
		Role role = null; // roles are no longer chosen: any free place
		Component reason = null;
		if (feature.connection() != PartyFinderFeature.Connection.ONLINE) reason = Component.translatable("golemaddons.pf.error.offline");
		else if (feature.leading() != null) reason = Component.translatable("golemaddons.pf.why.leading");
		else if (out != null) reason = Component.translatable("golemaddons.pf.why.requested");
		else if (p.status() == Status.IN_PROGRESS) reason = Component.translatable("golemaddons.pf.why.in_progress");
		else if (p.status() == Status.FULL) reason = Component.translatable("golemaddons.pf.why.full");
		else if (!p.leaderOnline()) reason = Component.translatable("golemaddons.pf.why.leader_offline");
		else if (!p.canTake(role)) reason = Component.translatable("golemaddons.pf.why.full");
		if (reason != null) return new JoinState(join, false, UiText.GREEN, reason, () -> {});
		Component tip = Component.translatable("golemaddons.pf.join.tooltip", p.leaderName());
		return new JoinState(join, true, UiText.GREEN, tip, () -> feature.join(p, role));
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		double mx = event.x();
		double my = event.y();
		if (event.button() != 0 || mx < listX0 || mx >= listX1 || my < listY0 + PANEL_LABEL || my >= listY1) return false;
		for (RowHit hit : rows) {
			if (my < hit.y0() || my >= hit.y1()) continue;
			if (mx >= hit.jx0() && mx < hit.jx1() && my >= hit.jy0() && my < hit.jy1()) {
				JoinState join = joinState(hit.party());
				if (join.enabled()) {
					click();
					join.action().run();
				}
			} else {
				click();
				if (!expanded.remove(hit.party().id())) expanded.add(hit.party().id());
			}
			return true;
		}
		return false;
	}

	private void click() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x >= listX0 && x < listX1 && y >= listY0 && y < listY1) {
			scrollTarget -= (float) scrollY * 28f;
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}
}
