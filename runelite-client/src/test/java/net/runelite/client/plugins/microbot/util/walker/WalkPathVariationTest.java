package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WalkPathVariationTest
{
	private static List<WorldPoint> line(int n)
	{
		List<WorldPoint> path = new ArrayList<>();
		for (int i = 0; i < n; i++)
		{
			path.add(new WorldPoint(3200 + i, 3200, 0));
		}
		return path;
	}

	@After
	public void reset()
	{
		WalkPathVariation.configure(false, 3);
	}

	@Test
	public void variedTargetIsAlwaysAnEarlierRouteTile()
	{
		List<WorldPoint> path = line(60);
		WorldPoint player = path.get(0);
		WorldPoint target = path.get(20);
		Set<WorldPoint> seen = new HashSet<>();
		for (int seed = 0; seed < 200; seed++)
		{
			WorldPoint v = WalkPathVariation.vary(path, target, player, 3, new Random(seed));
			assertTrue(path.contains(v));
			assertTrue(path.indexOf(v) <= path.indexOf(target));
			assertTrue(v.distanceTo(player) >= WalkPathVariation.MIN_PLAYER_DISTANCE);
			seen.add(v);
		}
		assertTrue("should vary", seen.size() > 1);
	}

	@Test
	public void neverVariesNearTheEndOfTheRoute()
	{
		List<WorldPoint> path = line(30);
		WorldPoint target = path.get(25);
		for (int seed = 0; seed < 50; seed++)
		{
			assertEquals(target, WalkPathVariation.vary(path, target, path.get(0), 3, new Random(seed)));
		}
	}

	@Test
	public void neverReturnsFinalDestinationAlteredOrOffRoute()
	{
		List<WorldPoint> path = line(40);
		WorldPoint last = path.get(39);
		assertEquals(last, WalkPathVariation.vary(path, last, path.get(0), 3, new Random(1)));
		WorldPoint offRoute = new WorldPoint(1, 1, 0);
		assertEquals(offRoute, WalkPathVariation.vary(path, offRoute, path.get(0), 3, new Random(1)));
	}

	@Test
	public void disabledReturnsTarget()
	{
		List<WorldPoint> path = line(60);
		WalkPathVariation.configure(false, 3);
		for (int i = 0; i < 20; i++)
		{
			assertEquals(path.get(20), WalkPathVariation.varyMinimapTarget(path, path.get(20), path.get(0), "t" + i));
		}
	}

	@Test
	public void strengthIsClampedAndScalesPullBack()
	{
		WalkPathVariation.configure(true, 99);
		assertEquals(3, WalkPathVariation.getStrength());
		assertTrue(WalkPathVariation.maxPullBack(3) > WalkPathVariation.maxPullBack(1));
		assertFalse(WalkPathVariation.maxPullBack(0) < 1);
	}

	@Test
	public void cadenceStaysPositive()
	{
		WalkPathVariation.configure(true, 3);
		Random rng = new Random(3);
		for (int i = 0; i < 200; i++)
		{
			assertTrue(WalkPathVariation.nextCadence(2, 8, rng) >= 1);
		}
	}
}
