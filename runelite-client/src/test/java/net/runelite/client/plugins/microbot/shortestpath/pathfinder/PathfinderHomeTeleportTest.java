package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportExecutionRegistry;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportPlanningPolicy;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathfinderHomeTeleportTest
{
	@Test
	public void everyRegisteredHomeTeleportIsZeroRuneUsable()
	{
		PathfinderConfig config = new PathfinderConfig(
			null, Collections.emptyMap(), Collections.emptyList(), null, null,
			Rs2TransportPlanningPolicy.INSTANCE);

		for (TransportExecutionRegistry.HomeTeleport homeTeleport
			: TransportExecutionRegistry.HomeTeleport.values())
		{
			assertTrue(homeTeleport.getDisplayName(),
				config.isTeleportationSpellUsable(spell(homeTeleport.getDisplayName())));
		}
		assertFalse(config.isTeleportationSpellUsable(spell("Unknown Home Teleport")));
	}

	private static Transport spell(String displayInfo)
	{
		return new Transport(
			null, new WorldPoint(3200, 3200, 0), displayInfo,
			TransportType.TELEPORTATION_SPELL, false, 1);
	}
}
