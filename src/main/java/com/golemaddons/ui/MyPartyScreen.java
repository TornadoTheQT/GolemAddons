package com.golemaddons.ui;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.partyfinder.PartyFinderFeature;
import com.golemaddons.partyfinder.PartyModels;
import com.golemaddons.partyfinder.PartyModels.DragonType;
import com.golemaddons.partyfinder.PartyModels.JoinRequest;
import com.golemaddons.partyfinder.PartyModels.Member;
import com.golemaddons.partyfinder.PartyModels.Party;
import com.golemaddons.partyfinder.PartyModels.Role;
import com.golemaddons.partyfinder.PartyModels.Settings;
import com.golemaddons.partyfinder.PartyText;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Create, edit and run your party: type, name, description, size, role slots and auto-accept on the left; members
 * and join requests (accept, deny, kick) on the right. Members of someone else's party see that party with a Leave
 * button. Settings you type are kept as the draft for next time, and notification options are saved too.
 */
final class MyPartyScreen extends EditorScreen {
	private static final int ROW = 20;
	private static final int LINE = 14;
	private static final int MAX_REQUESTS_SHOWN = 4;

	private final PartyFinderFeature feature = PartyFinderFeature.instance();
	private final GolemConfig.PartyFinder prefs = working.partyFinder;

	// The form, kept across rebuilds so live updates never wipe what you typed.
	private DragonType type;
	private String name;
	private String desc;
	private int max;
	private final EnumMap<Role, Integer> roles = PartyModels.emptyRoles();
	private boolean autoAccept;
	private String formFor;

	private String modeKey = "";
	private String seenContent = "";
	private long disbandArmedUntil;
	private Button primary;
	private Button disbandButton;
	private int statusX;
	private int statusY;
	private int statusW;
	private int rightX;
	private int rightW;
	private final List<Line> lines = new ArrayList<>();
	/** A wrapped hint in the right panel, when there's nothing else to show there. */
	private Component wrapped;
	private int wrappedY;

	/** One drawn line in the right panel (a member or a request); its buttons are real widgets. */
	private record Line(int y, UUID uuid, Component text) {
	}

	MyPartyScreen(Screen parent) {
		super(Component.translatable("golemaddons.pf.my_party"), parent);
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
		saveDraftAndOptions();
	}

	/** Loads the form from your party when you lead one, else from the saved draft. */
	private void loadForm() {
		Party own = feature == null ? null : feature.leading();
		String key = own == null ? "draft" : own.id();
		if (key.equals(formFor)) return;
		formFor = key;
		if (own != null) {
			type = own.type();
			name = own.name();
			desc = own.desc();
			max = own.max();
			roles.putAll(own.roles());
			autoAccept = own.autoAccept();
		} else {
			GolemConfig.PartyFinder.Draft d = prefs.draft;
			type = DragonType.byId(d.type) == null ? DragonType.GDRAG : DragonType.byId(d.type);
			name = d.name.isEmpty() ? Component.translatable("golemaddons.pf.default_name", minecraft.getUser().getName()).getString() : d.name;
			desc = d.desc;
			max = Math.clamp(d.max, 1, 6);
			for (Role r : Role.values()) roles.put(r, Math.clamp(d.roles.getOrDefault(r.id(), 0), 0, 5));
			autoAccept = d.autoAccept;
		}
	}

	/** Which mode the screen is in (leading, member, neither) and whether it's connected. */
	private String modeKey() {
		if (feature == null) return "none";
		Party own = feature.leading();
		Party member = feature.memberOf();
		return (own == null ? "-" : own.id()) + ":" + (member == null ? "-" : member.id()) + ":" + feature.connection();
	}

	/**
	 * Everything the right-hand panel shows. Only a change here rebuilds the widgets, so other parties updating
	 * never take the focus away from a text box you're typing in.
	 */
	private String contentKey() {
		if (feature == null) return "";
		StringBuilder sb = new StringBuilder(modeKey());
		Party shown = feature.leading() != null ? feature.leading() : feature.memberOf();
		if (shown != null) {
			sb.append('|').append(shown.status()).append(shown.max());
			for (Member m : shown.members()) sb.append(',').append(m.uuid()).append(m.role());
		}
		for (JoinRequest r : feature.incoming()) sb.append(';').append(r.id());
		JoinRequest out = feature.outgoing();
		if (out != null) sb.append('>').append(out.id());
		return sb.toString();
	}

