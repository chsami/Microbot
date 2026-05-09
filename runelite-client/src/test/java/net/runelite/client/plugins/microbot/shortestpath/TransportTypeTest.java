package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import org.junit.Assert;
import org.junit.Test;

public class TransportTypeTest
{
	@Test
	public void seasonalTeleportOriginlessIsTeleportForPathfinding()
	{
		Assert.assertTrue(TransportType.isTeleport(TransportType.SEASONAL_TRANSPORT, null));
	}

	@Test
	public void seasonalTeleportAnchoredIsNotTeleportForPathfinding()
	{
		WorldPoint origin = new WorldPoint(3200, 3200, 0);
		Assert.assertFalse(TransportType.isTeleport(TransportType.SEASONAL_TRANSPORT, origin));
	}

	@Test
	public void seasonalOneArgRemainsTeleportWhenOriginUnknown()
	{
		Assert.assertTrue(TransportType.isTeleport(TransportType.SEASONAL_TRANSPORT));
	}

	@Test
	public void spellTeleportIgnoresOrigin()
	{
		WorldPoint origin = new WorldPoint(1, 1, 0);
		Assert.assertTrue(TransportType.isTeleport(TransportType.TELEPORTATION_SPELL, origin));
		Assert.assertTrue(TransportType.isTeleport(TransportType.TELEPORTATION_SPELL, null));
	}
}
