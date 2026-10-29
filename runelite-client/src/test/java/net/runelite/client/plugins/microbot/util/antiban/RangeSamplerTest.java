package net.runelite.client.plugins.microbot.util.antiban;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultMouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FactoryTemplates;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;
import org.junit.Test;

public class RangeSamplerTest
{
	@Test
	public void theRandomMouseChanceIsSampledFromFiveToTwentyPercent()
	{
		Random random = new Random(42);
		double low = 1;
		double high = 0;
		double sum = 0;
		int n = 5000;
		for (int i = 0; i < n; i++)
		{
			double v = RangeSampler.between(0.05, 0.20, random);
			assertTrue(v >= 0.05 && v <= 0.20);
			low = Math.min(low, v);
			high = Math.max(high, v);
			sum += v;
		}
		assertTrue("reaches near the bottom", low < 0.06);
		assertTrue("reaches near the top", high > 0.19);
		assertEquals("average about 12.5 percent", 0.125, sum / n, 0.005);
	}

	@Test
	public void aSampleLastsForThePeriodThenAnotherIsDrawn()
	{
		RangeSampler sampler = new RangeSampler(600_000L);
		Random random = new Random(1);
		double first = sampler.get(0.05, 0.20, 0L, random);
		assertEquals(first, sampler.get(0.05, 0.20, 599_999L, random), 0.0);
		double second = sampler.get(0.05, 0.20, 600_000L, random);
		assertNotEquals(first, second, 0.0);
		assertTrue(second >= 0.05 && second <= 0.20);
	}

	@Test
	public void changingTheRangeDrawsAgainAtOnce()
	{
		RangeSampler sampler = new RangeSampler(600_000L);
		Random random = new Random(2);
		sampler.get(0.05, 0.20, 0L, random);
		double narrowed = sampler.get(0.50, 0.60, 1L, random);
		assertTrue(narrowed >= 0.50 && narrowed <= 0.60);
	}

	@Test
	public void aRangeGivenBackToFrontStillWorks()
	{
		RangeSampler sampler = new RangeSampler(1_000L);
		double v = sampler.get(0.20, 0.05, 0L, new Random(3));
		assertTrue(v >= 0.05 && v <= 0.20);
		assertEquals(7, RangeSampler.betweenInclusive(7, 7, new Random(4)));
	}

	@Test
	public void everyMouseMoveGetsItsOwnSpeedInsideTheRange()
	{
		Random random = new Random(7);
		long fastest = Long.MAX_VALUE;
		long slowest = 0;
		java.util.Set<Long> distinct = new java.util.HashSet<>();
		for (int i = 0; i < 300; i++)
		{
			MouseMotionFactory factory = FactoryTemplates.createHumanMotionFactory(
				new DefaultMouseMotionNature(), 150, 300, random);
			Pair<?, Long> flow = (Pair<?, Long>) factory.getSpeedManager().getFlowWithTime(400);
			long time = flow.y;
			fastest = Math.min(fastest, time);
			slowest = Math.max(slowest, time);
			distinct.add(time);
		}
		assertTrue("never faster than the minimum base time: " + fastest, fastest >= 150);
		assertTrue("moves are not all alike: " + slowest + " vs " + fastest, slowest - fastest > 150);
		assertTrue(distinct.size() > 100);
	}
}
