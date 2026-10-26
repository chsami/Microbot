package net.runelite.client.plugins.microbot.ddchopper;

import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SpotRotationTest
{
	private static final long MIN = 60_000L;
	private static final List<Spot> POOL = List.of(Spot.EDGEVILLE_YEWS, Spot.VARROCK_PALACE_YEWS, Spot.DRAYNOR_MANOR_YEWS);

	@Test
	public void fixedTimeStaysThenMovesToNextSpot()
	{
		SpotRotation rotation = new SpotRotation(new Random(1), 90, 90);
		assertEquals(Spot.EDGEVILLE_YEWS, rotation.current(POOL, 0));
		assertEquals(90 * MIN, rotation.remainingMillis(0));
		assertEquals(Spot.EDGEVILLE_YEWS, rotation.current(POOL, 89 * MIN));
		assertEquals(Spot.VARROCK_PALACE_YEWS, rotation.current(POOL, 90 * MIN));
		assertEquals(Spot.VARROCK_PALACE_YEWS, rotation.current(POOL, 179 * MIN));
		assertEquals(Spot.DRAYNOR_MANOR_YEWS, rotation.current(POOL, 180 * MIN));
		// Round-robin back to the start.
		assertEquals(Spot.EDGEVILLE_YEWS, rotation.current(POOL, 270 * MIN));
	}

	@Test
	public void stayLengthIsRandomInsideTheRange()
	{
		SpotRotation rotation = new SpotRotation(new Random(5), 60, 90);
		long now = 0;
		for (int i = 0; i < 50; i++)
		{
			rotation.current(POOL, now);
			long stay = rotation.remainingMillis(now);
			assertTrue(stay >= 60 * MIN && stay <= 90 * MIN);
			now += stay;
		}
	}

	@Test
	public void sameSeedSameRotation()
	{
		SpotRotation a = new SpotRotation(new Random(9), 10, 40);
		SpotRotation b = new SpotRotation(new Random(9), 10, 40);
		for (long t = 0; t < 600 * MIN; t += 7 * MIN)
		{
			assertEquals(a.current(POOL, t), b.current(POOL, t));
			assertEquals(a.remainingMillis(t), b.remainingMillis(t));
		}
	}

	@Test
	public void singleSpotNeverMoves()
	{
		SpotRotation rotation = new SpotRotation(new Random(2), 1, 1);
		List<Spot> one = List.of(Spot.EDGEVILLE_YEWS);
		assertEquals(Spot.EDGEVILLE_YEWS, rotation.current(one, 0));
		assertEquals(Spot.EDGEVILLE_YEWS, rotation.current(one, 1000 * MIN));
		assertNull(rotation.current(List.of(), 0));
	}

	@Test
	public void currentSpotRemovedFromPoolRestartsAtFirst()
	{
		SpotRotation rotation = new SpotRotation(new Random(3), 30, 30);
		rotation.current(POOL, 0);
		assertEquals(Spot.VARROCK_PALACE_YEWS, rotation.current(List.of(Spot.VARROCK_PALACE_YEWS, Spot.DRAYNOR_MANOR_YEWS), 1));
	}

	@Test
	public void chosenSpotsFilterThePoolToTheCurrentTree()
	{
		ChopPlanner.Input in = new ChopPlanner.Input();
		in.level = 60;
		in.axe = Axe.MITHRIL;
		in.rotate = true;
		in.rotateSpots = ChopPlanner.parseSpots("EDGEVILLE_YEWS, Yew: Falador south, DRAYNOR_WILLOWS, nonsense");
		ChopPlanner.Plan plan = ChopPlanner.plan(in);
		assertEquals(TreeType.YEW, plan.tree);
		// The willow spot is dropped because it does not suit yews.
		assertEquals(2, plan.pool.size());
		assertTrue(plan.pool.contains(Spot.EDGEVILLE_YEWS));
		assertTrue(plan.pool.contains(Spot.FALADOR_SOUTH_YEWS));
	}

	@Test
	public void nothingChosenRotatesThroughAllEligibleSpots()
	{
		ChopPlanner.Input in = new ChopPlanner.Input();
		in.level = 60;
		in.rotate = true;
		ChopPlanner.Plan plan = ChopPlanner.plan(in);
		assertEquals(Spot.forTree(TreeType.YEW, 60, false, false).size(), plan.pool.size());
	}

	@Test
	public void rotationOffGivesASingleSpot()
	{
		ChopPlanner.Input in = new ChopPlanner.Input();
		in.level = 60;
		assertEquals(1, ChopPlanner.plan(in).pool.size());
	}

	@Test
	public void parseSpotsIgnoresUnknownNames()
	{
		Set<Spot> spots = ChopPlanner.parseSpots(" edgeville_yews ,, bogus ");
		assertEquals(1, spots.size());
		assertTrue(spots.contains(Spot.EDGEVILLE_YEWS));
		assertTrue(ChopPlanner.parseSpots(null).isEmpty());
	}
}
