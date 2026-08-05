package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import shortestpath.transport.TransportType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class UpstreamRoutePlannerTest
{
	private static final WorldPoint ORIGIN = new WorldPoint(3200, 3200, 0);
	private static final WorldPoint DESTINATION = new WorldPoint(3201, 3200, 0);

	@Test
	public void anchoredTypeProjectionIsExplicitAndComplete()
	{
		Map<Rs2TransportType, TransportType> expected = new EnumMap<>(Rs2TransportType.class);
		expected.put(Rs2TransportType.TRANSPORT, TransportType.TRANSPORT);
		expected.put(Rs2TransportType.AGILITY_SHORTCUT, TransportType.AGILITY_SHORTCUT);
		expected.put(Rs2TransportType.GRAPPLE_SHORTCUT, TransportType.GRAPPLE_SHORTCUT);
		expected.put(Rs2TransportType.BOAT, TransportType.BOAT);
		expected.put(Rs2TransportType.CANOE, TransportType.CANOE);
		expected.put(Rs2TransportType.CHARTER_SHIP, TransportType.CHARTER_SHIP);
		expected.put(Rs2TransportType.SHIP, TransportType.SHIP);
		expected.put(Rs2TransportType.FAIRY_RING, TransportType.FAIRY_RING);
		expected.put(Rs2TransportType.QUETZAL, TransportType.QUETZAL);
		expected.put(Rs2TransportType.QUETZAL_WHISTLE, TransportType.QUETZAL_WHISTLE);
		expected.put(Rs2TransportType.GNOME_GLIDER, TransportType.GNOME_GLIDER);
		expected.put(Rs2TransportType.MINECART, TransportType.MINECART);
		expected.put(Rs2TransportType.POH, TransportType.TRANSPORT);
		expected.put(Rs2TransportType.SPIRIT_TREE, TransportType.SPIRIT_TREE);
		expected.put(Rs2TransportType.TELEPORTATION_BOX, TransportType.TELEPORTATION_BOX);
		expected.put(Rs2TransportType.TELEPORTATION_LEVER, TransportType.TELEPORTATION_LEVER);
		expected.put(Rs2TransportType.TELEPORTATION_PORTAL, TransportType.TELEPORTATION_PORTAL);
		expected.put(Rs2TransportType.TELEPORTATION_PORTAL_POH, TransportType.TELEPORTATION_PORTAL_POH);
		expected.put(Rs2TransportType.TELEPORTATION_MINIGAME, TransportType.TELEPORTATION_MINIGAME);
		expected.put(Rs2TransportType.TELEPORTATION_ITEM, TransportType.TELEPORTATION_ITEM);
		expected.put(Rs2TransportType.TELEPORTATION_SPELL, TransportType.TELEPORTATION_SPELL);
		expected.put(Rs2TransportType.TELEPORTATION_SPELL_HOME, TransportType.TELEPORTATION_SPELL_HOME);
		expected.put(Rs2TransportType.WILDERNESS_OBELISK, TransportType.WILDERNESS_OBELISK);
		expected.put(Rs2TransportType.MAGIC_CARPET, TransportType.MAGIC_CARPET);
		expected.put(Rs2TransportType.HOT_AIR_BALLOON, TransportType.HOT_AIR_BALLOON);
		expected.put(Rs2TransportType.MAGIC_MUSHTREE, TransportType.MAGIC_MUSHTREE);
		expected.put(Rs2TransportType.SEASONAL_TRANSPORT, TransportType.SEASONAL_TRANSPORTS);
		expected.put(Rs2TransportType.NPC, TransportType.TRANSPORT);

		assertEquals("every supported boundary type must declare an anchored projection",
			Rs2TransportType.values().length - 1, expected.size());
		for (Map.Entry<Rs2TransportType, TransportType> entry : expected.entrySet())
		{
			assertEquals(entry.getKey().name(), entry.getValue(),
				UpstreamRoutePlanner.mapType(edge(ORIGIN, entry.getKey())));
		}
		assertRejected(edge(ORIGIN, Rs2TransportType.UNKNOWN));
	}

	@Test
	public void originlessProjectionOnlyAdmitsReviewedTeleportCategories()
	{
		Map<Rs2TransportType, TransportType> expected = new EnumMap<>(Rs2TransportType.class);
		expected.put(Rs2TransportType.QUETZAL_WHISTLE, TransportType.QUETZAL_WHISTLE);
		expected.put(Rs2TransportType.SEASONAL_TRANSPORT, TransportType.TELEPORTATION_ITEM);
		expected.put(Rs2TransportType.TELEPORTATION_ITEM, TransportType.TELEPORTATION_ITEM);
		expected.put(Rs2TransportType.TELEPORTATION_MINIGAME, TransportType.TELEPORTATION_MINIGAME);
		expected.put(Rs2TransportType.TELEPORTATION_SPELL, TransportType.TELEPORTATION_SPELL);
		expected.put(Rs2TransportType.TELEPORTATION_SPELL_HOME, TransportType.TELEPORTATION_SPELL_HOME);

		for (Rs2TransportType type : Rs2TransportType.values())
		{
			Rs2TransportEdge edge = edge(null, type);
			if (expected.containsKey(type))
			{
				assertEquals(type.name(), expected.get(type), UpstreamRoutePlanner.mapType(edge));
			}
			else
			{
				assertRejected(edge);
			}
		}
	}

	private static Rs2TransportEdge edge(WorldPoint origin, Rs2TransportType type)
	{
		return new Rs2TransportEdge(
			origin,
			DESTINATION,
			type,
			Rs2TransportExecutor.OBJECT,
			Rs2TerminalTravelMode.UNSUPPORTED,
			"test",
			"Use",
			"test",
			1,
			1,
			origin == null,
			false,
			false,
			0,
			"",
			0,
			Collections.emptyList());
	}

	private static void assertRejected(Rs2TransportEdge edge)
	{
		try
		{
			UpstreamRoutePlanner.mapType(edge);
			fail("expected unsupported projection for " + edge.getType());
		}
		catch (IllegalArgumentException expected)
		{
			// expected
		}
	}
}
