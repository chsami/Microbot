package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerTransportsCombatTest
{
	@Test
	public void executionRechecksCombatBeforeHomeTeleport()
	{
		assertFalse(Rs2WalkerTransports.isTeleportSpellUsableDuringCombat(
			spell("Lumbridge Home Teleport"), true));
		assertTrue(Rs2WalkerTransports.isTeleportSpellUsableDuringCombat(
			spell("Lumbridge Home Teleport"), false));
		assertTrue(Rs2WalkerTransports.isTeleportSpellUsableDuringCombat(
			spell("Varrock Teleport"), true));
	}

	private static Transport spell(String displayInfo)
	{
		return new Transport(
			null, new WorldPoint(3200, 3200, 0), displayInfo,
			TransportType.TELEPORTATION_SPELL, false, 1);
	}
}
