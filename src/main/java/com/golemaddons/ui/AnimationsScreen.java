package com.golemaddons.ui;

import com.golemaddons.animation.AnimationCodec;
import com.golemaddons.animation.HeldItemAnimation;
import com.golemaddons.animation.HeldItemPreviewState;
import com.golemaddons.animation.HeldItemTransform;
import com.golemaddons.config.GolemConfig;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Held item animation editor: a live first-person preview on the left, sliders or presets on the right.
 * Edits a working copy like every settings screen; Save applies it.
 */
final class AnimationsScreen extends EditorScreen {
	private static final int ROW = 20;
	private static final int TAB_H = 18;
	private static final int PRESET_ROW = 14;
	/** How long (ticks) the preview rests between looped swings. */
	private static final float LOOP_PAUSE_TICKS = 9f;
	private static final int DEFAULT_SWING_TICKS = 6;
	private static final long MESSAGE_MS = 4000;

	private enum Tab { ADJUST, OPTIONS, PRESETS }

	private static Tab tab = Tab.ADJUST;

	private int previewX0;
	private int previewY0;
	private int previewX1;
	private int previewY1;
	private boolean loopSwing = true;
	/** When the preview's swing started (ms); it plays over the same number of ticks as the in-game one. */
	private long swingStartedAt = Util.getMillis();
	/** Entrance timing: the whole screen once, and the right-hand side again when switching tabs. */
	private final long openedAt = Util.getMillis();
	private final PreviewBackdrop backdrop = new PreviewBackdrop();
	private long tabShownAt = openedAt;
	/** Where the tab highlight starts after a rebuild, so it slides from the old tab to the new one. */
	private float tabSlideFrom = tab.ordinal();

	private EditBox nameBox;
	private String nameDraft = "";
	private int selected = -1;
	private boolean confirmDelete;
	private int listX;
	private int listY;
	private int listW;
	private int listRows;
	private int scroll;

	private Component message;
	private int messageColor;
	private long messageAt;

	AnimationsScreen(Screen parent) {
		super(Component.translatable("golemaddons.animations.title"), parent);
	}

	private GolemConfig.Animations settings() {
		return working.animations;
	}

	private HeldItemAnimation current() {
		return working.animations.current;
	}

	@Override
	protected void build() {
		int top = HEADER_HEIGHT + 6;
		int bottom = height - FOOTER_HEIGHT - 16; // room for the status line above the footer
		int totalW = maxContentWidth(640);
		int x0 = (width - totalW) / 2;
		int previewW = Math.round(totalW * 0.4f);
		int rightX = x0 + previewW + PANEL_GAP;
		int rightW = totalW - previewW - PANEL_GAP;

		// Preview (left)
		panels.add(new Panel(x0, top, previewW, bottom - top, Component.translatable("golemaddons.animations.preview")));
		// The preview has the game window's shape, so the item sits where it does on screen in game.
		int boxX0 = x0 + 4;
		int boxY0 = top + PANEL_LABEL + 2;
		int boxW = previewW - 8;
		int boxH = bottom - ROW - 8 - boxY0;
		float aspect = minecraft.getWindow().getWidth() / (float) Math.max(1, minecraft.getWindow().getHeight());
		int w = Math.min(boxW, Math.round(boxH * aspect));
		int h = Math.min(boxH, Math.round(w / aspect));
		previewX0 = boxX0 + (boxW - w) / 2;
		previewY0 = boxY0 + (boxH - h) / 2;
		previewX1 = previewX0 + w;
		previewY1 = previewY0 + h;
		int half = (previewW - 8 - GAP * 2) / 2;
		addRenderableWidget(new GolemButton(x0 + 4, bottom - ROW - 4, half, ROW, Component.translatable("golemaddons.animations.swing"),
				UiText.ACCENT_2, openedAt + 120, this::swingNow));
		addRenderableWidget(new GolemToggle(x0 + previewW - 4 - half, bottom - ROW - 4, half, ROW,
				Component.translatable("golemaddons.animations.loop"), loopSwing, UiText.ACCENT_2, openedAt + 160, v -> {
					loopSwing = v;
					swingNow();
				}));

		// Tabs (right)
		addRenderableWidget(new GolemSegmented(rightX, top, rightW, TAB_H, Component.translatable("golemaddons.animations.tabs"),
				List.of(Component.translatable("golemaddons.animations.tab.adjust"),
						Component.translatable("golemaddons.animations.tab.options"),
						Component.translatable("golemaddons.animations.tab.presets", settings().presets.size())),
				tab.ordinal(), UiText.ACCENT, openedAt + 40, i -> switchTab(Tab.values()[i])).hideLabel()).slideFrom(tabSlideFrom);
		tabSlideFrom = tab.ordinal();
		int panelY = top + TAB_H + 4;
		if (tab == Tab.ADJUST) {
			buildAdjust(rightX, panelY, rightW, bottom - panelY);
		} else if (tab == Tab.OPTIONS) {
			buildOptions(rightX, panelY, rightW, bottom - panelY);
		} else {
			buildPresets(rightX, panelY, rightW, bottom - panelY);
		}

		GolemButton reset = new GolemButton(0, 0, 0, ROW, Component.translatable("golemaddons.animations.reset"), UiText.GOLD,
				openedAt + 200, () -> {
					current().set(new HeldItemAnimation());
					rebuildWidgets();
				});
		reset.setTooltip(Tooltip.create(Component.translatable("golemaddons.animations.reset.tooltip")));
		addFooter(reset,
				new GolemButton(0, 0, 0, ROW, Component.translatable("golemaddons.config.save"), UiText.GREEN, openedAt + 240, this::save),
				new GolemButton(0, 0, 0, ROW, Component.translatable("golemaddons.config.cancel"), UiText.ACCENT, openedAt + 280, this::onClose));
	}

