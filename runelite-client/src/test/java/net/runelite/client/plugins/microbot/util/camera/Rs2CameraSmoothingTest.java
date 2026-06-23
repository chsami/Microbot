package net.runelite.client.plugins.microbot.util.camera;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Camera pitch/yaw setters were previously a single direct call to
 * {@code setCameraYawTarget}/{@code setCameraPitchTarget}, producing a step-ramp
 * angle profile distinct from human input. The smoothing path interpolates the
 * target across multiple sub-steps; this test asserts the non-instant entry points
 * no longer call those client methods directly, so the fingerprint fix cannot be
 * silently regressed by re-inlining the old one-liner.
 */
public class Rs2CameraSmoothingTest {

	private static ClassNode readCameraClass() throws Exception {
		try (InputStream in = Rs2Camera.class.getResourceAsStream("Rs2Camera.class")) {
			assertNotNull("Rs2Camera.class resource missing", in);
			ClassNode cn = new ClassNode();
			new ClassReader(in).accept(cn, 0);
			return cn;
		}
	}

	@Test
	public void publicSetPitchDoesNotCallClientTargetDirectly() throws Exception {
		ClassNode cn = readCameraClass();
		MethodNode setPitch = findMethod(cn, "setPitch", "(I)V");
		assertFalse("setPitch(int) must delegate, not call setCameraPitchTarget directly",
				callsClientMethod(setPitch, "setCameraPitchTarget"));
	}

	@Test
	public void publicSetYawDoesNotCallClientTargetDirectly() throws Exception {
		ClassNode cn = readCameraClass();
		MethodNode setYaw = findMethod(cn, "setYaw", "(I)V");
		assertFalse("setYaw(int) must delegate, not call setCameraYawTarget directly",
				callsClientMethod(setYaw, "setCameraYawTarget"));
	}

	@Test
	public void instantHelpersArePresentForCallersThatNeedThem() throws Exception {
		ClassNode cn = readCameraClass();
		assertNotNull("setPitchInstant must exist as an escape hatch",
				findMethod(cn, "setPitchInstant", "(I)V"));
		assertNotNull("setYawInstant must exist as an escape hatch",
				findMethod(cn, "setYawInstant", "(I)V"));
	}

	@Test
	public void smoothingBoundsAreAboveTheJerkThreshold() {
		assertTrue("min smoothing duration should be long enough to matter",
				Rs2Camera.SMOOTH_MIN_DURATION_MS >= 200);
		assertTrue("max smoothing duration should not block scripts for long",
				Rs2Camera.SMOOTH_MAX_DURATION_MS <= 800);
		assertTrue("at least 4 interpolation steps are needed to break a step-ramp",
				Rs2Camera.SMOOTH_STEPS >= 4);
	}

	@Test
	public void legacyCameraUnitsConvertToRev239ClientUnits() {
		assertEquals(3696, Rs2Camera.toClientAngleUnits(462));
		assertEquals(462, Rs2Camera.fromClientAngleUnits(3696));
		assertEquals(3064, Rs2Camera.toClientAngleUnits(383));
	}

	private static MethodNode findMethod(ClassNode cn, String name, String desc) {
		for (MethodNode m : cn.methods) {
			if (m.name.equals(name) && m.desc.equals(desc)) return m;
		}
		return null;
	}

	private static boolean callsClientMethod(MethodNode m, String clientMethod) {
		if (m == null) return false;
		return m.instructions != null && java.util.stream.StreamSupport
				.stream(m.instructions.spliterator(), false)
				.filter(i -> i instanceof MethodInsnNode)
				.map(i -> (MethodInsnNode) i)
				.anyMatch(mi -> mi.name.equals(clientMethod));
	}

	// Sanity check: the ASM deps used here actually resolve at test time.
	@Test
	public void asmIsOnClasspath() {
		assertEquals("org.objectweb.asm", ClassReader.class.getPackage().getName());
	}
}
