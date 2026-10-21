package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class Rs2WalkerTransportsOriginAheadTest
{
	private static final WorldPoint ORIGIN = new WorldPoint(1487, 3232, 0);

	@Test
	public void originBehindTheMissedIndexStopsTheScan()
	{
		List<WorldPoint> path = Arrays.asList(
				ORIGIN,
				new WorldPoint(1400, 3250, 0),
				new WorldPoint(1364, 3287, 0));

		assertEquals(-1, Rs2WalkerTransports.nextIndexAfterObjectMiss(path, 1, ORIGIN));
	}

	@Test
	public void originAheadReturnsItsIndex()
	{
		List<WorldPoint> path = Arrays.asList(
				new WorldPoint(1400, 3250, 0),
				new WorldPoint(1450, 3240, 0),
				ORIGIN);

		assertEquals(2, Rs2WalkerTransports.nextIndexAfterObjectMiss(path, 0, ORIGIN));
		assertEquals(-1, Rs2WalkerTransports.nextIndexAfterObjectMiss(path, 2, ORIGIN));
	}

	@Test
	public void missedIndexItselfIsNotTheTarget()
	{
		List<WorldPoint> path = Arrays.asList(
				new WorldPoint(1400, 3250, 0),
				ORIGIN,
				new WorldPoint(1450, 3240, 0),
				ORIGIN);

		assertEquals(1, Rs2WalkerTransports.nextIndexAfterObjectMiss(path, 0, ORIGIN));
		assertEquals(3, Rs2WalkerTransports.nextIndexAfterObjectMiss(path, 1, ORIGIN));
	}

	@Test
	public void nullOriginStopsTheScan()
	{
		List<WorldPoint> path = Arrays.asList(ORIGIN, new WorldPoint(1400, 3250, 0));

		assertEquals(-1, Rs2WalkerTransports.nextIndexAfterObjectMiss(path, 0, null));
	}

	@Test
	public void negativeIndexStartsTheScanAtPathStart()
	{
		List<WorldPoint> path = Arrays.asList(new WorldPoint(1400, 3250, 0), ORIGIN);

		assertEquals(1, Rs2WalkerTransports.nextIndexAfterObjectMiss(path, -5, ORIGIN));
	}
}