	@Override
	protected void build() {
		loadForm();
		modeKey = modeKey();
		seenContent = contentKey();
		lines.clear();
		wrapped = null;
		Party own = feature == null ? null : feature.leading();
		Party member = feature == null ? null : feature.memberOf();

		int contentW = maxContentWidth(600);
		boolean twoColumns = contentW >= 340;
		int left = (width - contentW) / 2;
		int leftW = twoColumns ? (contentW - PANEL_GAP) * 11 / 20 : contentW;
		rightX = twoColumns ? left + leftW + PANEL_GAP : left;
		rightW = twoColumns ? contentW - leftW - PANEL_GAP : contentW;
		int ix = left + PANEL_PAD + 2;
		int inner = leftW - 2 * PANEL_PAD - 2;

		// Roles go on one row when there's room for "Support: 5", else 3 + 2.
		int roleW1 = (inner - 4 * GAP) / 5;
		boolean oneRoleRow = roleW1 >= font.width("Support: 5") + 10;
		int partyH = PANEL_LABEL + 3 * (ROW + 2) + PANEL_PAD;
		int rolesH = PANEL_LABEL + (oneRoleRow ? ROW : 2 * ROW + 2) + PANEL_PAD;
		int optionsH = PANEL_LABEL + 2 * (ROW + 2) + PANEL_PAD;
		int leftH = partyH + 4 + rolesH;
		int available = height - HEADER_HEIGHT - FOOTER_HEIGHT - 12;
		int columnH = twoColumns ? Math.max(leftH + 14, Math.min(available - 6, 260)) : leftH;
		int top = HEADER_HEIGHT + 6 + Math.max(0, (available - columnH) / 2);
		int y = top;

		// Party settings: type, size, auto-accept; name; description.
		panels.add(new Panel(left, y, leftW, partyH, Component.translatable(own != null ? "golemaddons.pf.panel.edit" : "golemaddons.pf.panel.new")));
		int ry = y + PANEL_LABEL + 2;
		int smallW = Math.max(44, inner / 4);
		addRenderableWidget(CycleButton.builder((DragonType t) -> Component.literal(t.label).withColor(t.color & 0xFFFFFF), type)
				.withValues(DragonType.values())
				.displayOnlyValue()
				.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.pf.field.type")))
				.create(ix, ry, smallW, ROW, Component.translatable("golemaddons.pf.field.type"), (b, v) -> type = v));
		addRenderableWidget(CycleButton.builder((Integer n) -> Component.translatable("golemaddons.pf.field.max.value", n), max)
				.withValues(List.of(1, 2, 3, 4, 5, 6))
				.displayOnlyValue()
				.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.pf.field.max.tooltip")))
				.create(ix + smallW + GAP, ry, smallW, ROW, Component.translatable("golemaddons.pf.field.max"), (b, v) -> max = v));
		int autoX = ix + 2 * (smallW + GAP);
		addRenderableWidget(CycleButton.booleanBuilder(Component.translatable("golemaddons.pf.field.auto_accept.on").withColor(UiText.GREEN & 0xFFFFFF),
						Component.translatable("golemaddons.pf.field.auto_accept.off").withColor(UiText.GRAY & 0xFFFFFF), autoAccept)
				.displayOnlyValue()
				.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.pf.field.auto_accept.tooltip")))
				.create(autoX, ry, ix + inner - autoX, ROW, Component.translatable("golemaddons.pf.field.auto_accept"), (b, v) -> autoAccept = v));
		ry += ROW + 2;
		EditBox nameBox = new EditBox(font, ix, ry, inner, ROW, Component.translatable("golemaddons.pf.field.name"));
		nameBox.setMaxLength(PartyText.NAME_MAX);
		nameBox.setHint(Component.translatable("golemaddons.pf.field.name"));
		nameBox.setTooltip(Tooltip.create(Component.translatable("golemaddons.pf.field.name")));
		nameBox.setValue(name);
		nameBox.setResponder(v -> name = v);
		addRenderableWidget(nameBox);
		ry += ROW + 2;
		EditBox descBox = new EditBox(font, ix, ry, inner, ROW, Component.translatable("golemaddons.pf.field.desc"));
		descBox.setMaxLength(PartyText.DESC_MAX);
		descBox.setHint(Component.translatable("golemaddons.pf.field.desc.hint"));
		descBox.setTooltip(Tooltip.create(Component.translatable("golemaddons.pf.field.desc")));
		descBox.setValue(desc);
		descBox.setResponder(v -> desc = v);
		addRenderableWidget(descBox);
		y += partyH + 4;

		// Role slots wanted from others.
		panels.add(new Panel(left, y, leftW, rolesH, Component.translatable("golemaddons.pf.panel.roles")));
		ry = y + PANEL_LABEL + 2;
		List<Integer> counts = List.of(0, 1, 2, 3, 4, 5);
		Role[] all = Role.values();
		for (int i = 0; i < all.length; i++) {
			Role r = all[i];
			int perRow = oneRoleRow ? 5 : i < 3 ? 3 : 2;
			int col = oneRoleRow ? i : i < 3 ? i : i - 3;
			int w = (inner - (perRow - 1) * GAP) / perRow;
			int bx = ix + col * (w + GAP);
			int by = ry + (oneRoleRow || i < 3 ? 0 : ROW + 2);
			addRenderableWidget(CycleButton.builder((Integer n) -> Component.translatable(r.translationKey())
							.append(": " + n).withColor(n > 0 ? r.color & 0xFFFFFF : UiText.GRAY & 0xFFFFFF), roles.get(r))
					.withValues(counts)
					.displayOnlyValue()
					.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.pf.field.role.tooltip", PartyUi.role(r))))
					.create(bx, by, w, ROW, Component.translatable(r.translationKey()), (b, v) -> roles.put(r, v)));
		}
		y += rolesH;
		statusX = left;
		statusY = y + 4;
		statusW = leftW - 4;

		// Right column: requests and members (or the party you're in), then options.
		int rTop = twoColumns ? top : statusY + 14;
		int rBottom = twoColumns ? top + columnH : rTop + 200;
		int optionsY = rBottom - optionsH;
		int listBottom = optionsY - 4;
		int rix = rightX + PANEL_PAD + 2;
		int rInner = rightW - 2 * PANEL_PAD - 2;
		int ly = rTop + PANEL_LABEL + 3;
		int maxLy = listBottom - LINE;
		if (own != null) {
			panels.add(new Panel(rightX, rTop, rightW, listBottom - rTop, Component.translatable("golemaddons.pf.panel.members",
					own.size(), own.max())));
			// Requests first: they're what a leader has to act on.
			List<JoinRequest> requests = feature.incoming();
			if (!requests.isEmpty()) {
				lines.add(new Line(ly, null, Component.translatable("golemaddons.pf.requests", requests.size()).withColor(UiText.YELLOW & 0xFFFFFF)));
				ly += LINE;
				int shown = 0;
				for (JoinRequest r : requests) {
					if (ly > maxLy - LINE || shown == MAX_REQUESTS_SHOWN) break;
					lines.add(new Line(ly, r.uuid(), Component.literal(r.name()).append(" ").append(PartyUi.role(r.role()))));
					addRenderableWidget(Button.builder(Component.literal("✔"), b -> feature.respond(r.id(), true))
							.tooltip(Tooltip.create(Component.translatable("golemaddons.pf.accept.tooltip", r.name())))
							.bounds(rix + rInner - 31, ly - 2, 15, 12).build());
					addRenderableWidget(Button.builder(Component.literal("✖"), b -> feature.respond(r.id(), false))
							.tooltip(Tooltip.create(Component.translatable("golemaddons.pf.deny.tooltip", r.name())))
							.bounds(rix + rInner - 15, ly - 2, 15, 12).build());
					ly += LINE;
					shown++;
				}
				if (shown < requests.size()) {
					lines.add(new Line(ly, null, Component.translatable("golemaddons.pf.more", requests.size() - shown).withColor(UiText.GRAY & 0xFFFFFF)));
					ly += LINE;
				}
				ly += 3;
			}
			lines.add(new Line(ly, own.leaderId(), Component.literal("★ " + own.leaderName()).withColor(UiText.GOLD & 0xFFFFFF)));
			ly += LINE;
			int hidden = 0;
			for (Member m : own.members()) {
				if (ly > maxLy) {
					hidden++;
					continue;
				}
				lines.add(new Line(ly, m.uuid(), Component.literal(m.name()).append(" ").append(PartyUi.role(m.role()))));
				addRenderableWidget(Button.builder(Component.translatable("golemaddons.pf.kick"), b -> feature.kick(m.uuid()))
						.tooltip(Tooltip.create(Component.translatable("golemaddons.pf.kick.tooltip", m.name())))
						.bounds(rix + rInner - 30, ly - 2, 30, 12).build());
				ly += LINE;
			}
			if (hidden > 0) {
				lines.add(new Line(ly, null, Component.translatable("golemaddons.pf.more", hidden).withColor(UiText.GRAY & 0xFFFFFF)));
			} else if (requests.isEmpty() && ly <= maxLy) {
				lines.add(new Line(ly + 2, null, Component.translatable(own.autoAccept() ? "golemaddons.pf.requests.auto"
						: "golemaddons.pf.requests.none").withColor(UiText.DARK_GRAY & 0xFFFFFF)));
			}
		} else if (member != null) {
			panels.add(new Panel(rightX, rTop, rightW, listBottom - rTop, Component.translatable("golemaddons.pf.panel.member_of", member.leaderName())));
			lines.add(new Line(ly, null, Component.literal(PartyUi.fit(font, member.name(), rInner - 40)).withColor(UiText.WHITE & 0xFFFFFF)
					.append(Component.literal(" " + member.type().label).withColor(member.type().color & 0xFFFFFF))));
			ly += LINE;
			lines.add(new Line(ly, member.leaderId(), Component.literal("★ " + member.leaderName()).withColor(UiText.GOLD & 0xFFFFFF)));
			ly += LINE;
			for (Member m : member.members()) {
				if (ly > maxLy - ROW) break;
				lines.add(new Line(ly, m.uuid(), Component.literal(m.name()).append(" ").append(PartyUi.role(m.role()))));
				ly += LINE;
			}
			addRenderableWidget(Button.builder(Component.translatable("golemaddons.pf.leave"), b -> feature.leave())
					.tooltip(Tooltip.create(Component.translatable("golemaddons.pf.leave.tooltip")))
					.bounds(rix, listBottom - ROW - PANEL_PAD + 2, rInner, ROW).build());
		} else {
			panels.add(new Panel(rightX, rTop, rightW, listBottom - rTop, Component.translatable("golemaddons.pf.panel.status")));
			JoinRequest out = feature == null ? null : feature.outgoing();
			if (out != null) {
				Party p = feature.party(out.party());
				lines.add(new Line(ly, p == null ? null : p.leaderId(), Component.translatable("golemaddons.pf.waiting",
						p == null ? "?" : p.leaderName())));
				addRenderableWidget(Button.builder(Component.translatable("golemaddons.pf.cancel"), b -> feature.cancelRequest())
						.tooltip(Tooltip.create(Component.translatable("golemaddons.pf.cancel.tooltip")))
						.bounds(rix, listBottom - ROW - PANEL_PAD + 2, rInner, ROW).build());
			} else {
				wrapped = Component.translatable("golemaddons.pf.hint.create");
				wrappedY = ly;
			}
		}

		// Options (saved locally).
		panels.add(new Panel(rightX, optionsY, rightW, optionsH, Component.translatable("golemaddons.pf.panel.options")));
		ry = optionsY + PANEL_LABEL + 2;
		int optW = (rInner - 2 * GAP) / 3;
		addRenderableWidget(toggle(rix, ry, optW, "golemaddons.pf.opt.toasts", prefs.toasts, v -> prefs.toasts = v, null));
		addRenderableWidget(toggle(rix + optW + GAP, ry, optW, "golemaddons.pf.opt.chat", prefs.chatMessages, v -> prefs.chatMessages = v,
				"golemaddons.pf.opt.chat.tooltip"));
		addRenderableWidget(toggle(rix + 2 * (optW + GAP), ry, rInner - 2 * (optW + GAP), "golemaddons.pf.opt.sound", prefs.sound,
				v -> prefs.sound = v, null));
		ry += ROW + 2;
		EditBox command = new EditBox(font, rix, ry, rInner, ROW, Component.translatable("golemaddons.pf.opt.command"));
		command.setMaxLength(48);
		command.setValue(prefs.inviteCommand);
		command.setTooltip(Tooltip.create(Component.translatable("golemaddons.pf.opt.command.tooltip")));
		command.setResponder(v -> {
			if (PartyText.validTemplate(v)) prefs.inviteCommand = v.strip();
			command.setTextColor(PartyText.validTemplate(v) ? 0xFFE0E0E0 : UiText.RED);
		});
		addRenderableWidget(command);

		// Footer
		if (own != null) {
			primary = Button.builder(Component.translatable("golemaddons.pf.save"), b -> submit(true)).build();
			disbandButton = Button.builder(Component.translatable("golemaddons.pf.disband"), b -> disband())
					.tooltip(Tooltip.create(Component.translatable("golemaddons.pf.disband.tooltip"))).build();
			CycleButton<Boolean> progress = CycleButton.booleanBuilder(
							Component.translatable("golemaddons.pf.status.in_progress").withColor(UiText.GOLD & 0xFFFFFF),
							Component.translatable("golemaddons.pf.status.open").withColor(UiText.GREEN & 0xFFFFFF),
							own.status() == PartyModels.Status.IN_PROGRESS)
					.withTooltip(v -> Tooltip.create(Component.translatable("golemaddons.pf.in_progress.tooltip")))
					.create(0, 0, 90, 20, Component.translatable("golemaddons.pf.field.status"), (b, v) -> feature.setInProgress(v));
			addFooter(primary, progress, disbandButton, Button.builder(Component.translatable("golemaddons.pf.back"), b -> onClose()).build());
		} else {
			primary = Button.builder(Component.translatable("golemaddons.pf.create"), b -> submit(false))
					.tooltip(Tooltip.create(Component.translatable(member != null ? "golemaddons.pf.create.leaves" : "golemaddons.pf.create.tooltip")))
					.build();
			disbandButton = null;
			addFooter(primary, Button.builder(Component.translatable("golemaddons.pf.back"), b -> onClose()).build());
		}
		updateButtons();
	}

