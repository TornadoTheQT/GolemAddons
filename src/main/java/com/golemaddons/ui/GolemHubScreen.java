package com.golemaddons.ui;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.update.Release;
import com.golemaddons.update.UpdateChecker;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The GolemAddons main menu: an animated title, drifting particles and one card per category (Selling, QoL,
 * Visuals) plus the Party Finder. Category cards open a {@link CategoryHubScreen} with that category's features.
 *
 * <p>Laid out on a fixed {@link HubCanvas} (640x360 design units) scaled to the window, so it fills the screen
 * the same way whatever the GUI scale. Add a feature by adding a tile to its category in {@link #init()}.
 */
public final class GolemHubScreen extends Screen {
	private static final String TITLE = "GolemAddons";
	private static final float TITLE_SCALE = 3.5f;
	private static final float TITLE_Y = 30f;
	private static final float CARD_Y = 116f;
	private static final float CARD_W = 114f;
	private static final float CARD_H = 172f;
	private static final float CARD_GAP = 8f;
	/** Number of top-level cards. */
	public static final int CARDS = 5;

	private final Screen parent;
	/** Kept across re-inits (resize, returning from a sub-screen) so the entrance plays once. */
	private final long openedAt = Util.getMillis();
	private final HubBackdrop backdrop = new HubBackdrop();
	private final String version;
	private HubCanvas canvas;
	private static final float FOOTER_Y = HubCanvas.HEIGHT - 16;
	private static final float ICON = 13f;
	private float hintX;
	private float versionX;
	private float iconX;
	private String shownStatus = "";
	private long statusChangedAt;

	public GolemHubScreen(Screen parent) {
		super(Component.literal(TITLE));
		this.parent = parent;
		this.version = FabricLoader.getInstance().getModContainer(GolemAddonsClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	}

	@Override
	protected void init() {
		canvas = HubCanvas.of(minecraft, width, height);
		float x = (HubCanvas.WIDTH - (CARDS * CARD_W + (CARDS - 1) * CARD_GAP)) / 2f;

		addRenderableWidget(new HubCard(canvas, x, CARD_Y, CARD_W, CARD_H,
				Component.translatable("golemaddons.hub.selling"), Component.translatable("golemaddons.hub.selling.desc"),
				new ItemStack(Items.EMERALD), UiText.GOLD, 0, openedAt, HubStatus::selling, () -> minecraft.setScreen(selling())));
		x += CARD_W + CARD_GAP;
		addRenderableWidget(new HubCard(canvas, x, CARD_Y, CARD_W, CARD_H,
				Component.translatable("golemaddons.hub.qol"), Component.translatable("golemaddons.hub.qol.desc"),
				new ItemStack(Items.CLOCK), UiText.ACCENT_2, 1, openedAt, HubStatus::qol, () -> minecraft.setScreen(qol())));
		x += CARD_W + CARD_GAP;
		addRenderableWidget(new HubCard(canvas, x, CARD_Y, CARD_W, CARD_H,
				Component.translatable("golemaddons.hub.combat"), Component.translatable("golemaddons.hub.combat.desc"),
				new ItemStack(Items.ENDER_EYE), UiText.RED, 2, openedAt, HubStatus::combat, () -> minecraft.setScreen(combat())));
		x += CARD_W + CARD_GAP;
		addRenderableWidget(new HubCard(canvas, x, CARD_Y, CARD_W, CARD_H,
				Component.translatable("golemaddons.hub.visuals"), Component.translatable("golemaddons.hub.visuals.desc"),
				new ItemStack(Items.ELYTRA), UiText.ACCENT, 3, openedAt, HubStatus::visuals, () -> minecraft.setScreen(visuals())));
		x += CARD_W + CARD_GAP;
		addRenderableWidget(new HubCard(canvas, x, CARD_Y, CARD_W, CARD_H,
				Component.translatable("golemaddons.hub.partyfinder"), Component.translatable("golemaddons.hub.partyfinder.desc"),
				new ItemStack(Items.DRAGON_EGG), UiText.ACCENT, 4, openedAt, HubStatus::partyFinder,
				() -> minecraft.setScreen(new PartyFinderScreen(this))));

		addRenderableWidget(HubCard.button(canvas, (HubCanvas.WIDTH - 140) / 2f, 306f, 140f, 24f,
				Component.translatable("gui.done"), UiText.ACCENT, 5, openedAt, this::onClose));

		// Footer, right to left: "K opens this menu", the version, the refresh button, then what the updater is doing.
		Component hint = Component.translatable("golemaddons.hub.hint", GolemAddonsClient.openMenuKeyName());
		hintX = HubCanvas.WIDTH - 12 - font.width(hint);
		versionX = hintX - 10 - (font.width(versionText()) + 8);
		iconX = versionX - 6 - ICON;
		UpdateIcon icon = new UpdateIcon(canvas, iconX, FOOTER_Y - 3, ICON, () -> minecraft.setScreen(new UpdatesScreen(this)));
		icon.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("golemaddons.hub.update.tooltip")));
		addRenderableWidget(icon);
	}

	private Component versionText() {
		return Component.translatable("golemaddons.hub.version", version);
	}

	/** One short line about the updater, or null when there's nothing worth saying. Shown left of the refresh button. */
	private Component updateStatus() {
		UpdateChecker checker = UpdateChecker.instance();
		Release latest = checker.latest();
		Release newer = checker.newerRelease();
		return switch (checker.state()) {
			case IDLE -> null;
			case CHECKING -> Component.translatable("golemaddons.hub.update.checking");
			case UP_TO_DATE -> Component.translatable("golemaddons.hub.update.up_to_date");
			case AVAILABLE -> newer == null ? null : checker.installUnavailable() != null || newer.sha256() == null
					? Component.translatable("golemaddons.hub.update.available_manual", newer.version())
					: Component.translatable("golemaddons.hub.update.available", newer.version());
			case DOWNLOADING -> Component.translatable("golemaddons.hub.update.downloading",
					latest == null ? 0 : Math.min(100, checker.downloaded() * 100 / Math.max(1, latest.size())));
			case STAGED -> Component.translatable("golemaddons.hub.update.staged", latest == null ? "" : latest.version());
			case ERROR -> Component.translatable("golemaddons.hub.update.error");
		};
	}

	private int updateStatusColor() {
		return switch (UpdateChecker.instance().state()) {
			case AVAILABLE, STAGED -> UiText.GREEN;
			case ERROR -> UiText.YELLOW;
			default -> UiText.GRAY;
		};
	}

	/** Fades the status line in when it changes, and fades "up to date" / errors out after a while. */
	private float updateStatusAlpha(Component status) {
		long now = Util.getMillis();
		String key = status == null ? "" : status.getString();
		if (!key.equals(shownStatus)) {
			shownStatus = key;
			statusChangedAt = now;
		}
		float in = UiAnim.easeOutCubic((now - statusChangedAt) / 250f);
		UpdateChecker.State state = UpdateChecker.instance().state();
		boolean transient_ = state == UpdateChecker.State.UP_TO_DATE || state == UpdateChecker.State.ERROR;
		float out = transient_ ? 1f - Math.clamp((now - statusChangedAt - 5000) / 600f, 0f, 1f) : 1f;
		return in * out;
	}

	private Screen selling() {
		return new CategoryHubScreen(this, Component.translatable("golemaddons.hub.selling"), UiText.GOLD, List.of(
				new CategoryHubScreen.Tile("golemaddons.hub.mining", new ItemStack(Items.DIAMOND_PICKAXE), UiText.ACCENT_2,
						HubStatus::mining, MiningAutoSellScreen::new),
				new CategoryHubScreen.Tile("golemaddons.hub.dragon", new ItemStack(Items.DRAGON_HEAD), UiText.ACCENT,
						HubStatus::dragon, DragonSellerScreen::new),
				new CategoryHubScreen.Tile("golemaddons.hub.settings", new ItemStack(Items.COMPARATOR), UiText.GOLD,
						HubStatus::sellSettings, SellSettingsScreen::new)));
	}

	private Screen qol() {
		return new CategoryHubScreen(this, Component.translatable("golemaddons.hub.qol"), UiText.ACCENT_2, List.of(
				new CategoryHubScreen.Tile("golemaddons.hub.autoclicker", new ItemStack(Items.GOLDEN_SWORD), UiText.GOLD,
						HubStatus::autoClicker, AutoClickerScreen::new),
				new CategoryHubScreen.Tile("golemaddons.hub.camera", new ItemStack(Items.SPYGLASS), UiText.ACCENT_2,
						HubStatus::camera, CameraScreen::new),
				new CategoryHubScreen.Tile("golemaddons.hub.terminals", new ItemStack(Items.LIME_STAINED_GLASS_PANE), UiText.ACCENT,
						HubStatus::terminals, TerminalsScreen::new),
				new CategoryHubScreen.Tile("golemaddons.hub.afk", new ItemStack(Items.RED_BED), UiText.GOLD,
						HubStatus::afk, AfkScreen::new)));
	}

	private Screen combat() {
		return new CategoryHubScreen(this, Component.translatable("golemaddons.hub.combat"), UiText.RED, List.of(
				new CategoryHubScreen.Tile("golemaddons.hub.riftborn", new ItemStack(Items.ENDER_EYE), UiText.ACCENT,
						HubStatus::riftborn, RiftbornScreen::new),
				new CategoryHubScreen.Tile("golemaddons.hub.boneswap", new ItemStack(Items.BONE), UiText.GOLD,
						HubStatus::boneSwap, BoneSwapScreen::new)));
	}

	private Screen visuals() {
		return new CategoryHubScreen(this, Component.translatable("golemaddons.hub.visuals"), UiText.ACCENT, List.of(
				new CategoryHubScreen.Tile("golemaddons.hub.cosmetics", new ItemStack(Items.ELYTRA), UiText.ACCENT_2,
						HubStatus::cosmetics, CosmeticsScreen::new),
				new CategoryHubScreen.Tile("golemaddons.hub.animations", new ItemStack(Items.DIAMOND_SWORD), UiText.ACCENT,
						HubStatus::animations, AnimationsScreen::new),
				new CategoryHubScreen.Tile("golemaddons.hub.hitboxes", new ItemStack(Items.END_CRYSTAL), UiText.GOLD,
						HubStatus::hitboxes, parent -> {
							toggleHitboxes();
							return null;
						})));
	}

	/** The hitbox tile is a toggle: saved right away, like the hitbox key. */
	private static void toggleHitboxes() {
		GolemConfig next = GolemAddonsClient.config().get().copy();
		next.render.dragonHitboxes = !next.render.dragonHitboxes;
		GolemAddonsClient.config().save(next);
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

		// Title: letters drop in one by one, bob gently, and a highlight sweeps across them.
		int titleWidth = font.width(TITLE);
		float x = cx - titleWidth * TITLE_SCALE / 2f;
		float sweep = (t * 6f) % (TITLE.length() + 12f) - 6f;
		for (int i = 0; i < TITLE.length(); i++) {
			String letter = String.valueOf(TITLE.charAt(i));
			float intro = Math.clamp((t - 0.05f * i) / 0.3f, 0f, 1f);
			if (intro > 0f) {
				float bob = (float) Math.sin(t * 2.2f + i * 0.5f) * 1.6f;
				float drop = (1f - intro) * -12f;
				float shine = Math.max(0f, 1f - Math.abs(i - sweep) / 2.5f);
				int base = UiText.lerpColor(0xFFEDE9FF, UiText.ACCENT, i / (float) TITLE.length() * 0.6f);
				int color = UiText.withAlpha(UiText.lerpColor(base, UiText.ACCENT_2, shine), intro);
				graphics.pose().pushMatrix();
				graphics.pose().translate(x, TITLE_Y + bob + drop);
				graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
				graphics.text(font, letter, 0, 0, color, true);
				graphics.pose().popMatrix();
			}
			x += font.width(letter) * TITLE_SCALE;
		}

		// Underline grows out from the centre, then the subtitle fades in.
		float line = Math.clamp((t - 0.35f) / 0.5f, 0f, 1f);
		line = 1f - (1f - line) * (1f - line);
		int half = Math.round(line * (titleWidth * TITLE_SCALE / 2f + 16f));
		int lineY = Math.round(TITLE_Y + 9 * TITLE_SCALE + 8);
		int icx = Math.round(cx);
		graphics.fill(icx - half, lineY, icx + half, lineY + 2, UiText.withAlpha(UiText.ACCENT, 0.9f * line));
		graphics.fill(icx - half / 2, lineY + 2, icx + half / 2, lineY + 3, UiText.withAlpha(UiText.ACCENT_2, 0.5f * line));
		float sub = Math.clamp((t - 0.6f) / 0.4f, 0f, 1f);
		if (sub > 0.02f) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(cx, lineY + 10f);
			graphics.pose().scale(1.25f, 1.25f);
			graphics.centeredText(font, Component.translatable("golemaddons.hub.subtitle"), 0, 0, UiText.withAlpha(UiText.GRAY, sub));
			graphics.pose().popMatrix();
		}

		Component hint = Component.translatable("golemaddons.hub.hint", GolemAddonsClient.openMenuKeyName());
		int fy = Math.round(FOOTER_Y);
		graphics.text(font, hint, Math.round(hintX), fy, UiText.DARK_GRAY);
		// Version pill.
		Component versionText = versionText();
		int vx = Math.round(versionX);
		int vw = font.width(versionText) + 8;
		graphics.fill(vx, fy - 3, vx + vw, fy + 10, 0x90141220);
		graphics.outline(vx, fy - 3, vw, 13, UiText.withAlpha(UiText.ACCENT, 0.6f));
		graphics.text(font, versionText, vx + 4, fy, UiText.GRAY);
		// Updater status, left of the refresh button.
		Component status = updateStatus();
		float alpha = updateStatusAlpha(status);
		if (status != null && alpha > 0.02f) {
			int sx = Math.round(iconX - 6 - font.width(status) + (1f - alpha) * 4f);
			graphics.text(font, status, sx, fy, UiText.withAlpha(updateStatusColor(), alpha));
		}
		graphics.pose().popMatrix();
	}
}
