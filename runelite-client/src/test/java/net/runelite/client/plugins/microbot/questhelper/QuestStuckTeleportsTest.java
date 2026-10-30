package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/** Dragon Slayer I: stuck in the Dwarven Mines magic-door room with Port Sarim Jail 6,600 tiles away. */
public class QuestStuckTeleportsTest
{
	private static final WorldPoint MAGIC_DOOR_ROOM = new WorldPoint(3056, 9841, 0);
	private static final WorldPoint PORT_SARIM_JAIL = new WorldPoint(3015, 3197, 0);

	@Test
	public void theFaladorTabletIsTheOneNearestPortSarim()
	{
		QuestStuckTeleports.Option option = QuestStuckTeleports.choose(MAGIC_DOOR_ROOM, PORT_SARIM_JAIL,
			List.of("Falador teleport", "Varrock teleport", "Coins"), List.of());
		assertEquals("falador teleport", option.item);
		assertEquals("Break", option.action);
		assertFalse(option.worn);
	}

	@Test
	public void wornJewelleryCountsAndIsUsedByItsOwnAction()
	{
		QuestStuckTeleports.Option option = QuestStuckTeleports.choose(PORT_SARIM_JAIL, new WorldPoint(3090, 3500, 0),
			List.of(), List.of("Amulet of glory(4)"));
		assertEquals("amulet of glory", option.item);
		assertTrue(option.worn);
		assertEquals("Edgeville", option.action);
	}

	@Test
	public void nothingOwnedMeansNoTeleport()
	{
		assertNull(QuestStuckTeleports.choose(MAGIC_DOOR_ROOM, PORT_SARIM_JAIL, List.of("Coins", "Lobster pot"), List.of()));
	}

	@Test
	public void aNearGoalIsNeverTeleportedTo()
	{
		assertNull(QuestStuckTeleports.choose(new WorldPoint(3010, 3190, 0), PORT_SARIM_JAIL, List.of("Falador teleport"), List.of()));
	}

	@Test
	public void aTeleportThatLandsNoNearerIsNotUsed()
	{
		// standing in Falador, heading for Port Sarim Jail ~190 tiles away: the Falador tablet lands where we are
		assertNull(QuestStuckTeleports.choose(new WorldPoint(2966, 3379, 0), PORT_SARIM_JAIL, List.of("Falador teleport"), List.of()));
	}

	/** A glory amulet in the inventory is not worn: it must not be picked as the worn option. */
	@Test
	public void jewelleryInTheInventoryIsNotWorn()
	{
		assertNull(QuestStuckTeleports.choose(PORT_SARIM_JAIL, new WorldPoint(3090, 3500, 0), List.of("Amulet of glory(4)"), List.of()));
	}

	/** Underground y is 6,400 north of the surface: a catacomb under Lumbridge is not nearer Edgeville than the goal. */
	@Test
	public void undergroundPlayerIsComparedByTheSurfaceAbove()
	{
		WorldPoint lumbridgeCatacombs = new WorldPoint(3150, 9600, 0);
		WorldPoint lumbridgeGoal = new WorldPoint(3222, 3218, 0);
		assertNull(QuestStuckTeleports.choose(lumbridgeCatacombs, lumbridgeGoal, List.of(), List.of("Amulet of glory(4)")));
	}

	/** Only real underground maps read as the surface above; surface regions in the 6,400-7,500 band do not shift. */
	@Test
	public void onlyRealUndergroundIsShifted()
	{
		assertEquals(0, QuestStuckTeleports.distance(new WorldPoint(3300, 9800, 0), new WorldPoint(3300, 3400, 0)));
		assertEquals(6400, QuestStuckTeleports.distance(new WorldPoint(3300, 7000, 0), new WorldPoint(3300, 600, 0)));
	}
}