	private static CycleButton<Boolean> toggle(int x, int y, int w, String key, boolean value, java.util.function.Consumer<Boolean> set,
			String tooltip) {
		Component label = Component.translatable(key);
		CycleButton.Builder<Boolean> b = CycleButton.booleanBuilder(label.copy().withColor(UiText.GREEN & 0xFFFFFF),
				label.copy().withColor(UiText.DARK_GRAY & 0xFFFFFF).withStyle(net.minecraft.ChatFormatting.STRIKETHROUGH), value)
				.displayOnlyValue();
		if (tooltip != null) b = b.withTooltip(v -> Tooltip.create(Component.translatable(tooltip)));
		return b.create(x, y, w, ROW, label, (btn, v) -> set.accept(v));
	}

	private Settings settings() {
		return new Settings(type, PartyText.check(name, PartyText.NAME_MAX, false).text(),
				PartyText.check(desc, PartyText.DESC_MAX, true).text(), max, new EnumMap<>(roles), autoAccept);
	}

	/** The first thing wrong with the form, or null if it can be sent. */
	private Component formProblem() {
		PartyText.Result n = PartyText.check(name, PartyText.NAME_MAX, false);
		if (!n.valid()) return Component.translatable("golemaddons.pf.text." + n.problem().name().toLowerCase(Locale.ROOT),
				Component.translatable("golemaddons.pf.field.name"));
		PartyText.Result d = PartyText.check(desc, PartyText.DESC_MAX, true);
		if (!d.valid()) return Component.translatable("golemaddons.pf.text." + d.problem().name().toLowerCase(Locale.ROOT),
				Component.translatable("golemaddons.pf.field.desc"));
		int total = 0;
		for (int v : roles.values()) total += v;
		if (total > max - 1) return Component.translatable("golemaddons.pf.why.too_many_roles", total, max - 1);
		Party own = feature == null ? null : feature.leading();
		if (own != null && max < own.size()) return Component.translatable("golemaddons.pf.why.too_small", own.size());
		return null;
	}

