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
	public void originBehindStartIndexIsNotAhead()
	{
		List<WorldPoint> path = Arrays.asList(
				ORIGIN,
				new WorldPoint(1400, 3250, 0),
				new WorldPoint(1364, 3287, 0));

		assertEquals(-1, Rs2WalkerTransports.indexOfOriginAhead(path, 1, ORIGIN));
	}

	@Test
	public void originAtOrAfterFromIndexIsAhead()
	{
		List<WorldPoint> path = Arrays.asList(
				new WorldPoint(1400, 3250, 0),
				new WorldPoint(1450, 3240, 0),
				ORIGIN);

		assertEquals(2, Rs2WalkerTransports.indexOfOriginAhead(path, 0, ORIGIN));
		assertEquals(2, Rs2WalkerTransports.indexOfOriginAhead(path, 2, ORIGIN));
		assertEquals(-1, Rs2WalkerTransports.indexOfOriginAhead(path, 3, ORIGIN));
	}

	@Test
	public void firstMatchingIndexAtOrAfterFromIndexIsReturned()
	{
		List<WorldPoint> path = Arrays.asList(
				new WorldPoint(1400, 3250, 0),
				ORIGIN,
				new WorldPoint(1450, 3240, 0),
				ORIGIN);

		assertEquals(1, Rs2WalkerTransports.indexOfOriginAhead(path, 0, ORIGIN));
		assertEquals(3, Rs2WalkerTransports.indexOfOriginAhead(path, 2, ORIGIN));
	}

	@Test
	public void nullOriginIsNeverAhead()
	{
		List<WorldPoint> path = Arrays.asList(ORIGIN, new WorldPoint(1400, 3250, 0));

		assertEquals(-1, Rs2WalkerTransports.indexOfOriginAhead(path, 0, null));
	}

	@Test
	public void negativeFromIndexIsClampedToPathStart()
	{
		List<WorldPoint> path = Arrays.asList(ORIGIN, new WorldPoint(1400, 3250, 0));

		assertEquals(0, Rs2WalkerTransports.indexOfOriginAhead(path, -5, ORIGIN));
	}
}