	private void switchTab(Tab next) {
		tab = next;
		tabShownAt = Util.getMillis();
		confirmDelete = false;
		rebuildWidgets();
	}

	private void buildAdjust(int x, int y, int w, int h) {
		panels.add(new Panel(x, y, w, h, Component.translatable("golemaddons.animations.panel.adjust")));
		int ix = x + PANEL_PAD + 2;
		int inner = w - 2 * PANEL_PAD - 2;
		int rowY = y + PANEL_LABEL + 2;
		int half = (inner - GAP * 2) / 2;
		addRenderableWidget(new GolemToggle(ix, rowY, half, ROW, Component.translatable("golemaddons.animations.enabled"),
				settings().enabled, UiText.GREEN, tabShownAt + 40, v -> settings().enabled = v));
		GolemSegmented mode = new GolemSegmented(ix + inner - half, rowY, half, ROW, Component.translatable("golemaddons.animations.swing_mode"),
				List.of(Component.translatable("golemaddons.animations.swing_mode.full"), Component.translatable("golemaddons.animations.swing_mode.pivot")),
				current().swingTranslation ? 0 : 1, UiText.ACCENT_2, tabShownAt + 80, i -> {
					current().swingTranslation = i == 0;
					swingNow();
				});
		mode.setTooltip(Tooltip.create(Component.translatable("golemaddons.animations.swing_mode.tooltip")));
		addRenderableWidget(mode);
		rowY += ROW + 6;
		sliderIndex = 0;

		HeldItemAnimation a = current();
		int colW = (inner - GAP * 4) / 2;
		int rowH = GolemSlider.HEIGHT + 4;
		// Left column: position and size. Right column: rotation and swing speed. (Odin's settings and ranges.)
		slider(ix, rowY, colW, "x", HeldItemAnimation.MIN_X, HeldItemAnimation.MAX_X, 0.05, 0, () -> a.x, v -> a.x = (float) v, AnimationsScreen::plain, UiText.ACCENT);
		slider(ix, rowY + rowH, colW, "y", HeldItemAnimation.MIN_Y, HeldItemAnimation.MAX_Y, 0.05, 0, () -> a.y, v -> a.y = (float) v, AnimationsScreen::plain, UiText.ACCENT);
		slider(ix, rowY + 2 * rowH, colW, "z", HeldItemAnimation.MIN_Z, HeldItemAnimation.MAX_Z, 0.05, 0, () -> a.z, v -> a.z = (float) v, AnimationsScreen::plain, UiText.ACCENT);
		slider(ix, rowY + 3 * rowH, colW, "scale", HeldItemAnimation.MIN_SCALE, HeldItemAnimation.MAX_SCALE, 0.05, 1, () -> a.scale, v -> a.scale = (float) v, AnimationsScreen::times, UiText.GOLD);
		int rx = ix + inner - colW;
		slider(rx, rowY, colW, "rot_x", HeldItemAnimation.MIN_ROTATION, HeldItemAnimation.MAX_ROTATION, 1, 0, () -> a.rotX, v -> a.rotX = (float) v, AnimationsScreen::degrees, UiText.ACCENT_2);
		slider(rx, rowY + rowH, colW, "rot_y", HeldItemAnimation.MIN_ROTATION, HeldItemAnimation.MAX_ROTATION, 1, 0, () -> a.rotY, v -> a.rotY = (float) v, AnimationsScreen::degrees, UiText.ACCENT_2);
		slider(rx, rowY + 2 * rowH, colW, "rot_z", HeldItemAnimation.MIN_ROTATION, HeldItemAnimation.MAX_ROTATION, 1, 0, () -> a.rotZ, v -> a.rotZ = (float) v, AnimationsScreen::degrees, UiText.ACCENT_2);
		slider(rx, rowY + 3 * rowH, colW, "swing_speed", HeldItemAnimation.MIN_SWING_SPEED, HeldItemAnimation.MAX_SWING_SPEED, 0.05, 1, () -> a.swingSpeed, v -> a.swingSpeed = (float) v, AnimationsScreen::times, UiText.GOLD);
	}

