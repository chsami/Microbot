package net.runelite.client.plugins.microbot.util.antiban;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class HumanizedTimingSharedTest {
	@Test
	public void customHesitationRangeIsHonoured() {
		for (int seed = 0; seed < 50; seed++) {
			HumanizedTiming t = new HumanizedTiming(new Random(seed), true);
			long h = t.maybeHesitate(1_000_000L, 1.0, true, 5_000, 20_000);
			assertTrue(h >= 5_000 && h <= 20_000);
		}
	}

	@Test
	public void noHesitationWhenUnsafeOrTooSoon() {
		HumanizedTiming t = new HumanizedTiming(new Random(1), true);
		assertEquals(0, t.maybeHesitate(1_000_000L, 1.0, false, 5_000, 20_000));
		assertTrue(t.maybeHesitate(1_000_000L, 1.0, true, 5_000, 20_000) > 0);
		assertEquals(0, t.maybeHesitate(1_010_000L, 1.0, true, 5_000, 20_000));
	}
}
