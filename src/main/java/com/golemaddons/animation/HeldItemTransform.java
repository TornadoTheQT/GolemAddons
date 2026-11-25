package com.golemaddons.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * First-person held item transform, ported from OdinLegacy's Animations module
 * (https://github.com/odtheking/OdinLegacy, BSD-3-Clause; parts of it come from Floppa Client) and adapted from
 * Minecraft 1.8.9 to 26.1.2. Used by the in-game renderer ({@code ItemInHandRendererMixin}) and the editor preview,
 * so both always match.
 *
 * <p>Like Odin: X/Y/Z scale vanilla's resting position, pitch/yaw/roll are applied before the swing (so the swing
 * follows the rotated item), the swing's movement scales with the item's size, and the swing speed changes the
 * vanilla swing length. Modern item models need the extra -45° yaw that 1.8 baked into its first-person display
 * transform. With every setting at its default this is exactly vanilla's {@code applyItemArmTransform} + {@code swingArm}.
 */
public final class HeldItemTransform {
	private HeldItemTransform() {
	}

	/** Odin's resting position (vanilla's {@code applyItemArmTransform} with X/Y/Z as multipliers). */
	public static void applyBase(PoseStack pose, HeldItemAnimation a, boolean rightArm, float inverseArmHeight) {
		float invert = rightArm ? 1f : -1f;
		float drop = a.noEquipReset ? 0f : inverseArmHeight;
		pose.translate(invert * 0.56f * (1f + a.x), -0.52f * (1f - a.y) + drop * -0.6f, -0.72f * (1f + a.z));
	}

	/**
	 * The swing and the item's rotation and size, applied right before the item is drawn. {@code attack} is the swing
	 * progress (0..1), or 0 when the item isn't swinging.
	 */
	public static void applyItem(PoseStack pose, HeldItemAnimation a, boolean rightArm, float attack) {
		float invert = rightArm ? 1f : -1f;
		float sqrt = Mth.sqrt(attack);
		if (a.swingTranslation && attack > 0f) {
			float s = a.scaleSwing ? a.scale : 1f;
			pose.translate(invert * -0.4f * Mth.sin(sqrt * Mth.PI) * s, 0.2f * Mth.sin(sqrt * Mth.PI * 2f) * s,
					-0.2f * Mth.sin(attack * Mth.PI) * s);
		}
		pose.mulPose(Axis.XP.rotationDegrees(a.rotX));
		pose.mulPose(Axis.YP.rotationDegrees(invert * (a.rotY + 45f)));
		pose.mulPose(Axis.ZP.rotationDegrees(invert * a.rotZ));
		float f1 = Mth.sin(sqrt * Mth.PI);
		pose.mulPose(Axis.YP.rotationDegrees(invert * Mth.sin(attack * attack * Mth.PI) * -20f));
		pose.mulPose(Axis.ZP.rotationDegrees(invert * f1 * -20f));
		pose.mulPose(Axis.XP.rotationDegrees(f1 * -80f));
		pose.mulPose(Axis.YP.rotationDegrees(invert * -45f));
		if (a.scale != 1f) pose.scale(a.scale, a.scale, a.scale);
	}

	/** Vanilla's resting position, for the preview when animations are at their defaults. */
	public static void applyArm(PoseStack pose, boolean rightArm, float inverseArmHeight) {
		applyBase(pose, DEFAULT, rightArm, inverseArmHeight);
	}

	private static final HeldItemAnimation DEFAULT = new HeldItemAnimation();

	/** The swing length in ticks for a base length (the held item's, with or without Haste), at the chosen speed. */
	public static int swingDuration(int baseTicks, HeldItemAnimation a) {
		return Math.max(1, (int) (baseTicks / a.swingSpeed));
	}
}