	private void buildOptions(int x, int y, int w, int h) {
		panels.add(new Panel(x, y, w, h, Component.translatable("golemaddons.animations.panel.options")));
		int ix = x + PANEL_PAD + 2;
		int inner = w - 2 * PANEL_PAD - 2;
		int rowY = y + PANEL_LABEL + 2;
		HeldItemAnimation a = current();
		option(ix, rowY, inner, "scale_swing", a.scaleSwing, v -> a.scaleSwing = v, 40);
		option(ix, rowY + ROW + 4, inner, "ignore_haste", a.ignoreHaste, v -> a.ignoreHaste = v, 80);
		option(ix, rowY + 2 * (ROW + 4), inner, "no_equip_reset", a.noEquipReset, v -> a.noEquipReset = v, 120);
	}

	private void option(int x, int y, int w, String key, boolean value, java.util.function.Consumer<Boolean> set, int delay) {
		GolemToggle toggle = new GolemToggle(x, y, w, ROW, Component.translatable("golemaddons.animations." + key), value,
				UiText.ACCENT_2, tabShownAt + delay, set);
		toggle.setTooltip(Tooltip.create(Component.translatable("golemaddons.animations." + key + ".tooltip")));
		addRenderableWidget(toggle);
	}

	private void slider(int x, int y, int w, String key, double min, double max, double step, double def, DoubleSupplier get,
			DoubleConsumer set, java.util.function.DoubleFunction<String> format, int accent) {
		GolemSlider slider = new GolemSlider(x, y, w, Component.translatable("golemaddons.animations." + key), min, max, step, def,
				get, set, format, accent).appearAt(tabShownAt + 110 + 30L * sliderIndex++);
		slider.setTooltip(Tooltip.create(Component.translatable("golemaddons.animations.slider.tooltip")));
		addRenderableWidget(slider);
	}

	private int sliderIndex;

	private static String plain(double v) {
		return String.format(Locale.ROOT, "%+.2f", v);
	}

	private static String degrees(double v) {
		return String.format(Locale.ROOT, "%+.0f°", v);
	}

	private static String times(double v) {
		return String.format(Locale.ROOT, "%.2f×", v);
	}

