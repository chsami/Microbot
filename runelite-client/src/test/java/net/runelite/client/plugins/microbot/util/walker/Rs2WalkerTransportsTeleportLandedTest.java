package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerTransportsTeleportLandedTest
{
	private static final WorldPoint CIVITAS = new WorldPoint(1680, 3134, 0);
	private static final WorldPoint VARROCK = new WorldPoint(3213, 3424, 0);

	@Test
	public void scatteredLandingOfTheTeleportJustHandledIsNotCastAgain()
	{
		assertTrue(Rs2WalkerTransports.isTeleportAlreadyLanded(new WorldPoint(1683, 3131, 0), CIVITAS, CIVITAS));
		assertTrue(Rs2WalkerTransports.isTeleportAlreadyLanded(new WorldPoint(1689, 3134, 0), CIVITAS, CIVITAS));
	}

	@Test
	public void landingRadiusIsExclusive()
	{
		assertFalse(Rs2WalkerTransports.isTeleportAlreadyLanded(new WorldPoint(1690, 3134, 0), CIVITAS, CIVITAS));
	}

	@Test
	public void sameDistanceWithoutAHandledTeleportStillCasts()
	{
		assertFalse(Rs2WalkerTransports.isTeleportAlreadyLanded(new WorldPoint(1683, 3131, 0), CIVITAS, null));
		assertFalse(Rs2WalkerTransports.isTeleportAlreadyLanded(new WorldPoint(1683, 3131, 0), CIVITAS, VARROCK));
	}

	@Test
	public void standingNextToTheDestinationSkipsRegardlessOfHistory()
	{
		assertTrue(Rs2WalkerTransports.isTeleportAlreadyLanded(new WorldPoint(1682, 3132, 0), CIVITAS, null));
	}

	@Test
	public void otherPlaneNeverCountsAsLanded()
	{
		assertFalse(Rs2WalkerTransports.isTeleportAlreadyLanded(new WorldPoint(1680, 3134, 1), CIVITAS, CIVITAS));
	}

	@Test
	public void missingPositionsAreNotLanded()
	{
		assertFalse(Rs2WalkerTransports.isTeleportAlreadyLanded(null, CIVITAS, CIVITAS));
		assertFalse(Rs2WalkerTransports.isTeleportAlreadyLanded(new WorldPoint(1680, 3134, 0), null, CIVITAS));
	}
}
