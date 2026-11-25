package com.golemaddons.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.function.Consumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class HeldItemTransformTest {
	private static Matrix4f pose(Consumer<PoseStack> transform) {
		PoseStack stack = new PoseStack();
		transform.accept(stack);
		return new Matrix4f(stack.last().pose());
	}

	/** Vanilla 26.1.2: applyItemArmTransform then swingArm (ItemInHandRenderer), copied for comparison. */
	private static void vanilla(PoseStack pose, boolean right, float height, float attack) {
		int invert = right ? 1 : -1;
		pose.translate(invert * 0.56F, -0.52F + height * -0.6F, -0.72F);
		pose.translate(invert * -0.4F * Mth.sin(Mth.sqrt(attack) * (float) Math.PI), 0.2F * Mth.sin(Mth.sqrt(attack) * (float) (Math.PI * 2)),
				-0.2F * Mth.sin(attack * (float) Math.PI));
		float y = Mth.sin(attack * attack * (float) Math.PI);
		pose.mulPose(Axis.YP.rotationDegrees(invert * (45.0F + y * -20.0F)));
		float xz = Mth.sin(Mth.sqrt(attack) * (float) Math.PI);
		pose.mulPose(Axis.ZP.rotationDegrees(invert * xz * -20.0F));
		pose.mulPose(Axis.XP.rotationDegrees(xz * -80.0F));
		pose.mulPose(Axis.YP.rotationDegrees(invert * -45.0F));
	}

	private static Matrix4f ours(HeldItemAnimation a, boolean right, float height, float attack) {
		return pose(s -> {
			HeldItemTransform.applyBase(s, a, right, height);
			HeldItemTransform.applyItem(s, a, right, attack);
		});
	}

	@Test
	void defaultsAreExactlyVanillaThroughTheWholeSwing() {
		HeldItemAnimation defaults = new HeldItemAnimation();
		for (boolean right : new boolean[] {true, false}) {
			for (float p = 0f; p <= 1f; p += 0.05f) {
				float attack = p;
				Matrix4f expected = pose(s -> vanilla(s, right, 0.3f, attack));
				assertTrue(ours(defaults, right, 0.3f, attack).equals(expected, 1e-5f), "p=" + p + " right=" + right);
			}
		}
	}

	@Test
	void positionScalesVanillasRestingPositionLikeOdin() {
		HeldItemAnimation a = new HeldItemAnimation();
		a.x = 1f;
		a.y = 0.5f;
		a.z = -0.5f;
		Vector3f t = pose(s -> HeldItemTransform.applyBase(s, a, true, 0f)).getTranslation(new Vector3f());
		assertEquals(0.56f * 2f, t.x, 1e-6);
		assertEquals(-0.52f * 0.5f, t.y, 1e-6);
		assertEquals(-0.72f * 0.5f, t.z, 1e-6);
		assertEquals(-0.56f * 2f, pose(s -> HeldItemTransform.applyBase(s, a, false, 0f)).getTranslation(new Vector3f()).x, 1e-6);
	}

	@Test
	void pivotOnlyRotatesAndScaleSwingScalesTheMovement() {
		HeldItemAnimation pivot = new HeldItemAnimation();
		pivot.swingTranslation = false;
		assertEquals(0f, pose(s -> HeldItemTransform.applyItem(s, pivot, true, 0.3f)).getTranslation(new Vector3f()).length(), 1e-6);

		HeldItemAnimation big = new HeldItemAnimation();
		big.scale = 2f;
		HeldItemAnimation normal = new HeldItemAnimation();
		float moved = pose(s -> HeldItemTransform.applyItem(s, normal, true, 0.3f)).getTranslation(new Vector3f()).length();
		assertEquals(moved * 2f, pose(s -> HeldItemTransform.applyItem(s, big, true, 0.3f)).getTranslation(new Vector3f()).length(), 1e-5);
		big.scaleSwing = false;
		assertEquals(moved, pose(s -> HeldItemTransform.applyItem(s, big, true, 0.3f)).getTranslation(new Vector3f()).length(), 1e-5);
	}

	@Test
	void noEquipResetIgnoresTheEquipDip() {
		HeldItemAnimation a = new HeldItemAnimation();
		a.noEquipReset = true;
		assertTrue(pose(s -> HeldItemTransform.applyBase(s, a, true, 0.8f)).equals(pose(s -> HeldItemTransform.applyBase(s, a, true, 0f)), 1e-6f));
	}

	@Test
	void speedChangesTheSwingLengthInTicks() {
		HeldItemAnimation a = new HeldItemAnimation();
		assertEquals(6, HeldItemTransform.swingDuration(6, a));
		a.swingSpeed = 2f;
		assertEquals(3, HeldItemTransform.swingDuration(6, a));
		a.swingSpeed = 0.5f;
		assertEquals(12, HeldItemTransform.swingDuration(6, a));
		a.swingSpeed = 3f;
		assertEquals(1, HeldItemTransform.swingDuration(2, a));
	}
}