	private void buildPresets(int x, int y, int w, int h) {
		panels.add(new Panel(x, y, w, h, Component.translatable("golemaddons.animations.panel.presets")));
		int ix = x + PANEL_PAD + 2;
		int inner = w - 2 * PANEL_PAD - 2;
		int rowY = y + PANEL_LABEL + 2;
		List<GolemConfig.Animations.Preset> presets = settings().presets;
		if (selected >= presets.size()) selected = -1;

		nameBox = addRenderableWidget(new GolemEditBox(font, ix, rowY, inner, ROW, Component.translatable("golemaddons.animations.name")));
		nameBox.setMaxLength(AnimationCodec.MAX_NAME_LENGTH);
		nameBox.setHint(Component.translatable("golemaddons.animations.name.hint"));
		nameBox.setValue(nameDraft);
		nameBox.setResponder(v -> {
			nameDraft = v;
			confirmDelete = false;
		});
		rowY += ROW + 4;

		int quarter = (inner - GAP * 6) / 4;
		GolemButton saveNew = addRenderableWidget(new GolemButton(ix, rowY, quarter, ROW,
				Component.translatable("golemaddons.animations.save_new"), UiText.GREEN, tabShownAt + 80, this::saveNew));
		GolemButton overwrite = addRenderableWidget(new GolemButton(ix + quarter + GAP * 2, rowY, quarter, ROW,
				Component.translatable("golemaddons.animations.overwrite"), UiText.ACCENT, tabShownAt + 110, this::overwrite));
		overwrite.setTooltip(Tooltip.create(Component.translatable("golemaddons.animations.overwrite.tooltip")));
		GolemButton rename = addRenderableWidget(new GolemButton(ix + 2 * (quarter + GAP * 2), rowY, quarter, ROW,
				Component.translatable("golemaddons.animations.rename"), UiText.ACCENT, tabShownAt + 140, this::rename));
		GolemButton delete = addRenderableWidget(new GolemButton(ix + inner - quarter, rowY, quarter, ROW,
				Component.translatable(confirmDelete ? "golemaddons.animations.delete.confirm" : "golemaddons.animations.delete"),
				UiText.ACCENT, tabShownAt + 170, this::delete).danger(confirmDelete));
		saveNew.active = presets.size() < GolemConfig.Animations.MAX_PRESETS;
		overwrite.active = rename.active = delete.active = selected >= 0;
		rowY += ROW + 6;

		int bottomRow = y + h - PANEL_PAD - ROW;
		listX = ix;
		listY = rowY;
		listW = inner;
		listRows = Math.max(1, (bottomRow - 4 - rowY) / PRESET_ROW);
		scroll = Math.clamp(scroll, 0, Math.max(0, presets.size() - listRows));
		for (int r = 0; r < Math.min(listRows, presets.size() - scroll); r++) {
			int index = scroll + r;
			GolemConfig.Animations.Preset preset = presets.get(index);
			addRenderableWidget(new PresetRow(listX, listY + r * PRESET_ROW, listW, PRESET_ROW, Component.literal(preset.name),
					index == selected, () -> select(index)));
		}

		int half = (inner - GAP * 2) / 2;
		GolemButton copy = addRenderableWidget(new GolemButton(ix, bottomRow, half, ROW, Component.translatable("golemaddons.animations.copy"),
				UiText.ACCENT_2, tabShownAt + 200, this::copy));
		copy.setTooltip(Tooltip.create(Component.translatable("golemaddons.animations.copy.tooltip")));
		GolemButton paste = addRenderableWidget(new GolemButton(ix + inner - half, bottomRow, half, ROW,
				Component.translatable("golemaddons.animations.paste"), UiText.ACCENT_2, tabShownAt + 230, this::paste));
		paste.setTooltip(Tooltip.create(Component.translatable("golemaddons.animations.paste.tooltip")));
	}

	private void select(int index) {
		GolemConfig.Animations.Preset preset = settings().presets.get(index);
		selected = index;
		confirmDelete = false;
		current().set(preset.animation);
		nameDraft = preset.name;
		show(Component.translatable("golemaddons.animations.msg.loaded", preset.name), UiText.ACCENT_2);
		rebuildWidgets();
	}

	/** The name in the box, or null (with a message shown) when it can't be used for {@code except}'s new name. */
	private String validName(int except) {
		String name = AnimationCodec.cleanName(nameDraft);
		if (name.isEmpty()) {
			show(Component.translatable("golemaddons.animations.msg.name_required"), UiText.YELLOW);
			return null;
		}
		List<GolemConfig.Animations.Preset> presets = settings().presets;
		for (int i = 0; i < presets.size(); i++) {
			if (i != except && presets.get(i).name.equalsIgnoreCase(name)) {
				show(Component.translatable("golemaddons.animations.msg.name_taken", name), UiText.YELLOW);
				return null;
			}
		}
		return name;
	}

	private void saveNew() {
		confirmDelete = false;
		List<GolemConfig.Animations.Preset> presets = settings().presets;
		if (presets.size() >= GolemConfig.Animations.MAX_PRESETS) {
			show(Component.translatable("golemaddons.animations.msg.full", GolemConfig.Animations.MAX_PRESETS), UiText.YELLOW);
			return;
		}
		String name = validName(-1);
		if (name == null) return;
		presets.add(new GolemConfig.Animations.Preset(name, current()));
		selected = presets.size() - 1;
		scroll = Math.max(0, presets.size() - listRows);
		nameDraft = name;
		show(Component.translatable("golemaddons.animations.msg.saved", name), UiText.GREEN);
		rebuildWidgets();
	}

	private void overwrite() {
		confirmDelete = false;
		if (selected < 0) return;
		GolemConfig.Animations.Preset preset = settings().presets.get(selected);
		preset.animation = current().copy();
		show(Component.translatable("golemaddons.animations.msg.overwritten", preset.name), UiText.GREEN);
		rebuildWidgets();
	}

	private void rename() {
		confirmDelete = false;
		if (selected < 0) return;
		String name = validName(selected);
		if (name == null) return;
		settings().presets.get(selected).name = name;
		nameDraft = name;
		show(Component.translatable("golemaddons.animations.msg.renamed", name), UiText.GREEN);
		rebuildWidgets();
	}

