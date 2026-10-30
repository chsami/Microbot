package net.runelite.client.plugins.microbot.util.walker;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerStaminaTest {

	private static final long FIXED_SEED_A = 0xAAAAAAAAAAAAAAAAL;
	private static final long FIXED_SEED_B = 0x5555555555555555L;

	private static final List<String> SAMPLE_NAMES = List.of(
			"Alice", "Bob", "Carol", "Dave", "Eve", "Frank", "Grace", "Heidi",
			"Ivan", "Judy", "Mallory", "Niaj", "Olivia", "Peggy", "Rupert",
			"Sybil", "Trent", "Victor", "Walter", "Zoe", "xX_dragonslayer_Xx",
			"pvmdude", "f2pgrinder", "questhelper01", "Zezima", "Lynx Titan"
	);

	@Test
	public void thresholdAlwaysInConfiguredRange() {
		for (String name : SAMPLE_NAMES) {
			for (long seed : new long[]{FIXED_SEED_A, FIXED_SEED_B, 0L, 42L}) {
				int v = Rs2WalkerMovement.computeStaminaThreshold(name, seed);
				assertTrue(name + "@" + seed + " → " + v + " < min", v >= Rs2Walker.STAMINA_THRESHOLD_MIN);
				assertTrue(name + "@" + seed + " → " + v + " > max", v <= Rs2Walker.STAMINA_THRESHOLD_MAX);
			}
		}
	}

	@Test
	public void thresholdIsDeterministicPerNameAndSeed() {
		for (String name : SAMPLE_NAMES) {
			int first = Rs2WalkerMovement.computeStaminaThreshold(name, FIXED_SEED_A);
			int second = Rs2WalkerMovement.computeStaminaThreshold(name, FIXED_SEED_A);
			assertEquals(first, second);
		}
	}

	@Test
	public void thresholdIsCaseInsensitive() {
		assertEquals(Rs2WalkerMovement.computeStaminaThreshold("Zezima", FIXED_SEED_A),
				Rs2WalkerMovement.computeStaminaThreshold("ZEZIMA", FIXED_SEED_A));
		assertEquals(Rs2WalkerMovement.computeStaminaThreshold("Lynx Titan", FIXED_SEED_A),
				Rs2WalkerMovement.computeStaminaThreshold("lynx titan", FIXED_SEED_A));
	}

	@Test
	public void differentInstallSeedsProduceDifferentThresholds() {
		int differing = 0;
		for (String name : SAMPLE_NAMES) {
			int a = Rs2WalkerMovement.computeStaminaThreshold(name, FIXED_SEED_A);
			int b = Rs2WalkerMovement.computeStaminaThreshold(name, FIXED_SEED_B);
			if (a != b) differing++;
		}
		assertTrue("install seed must meaningfully scatter thresholds across installs; only " + differing + " of "
						+ SAMPLE_NAMES.size() + " names differed between seeds",
				differing >= SAMPLE_NAMES.size() * 3 / 4);
	}

	@Test
	public void distributionIsBimodal() {
		int hardcore = 0;
		int casual = 0;
		Random nameGen = new Random(1234L);
		int trials = 5_000;
		for (int i = 0; i < trials; i++) {
			String synthetic = Long.toHexString(nameGen.nextLong());
			int v = Rs2WalkerMovement.computeStaminaThreshold(synthetic, FIXED_SEED_A);
			if (v >= Rs2Walker.STAMINA_HARDCORE_MIN && v <= Rs2Walker.STAMINA_HARDCORE_MAX) hardcore++;
			else if (v >= Rs2Walker.STAMINA_CASUAL_MIN && v <= Rs2Walker.STAMINA_CASUAL_MAX) casual++;
		}
		assertEquals("every sample must land inside one of the two configured buckets", trials, hardcore + casual);
		double hardcoreRate = hardcore / (double) trials;
		double expected = Rs2Walker.STAMINA_HARDCORE_PROBABILITY;
		assertTrue("hardcore bucket rate " + hardcoreRate + " deviates too far from configured " + expected,
				Math.abs(hardcoreRate - expected) < 0.05);
	}

	@Test
	public void thresholdFallbackHandlesNullAndEmpty() {
		int nullThreshold = Rs2WalkerMovement.computeStaminaThreshold(null, FIXED_SEED_A);
		int emptyThreshold = Rs2WalkerMovement.computeStaminaThreshold("", FIXED_SEED_A);
		assertEquals(nullThreshold, emptyThreshold);
		assertTrue(nullThreshold >= Rs2Walker.STAMINA_THRESHOLD_MIN);
		assertTrue(nullThreshold <= Rs2Walker.STAMINA_THRESHOLD_MAX);
	}

	@Test
	public void populationSpreadCoversMultipleValuesPerBucket() {
		Set<Integer> casualValues = new HashSet<>();
		Set<Integer> hardcoreValues = new HashSet<>();
		Random nameGen = new Random(42L);
		for (int i = 0; i < 1_000; i++) {
			String name = Long.toHexString(nameGen.nextLong());
			int v = Rs2WalkerMovement.computeStaminaThreshold(name, FIXED_SEED_A);
			if (v <= Rs2Walker.STAMINA_HARDCORE_MAX) {
				hardcoreValues.add(v);
			} else {
				casualValues.add(v);
			}
		}
		assertTrue("casual bucket should produce several distinct values; saw " + casualValues,
				casualValues.size() >= 10);
		assertTrue("hardcore bucket should produce several distinct values; saw " + hardcoreValues,
				hardcoreValues.size() >= 5);
	}

	@Test
	public void anOverrideOfZeroOrLessIsNoOverride() {
		try {
			Rs2Walker.setStaminaThresholdOverride(40);
			assertEquals(40, Rs2Walker.staminaThresholdOverride);
			Rs2Walker.setStaminaThresholdOverride(0);
			assertEquals(-1, Rs2Walker.staminaThresholdOverride);
		} finally {
			Rs2Walker.setStaminaThresholdOverride(-1);
		}
	}
}