	private void submit(boolean edit) {
		if (feature == null || formProblem() != null) return;
		Settings s = settings();
		saveDraftAndOptions();
		if (edit) feature.edit(s);
		else feature.create(s);
	}

	private void disband() {
		long now = Util.getMillis();
		if (now > disbandArmedUntil) {
			disbandArmedUntil = now + 3_000;
			disbandButton.setMessage(Component.translatable("golemaddons.pf.disband.confirm"));
			return;
		}
		disbandArmedUntil = 0;
		feature.disband();
	}

	/** Keeps the form as the draft for next time and saves the options, without touching anything else in the config. */
	private void saveDraftAndOptions() {
		GolemConfig next = GolemAddonsClient.config().get().copy();
		GolemConfig.PartyFinder p = next.partyFinder;
		p.toasts = prefs.toasts;
		p.chatMessages = prefs.chatMessages;
		p.sound = prefs.sound;
		p.inviteCommand = prefs.inviteCommand;
		if (type != null) {
			p.draft.type = type.id;
			p.draft.name = name;
			p.draft.desc = desc;
			p.draft.max = max;
			p.draft.roles.clear();
			for (Role r : Role.values()) if (roles.get(r) > 0) p.draft.roles.put(r.id(), roles.get(r));
			p.draft.autoAccept = autoAccept;
		}
		GolemAddonsClient.config().save(next);
	}

