package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class HumanizedTimingTest {
	private static final long MIN = 60_000L;

	@Test
	public void delaysVaryAndRespectBounds() {
		HumanizedTiming t = new HumanizedTiming(new Random(42), true);
		Set<Long> seen = new HashSet<>();
		for (int i = 0; i < 500; i++) {
			long d = t.nextDelayMs(300);
			assertTrue(d >= HumanizedTiming.MIN_DELAY_MS && d <= HumanizedTiming.MAX_DELAY_MS);
			seen.add(d);
		}
		assertTrue(seen.size() > 100);
	}

	@Test
	public void sameSeedIsDeterministic() {
		HumanizedTiming a = new HumanizedTiming(new Random(7), true);
		HumanizedTiming b = new HumanizedTiming(new Random(7), true);
		for (int i = 0; i < 20; i++) assertEquals(a.nextDelayMs(250), b.nextDelayMs(250));
	}

	@Test
	public void fatigueRisesAndDecaysOnBreak() {
		HumanizedTiming t = new HumanizedTiming(new Random(1), true);
		t.update(0, false);
		t.update(45 * MIN, false);
		assertEquals(0.5, t.fatigue(), 1e-9);
		t.update(200 * MIN, false);
		assertEquals(1.0, t.fatigue(), 1e-9);
		t.update(205 * MIN, true);
		assertEquals(0.5, t.fatigue(), 1e-9);
		t.update(300 * MIN, true);
		assertEquals(0.0, t.fatigue(), 1e-9);
	}

	@Test
	public void fatigueDisabledStaysZero() {
		HumanizedTiming t = new HumanizedTiming(new Random(1), false);
		t.update(0, false);
		t.update(300 * MIN, false);
		assertEquals(0.0, t.fatigue(), 0.0);
	}

	@Test
	public void higherFatigueMeansLongerAverageDelay() {
		HumanizedTiming fresh = new HumanizedTiming(new Random(3), true);
		HumanizedTiming tired = new HumanizedTiming(new Random(3), true);
		tired.update(0, false);
		tired.update(500 * MIN, false);
		long f = 0, g = 0;
		for (int i = 0; i < 2000; i++) {
			f += fresh.nextDelayMs(300);
			g += tired.nextDelayMs(300);
		}
		assertTrue(g > f * 1.3);
	}

	@Test
	public void hesitationRangeSafetyAndSpacing() {
		HumanizedTiming t = new HumanizedTiming(new Random(5), true);
		assertEquals(0, t.maybeHesitate(0, 1.0, false));
		long h = t.maybeHesitate(1_000_000, 1.0, true);
		assertTrue(h >= 10_000 && h <= 20_000);
		assertEquals(0, t.maybeHesitate(1_000_000 + 60_000, 1.0, true));
		assertTrue(t.maybeHesitate(1_000_000 + 130_000, 1.0, true) > 0);
		assertEquals(0, new HumanizedTiming(new Random(5), true).maybeHesitate(0, 0.0, true));
	}

	@Test
	public void fatigueRaisesHesitationChance() {
		HumanizedTiming t = new HumanizedTiming(new Random(5), true);
		assertEquals(0.02, t.hesitationProbability(0.02), 1e-9);
		t.update(0, false);
		t.update(500 * MIN, false);
		assertEquals(0.06, t.hesitationProbability(0.02), 1e-9);
	}
}