	private void delete() {
		if (selected < 0) return;
		if (!confirmDelete) {
			confirmDelete = true;
			rebuildWidgets();
			return;
		}
		GolemConfig.Animations.Preset removed = settings().presets.remove(selected);
		selected = -1;
		confirmDelete = false;
		show(Component.translatable("golemaddons.animations.msg.deleted", removed.name), UiText.GREEN);
		rebuildWidgets();
	}

	private void copy() {
		minecraft.keyboardHandler.setClipboard(AnimationCodec.encode(current(), nameDraft));
		show(Component.translatable("golemaddons.animations.msg.copied"), UiText.GREEN);
	}

	private void paste() {
		confirmDelete = false;
		String text = minecraft.keyboardHandler.getClipboard();
		try {
			AnimationCodec.Decoded decoded = AnimationCodec.decode(text);
			current().set(decoded.animation());
			nameDraft = decoded.name();
			selected = -1;
			show(Component.translatable("golemaddons.animations.msg.pasted"), UiText.GREEN);
		} catch (AnimationCodec.DecodeException e) {
			show(Component.translatable("golemaddons.animations.msg.paste." + e.error().name().toLowerCase(Locale.ROOT)), UiText.RED);
		}
		rebuildWidgets();
	}

	private void show(Component text, int color) {
		message = text;
		messageColor = color;
		messageAt = Util.getMillis();
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (tab == Tab.PRESETS && x >= listX && x < listX + listW && y >= listY && y < listY + listRows * PRESET_ROW) {
			int max = Math.max(0, settings().presets.size() - listRows);
			int next = Math.clamp(scroll - (int) Math.signum(scrollY), 0, max);
			if (next != scroll) {
				scroll = next;
				rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private void swingNow() {
		swingStartedAt = Util.getMillis();
	}

	/** The held item's swing length in ticks before the speed setting (vanilla's 6 for most items). */
	private int baseSwingTicks() {
		ItemStack held = minecraft.player != null ? minecraft.player.getMainHandItem() : ItemStack.EMPTY;
		return held.isEmpty() ? DEFAULT_SWING_TICKS : Math.max(1, held.getSwingAnimation().duration());
	}

	/** The preview's swing progress (0 at rest), over the same number of ticks as in game; Loop restarts it. */
	private float previewAttack() {
		int ticks = HeldItemTransform.swingDuration(baseSwingTicks(), current());
		float elapsed = (Util.getMillis() - swingStartedAt) / 50f;
		if (loopSwing && elapsed > ticks + LOOP_PAUSE_TICKS) {
			swingNow();
			elapsed = 0f;
		}
		return elapsed < ticks ? elapsed / ticks : 0f;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		extractPreview(graphics);

		if (tab == Tab.PRESETS && settings().presets.isEmpty()) {
			var lines = font.split(Component.translatable("golemaddons.animations.presets.empty"), listW - 8);
			for (int i = 0; i < lines.size(); i++) {
				graphics.centeredText(font, lines.get(i), listX + listW / 2, listY + 4 + i * 10, UiText.DARK_GRAY);
			}
		}
		if (message != null && Util.getMillis() - messageAt < MESSAGE_MS) {
			graphics.centeredText(font, message, width / 2, height - FOOTER_HEIGHT - 12, messageColor);
		} else if (!settings().enabled) {
			graphics.centeredText(font, Component.translatable("golemaddons.animations.off_hint"), width / 2, height - FOOTER_HEIGHT - 12, UiText.YELLOW);
		}
	}

	private void extractPreview(GuiGraphicsExtractor graphics) {
		backdrop.extract(graphics, previewX0, previewY0, previewX1, previewY1);
		ItemStack stack = minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()
				? minecraft.player.getMainHandItem() : new ItemStack(Items.DIAMOND_SWORD);
		boolean rightArm = minecraft.player == null || minecraft.player.getMainArm() == HumanoidArm.RIGHT;
		ItemStackRenderState item = new ItemStackRenderState();
		minecraft.getItemModelResolver().updateForTopItem(item, stack,
				rightArm ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
				minecraft.level, minecraft.player, 0);
		graphics.guiRenderState.addPicturesInPictureState(new HeldItemPreviewState(item, current().copy(), previewAttack(), rightArm,
				minecraft.getWindow().getGuiScale(), previewX0, previewY0, previewX1, previewY1, graphics.scissorStack.peek()));
		// Small crosshair, so offsets read relative to the centre of the view like in game.
		int cx = (previewX0 + previewX1) / 2;
		int cy = (previewY0 + previewY1) / 2;
		graphics.fill(cx - 3, cy, cx + 4, cy + 1, 0x60FFFFFF);
		graphics.fill(cx, cy - 3, cx + 1, cy + 4, 0x60FFFFFF);
	}
}