	private void updateButtons() {
		boolean online = feature != null && feature.connection() == PartyFinderFeature.Connection.ONLINE;
		if (primary != null) primary.active = online && formProblem() == null;
		if (disbandButton != null) {
			disbandButton.active = online;
			if (disbandArmedUntil != 0 && Util.getMillis() > disbandArmedUntil) {
				disbandArmedUntil = 0;
				disbandButton.setMessage(Component.translatable("golemaddons.pf.disband"));
			}
		}
	}

	@Override
	public void tick() {
		if (feature != null && (!modeKey().equals(modeKey) || !contentKey().equals(seenContent))) {
			if (!modeKey().equals(modeKey)) formFor = null; // became leader / left: reload the form for the new state
			rebuildWidgets();
		}
		updateButtons();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		int x = rightX + PANEL_PAD + 2;
		for (Line line : lines) {
			int tx = x;
			if (line.uuid() != null) {
				PartyUi.head(g, line.uuid(), x, line.y() - 1, 10);
				tx += 14;
			}
			g.text(font, line.text(), tx, line.y(), UiText.WHITE, false);
		}
		if (wrapped != null) g.textWithWordWrap(font, wrapped, x, wrappedY, rightW - 2 * PANEL_PAD - 4, UiText.GRAY);
		Component status = status();
		if (status != null) g.textWithWordWrap(font, status, statusX + 2, statusY, statusW, UiText.GRAY, false);
	}

	private Component status() {
		if (feature == null || !feature.enabled()) return Component.translatable("golemaddons.pf.empty.disabled").withColor(UiText.YELLOW & 0xFFFFFF);
		if (!feature.onServer()) return Component.translatable("golemaddons.pf.empty.no_server").withColor(UiText.YELLOW & 0xFFFFFF);
		Component problem = formProblem();
		if (problem != null) return problem.copy().withColor(UiText.RED & 0xFFFFFF);
		if (feature.connection() != PartyFinderFeature.Connection.ONLINE) {
			Component why = feature.problem();
			return (why != null ? why : Component.translatable("golemaddons.pf.conn.connecting")).copy().withColor(UiText.YELLOW & 0xFFFFFF);
		}
		Component last = feature.problem();
		return last == null ? null : last.copy().withColor(UiText.RED & 0xFFFFFF);
	}
}
