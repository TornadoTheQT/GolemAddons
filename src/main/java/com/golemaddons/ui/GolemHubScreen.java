package com.golemaddons.ui;

import com.golemaddons.GolemAddonsClient;
import com.golemaddons.autosell.RuleValidator;
import com.golemaddons.config.GolemConfig;
import com.golemaddons.cosmetics.CosmeticsFeature;
import com.golemaddons.dragon.DragonCatalog;
import java.util.Arrays;
import java.util.Random;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The GolemAddons main menu: an animated title, drifting particles and one card per feature.
 *
 * <p>Laid out on a fixed {@link HubCanvas} (640x360 design units) scaled to the window, so it fills the screen
 * the same way whatever the GUI scale. Add a feature by adding a card in {@link #init()}.
 */
public final class GolemHubScreen extends Screen {
	private static final String TITLE = "GolemAddons";
	private static final float TITLE_SCALE = 3.5f;
	private static final float TITLE_Y = 30f;
	private static final float CARD_Y = 116f;
	private static final float CARD_W = 100f;
	private static final float CARD_H = 172f;
	private static final float CARD_GAP = 6f;
	private static final int PARTICLES = 64;
	/** Number of feature cards; the gametest clicks them by position. */
	public static final int CARDS = 6;

	private final Screen parent;
	/** Kept across re-inits (resize, returning from a sub-screen) so the entrance plays once. */
	private final long openedAt = Util.getMillis();
	private final float[][] particles = new float[PARTICLES][];
	private final String version;
	private HubCanvas canvas;

	public GolemHubScreen(Screen parent) {
		super(Component.literal(TITLE));
		this.parent = parent;
		Random random = new Random(0x601E_A5L);
		for (int i = 0; i < PARTICLES; i++) {
			// x fraction, start phase, speed (canvas units/s), size (units), twinkle phase
			particles[i] = new float[] {random.nextFloat(), random.nextFloat(), 6f + random.nextFloat() * 16f,
					1f + random.nextInt(2), random.nextFloat() * 6.28f};
		}
		this.version = FabricLoader.getInstance().getModContainer(GolemAddonsClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	}

	@Override
	protected void init() {
		canvas = HubCanvas.of(minecraft, width, height);
		float x = (HubCanvas.WIDTH - (CARDS * CARD_W + (CARDS - 1) * CARD_GAP)) / 2f;

		addRenderableWidget(new HubCard(canvas, x, CARD_Y, CARD_W, CARD_H,
				Component.translatable("golemaddons.hub.mining"), Component.translatable("golemaddons.hub.mining.desc"),
				new ItemStack(Items.DIAMOND_PICKAXE), UiText.ACCENT_2, 0, openedAt, this::miningStatus,
				() -> minecraft.setScreen(new MiningAutoSellScreen(this))));
		x += CARD_W + CARD_GAP;
		addRenderableWidget(new HubCard(canvas, x, CARD_Y, CARD_W, CARD_H,
				Component.translatable("golemaddons.hub.dragon"), Component.translatable("golemaddons.hub.dragon.desc"),
				new ItemStack(Items.DRAGON_HEAD), UiText.ACCENT, 1, openedAt, this::dragonStatus,
				() -> minecraft.setScreen(new DragonSellerScreen(this))));
		x += CARD_W + CARD_GAP;
		addRenderableWidget(new HubCard(canvas, x, CARD_Y, CARD_W, CARD_H,
				Component.translatable("golemaddons.hub.settings"), Component.translatable("golemaddons.hub.settings.desc"),
				new ItemStack(Items.COMPARATOR), UiText.GOLD, 2, openedAt, this::settingsStatus,
				() -> minecraft.setScreen(new SellSettingsScreen(this))));
		x += CARD_W + CARD_GAP;
		addRenderableWidget(new HubCard(canvas, x, CARD_Y, CARD_W, CARD_H,
				Component.translatable("golemaddons.hub.cosmetics"), Component.translatable("golemaddons.hub.cosmetics.desc"),
				new ItemStack(Items.ELYTRA), UiText.ACCENT_2, 3, openedAt, this::cosmeticsStatus,
				() -> minecraft.setScreen(new CosmeticsScreen(this))));
		x += CARD_W + CARD_GAP;
		addRenderableWidget(new HubCard(canvas, x, CARD_Y, CARD_W, CARD_H,
				Component.translatable("golemaddons.hub.autoclicker"), Component.translatable("golemaddons.hub.autoclicker.desc"),
				new ItemStack(Items.GOLDEN_SWORD), UiText.GOLD, 4, openedAt, this::autoClickerStatus,
				() -> minecraft.setScreen(new AutoClickerScreen(this))));
		x += CARD_W + CARD_GAP;
		addRenderableWidget(new HubCard(canvas, x, CARD_Y, CARD_W, CARD_H,
				Component.translatable("golemaddons.hub.partyfinder"), Component.translatable("golemaddons.hub.partyfinder.desc"),
				new ItemStack(Items.DRAGON_EGG), UiText.ACCENT, 5, openedAt, this::partyFinderStatus,
				() -> minecraft.setScreen(new PartyFinderScreen(this))));

		addRenderableWidget(HubCard.button(canvas, (HubCanvas.WIDTH - 140) / 2f, 306f, 140f, 24f,
				Component.translatable("gui.done"), UiText.ACCENT, 6, openedAt, this::onClose));
	}

	private HubCard.Status miningStatus() {
		GolemConfig config = GolemAddonsClient.config().get();
		int total = config.miningAutoSell.rules.size();
		long on = config.miningAutoSell.rules.stream().filter(r -> r.enabled).count();
		return on > 0
				? new HubCard.Status(Component.translatable("golemaddons.hub.mining.status", on, total), UiText.GREEN)
				: new HubCard.Status(Component.translatable("golemaddons.hub.mining.status.none"), UiText.GRAY);
	}

	private HubCard.Status dragonStatus() {
		GolemConfig.DragonSeller s = GolemAddonsClient.config().get().dragonSeller;
		if (!s.enabled) {
			return new HubCard.Status(Component.translatable("golemaddons.hub.off"), UiText.GRAY);
		}
		long dragons = Arrays.stream(DragonCatalog.DragonType.values()).filter(s::sellsDragon).count();
		return new HubCard.Status(Component.translatable("golemaddons.hub.dragon.status", dragons,
				DragonCatalog.DragonType.values().length), UiText.ACCENT_2);
	}

	private HubCard.Status settingsStatus() {
		String pattern = GolemAddonsClient.config().get().selling.sellScreenTitlePattern;
		return RuleValidator.validateScreenTitlePattern(pattern).valid()
				? new HubCard.Status(Component.translatable("golemaddons.hub.settings.status.ok"), UiText.GREEN)
				: new HubCard.Status(Component.translatable("golemaddons.hub.settings.status.missing"), UiText.YELLOW);
	}

	private HubCard.Status autoClickerStatus() {
		GolemConfig.AutoClicker s = GolemAddonsClient.config().get().autoClicker;
		if (!s.enabled) {
			return new HubCard.Status(Component.translatable("golemaddons.hub.off"), UiText.GRAY);
		}
		if (GolemAddonsClient.autoClickerKey().isUnbound()) {
			return new HubCard.Status(Component.translatable("golemaddons.hub.autoclicker.no_key"), UiText.YELLOW);
		}
		return new HubCard.Status(Component.translatable("golemaddons.hub.autoclicker.status", s.minCps, s.maxCps), UiText.GREEN);
	}

	private HubCard.Status partyFinderStatus() {
		com.golemaddons.partyfinder.PartyFinderFeature f = com.golemaddons.partyfinder.PartyFinderFeature.instance();
		if (f == null || !f.enabled()) {
			return new HubCard.Status(Component.translatable("golemaddons.hub.off"), UiText.GRAY);
		}
		var own = f.leading();
		if (own != null) {
			return new HubCard.Status(Component.translatable("golemaddons.hub.partyfinder.leading", own.size(), own.max()), UiText.GREEN);
		}
		if (f.memberOf() != null) return new HubCard.Status(Component.translatable("golemaddons.hub.partyfinder.member"), UiText.GREEN);
		if (f.outgoing() != null) return new HubCard.Status(Component.translatable("golemaddons.hub.partyfinder.requested"), UiText.YELLOW);
		return new HubCard.Status(Component.translatable("golemaddons.hub.partyfinder.ready"), UiText.ACCENT_2);
	}

	private HubCard.Status cosmeticsStatus() {
		CosmeticsFeature feature = CosmeticsFeature.instance();
		if (feature == null || !feature.active()) {
			return new HubCard.Status(Component.translatable("golemaddons.hub.off"), UiText.GRAY);
		}
		return feature.paused()
				? new HubCard.Status(Component.translatable("golemaddons.hub.cosmetics.status.paused"), UiText.YELLOW)
				: new HubCard.Status(Component.translatable("golemaddons.hub.cosmetics.status.on"), UiText.GREEN);
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
		graphics.fill(0, 0, width, height, 0xA008070E);
		graphics.fillGradient(0, 0, width, height / 2, 0x38281A4A, 0x00000000);
		graphics.fillGradient(0, height / 2, width, height, 0x00000000, 0x3010302C);

		float t = elapsed();
		float fadeIn = Math.min(1f, t / 0.8f);
		float s = canvas.scale();
		for (int i = 0; i < PARTICLES; i++) {
			float[] p = particles[i];
			float travel = height + 12f;
			float y = height + 6f - ((p[1] * travel + t * p[2] * s) % travel);
			float x = p[0] * width;
			float twinkle = 0.35f + 0.35f * (float) Math.sin(t * 1.7f + p[4]);
			int color = UiText.withAlpha(i % 3 == 0 ? UiText.ACCENT_2 : UiText.ACCENT, twinkle * fadeIn * 0.7f);
			// Plain fill at scaled size: no per-particle matrix push/pop.
			int size = Math.max(1, Math.round(p[3] * s));
			int ix = Math.round(x);
			int iy = Math.round(y);
			graphics.fill(ix, iy, ix + size, iy + size, color);
		}
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

		graphics.text(font, "v" + version, 12, HubCanvas.HEIGHT - 16, UiText.DARK_GRAY);
		Component hint = Component.translatable("golemaddons.hub.hint", GolemAddonsClient.openMenuKeyName());
		graphics.text(font, hint, HubCanvas.WIDTH - 12 - font.width(hint), HubCanvas.HEIGHT - 16, UiText.DARK_GRAY);
		graphics.pose().popMatrix();
	}
}
