package net.runelite.client.plugins.microbot.ddchopper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class HumanPacingTest {
	private static final long MIN = 60_000L;

	@Test
	public void reactionDelayIsFlooredCappedAndVaries() {
		HumanPacing p = new HumanPacing(new Random(42), true, true, 2);
		Set<Long> seen = new HashSet<>();
		long sum = 0;
		for (int i = 0; i < 1000; i++) {
			long d = p.reactionDelayMs();
			assertTrue(d >= HumanPacing.REACTION_FLOOR_MS && d <= HumanPacing.REACTION_CAP_MS);
			seen.add(d);
			sum += d;
		}
		assertTrue(seen.size() > 200);
		long mean = sum / 1000;
		assertTrue("mean " + mean, mean > 500 && mean < 1500);
	}

	@Test
	public void disabledMeansNoDelaysOrPauses() {
		HumanPacing p = new HumanPacing(new Random(1), false, true, 50);
		assertEquals(0, p.reactionDelayMs());
		assertEquals(0, p.idlePauseMs(10_000_000L, true));
		assertTrue(p.dropDelayMs() >= 150);
		assertEquals(0.0, p.fatigue(), 0.0);
	}

	@Test
	public void fatigueWidensDelaysAndMouseNoise() {
		HumanPacing fresh = new HumanPacing(new Random(3), true, true, 2);
		HumanPacing tired = new HumanPacing(new Random(3), true, true, 2);
		tired.update(0, false);
		tired.update(500 * MIN, false);
		assertEquals(1.0, tired.fatigue(), 1e-9);
		long a = 0, b = 0;
		for (int i = 0; i < 1000; i++) {
			a += fresh.reactionDelayMs();
			b += tired.reactionDelayMs();
		}
		assertTrue(b > a);
		assertTrue(tired.mouseNoiseAmplitude() > fresh.mouseNoiseAmplitude());
		tired.update(510 * MIN, true);
		assertEquals(0.0, tired.fatigue(), 1e-9);
	}

	@Test
	public void fatigueOffKeepsNoiseAtBase() {
		HumanPacing p = new HumanPacing(new Random(1), true, false, 2);
		p.update(0, false);
		p.update(500 * MIN, false);
		assertEquals(HumanPacing.MOUSE_NOISE_BASE, p.mouseNoiseAmplitude(), 0.0);
	}

	@Test
	public void idlePauseIsFiveToTwentySecondsAndRespectsZeroChance() {
		for (int seed = 0; seed < 30; seed++) {
			HumanPacing p = new HumanPacing(new Random(seed), true, true, 100);
			long d = p.idlePauseMs(10_000_000L, true);
			assertTrue(d >= 5_000 && d <= 20_000);
			assertEquals(0, p.idlePauseMs(10_000_001L, true));
		}
		HumanPacing none = new HumanPacing(new Random(1), true, true, 0);
		assertEquals(0, none.idlePauseMs(10_000_000L, true));
	}

	@Test
	public void idlePauseChanceIsLowAndRisesWithFatigue() {
		int fresh = 0, tired = 0;
		HumanPacing f = new HumanPacing(new Random(11), true, true, 2);
		HumanPacing t = new HumanPacing(new Random(11), true, true, 2);
		t.update(0, false);
		t.update(500 * MIN, false);
		for (int i = 0; i < 4000; i++) {
			long now = 10_000_000L + i * 200_000L;
			if (f.idlePauseMs(now, true) > 0) fresh++;
			if (t.idlePauseMs(now, true) > 0) tired++;
		}
		assertTrue("fresh " + fresh, fresh > 30 && fresh < 150);
		assertTrue(tired > fresh);
	}

	@Test
	public void dropDelaysVary() {
		HumanPacing p = new HumanPacing(new Random(9), true, true, 2);
		Set<Long> seen = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			long d = p.dropDelayMs();
			assertTrue(d >= HumanPacing.DROP_FLOOR_MS);
			seen.add(d);
		}
		assertTrue(seen.size() > 50);
	}
}
