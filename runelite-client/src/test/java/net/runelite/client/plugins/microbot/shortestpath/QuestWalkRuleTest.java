package net.runelite.client.plugins.microbot.shortestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathEdge;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathTerminationReason;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

/** Quest mode never uses ships, spirit trees and the like when the target is reachable on foot. */
public class QuestWalkRuleTest
{
	private static SplitFlagMap collisionMap;
	private static HashMap<WorldPoint, Set<Transport>> all;

	@BeforeClass
	public static void load()
	{
		collisionMap = SplitFlagMap.fromResources();
		all = Transport.loadAllFromResources();
	}

	@After
	public void off()
	{
		QuestWalkRule.setActive(false);
	}

	/** Transports a fresh account cannot use: skill, quest or item requirements, grapples and agility shortcuts. */
	private java.util.function.Predicate<Transport> excluded = t -> false;

	private static boolean needsSomethingAFreshAccountLacks(Transport t)
	{
		if (t.getType() == TransportType.GRAPPLE_SHORTCUT || t.getType() == TransportType.AGILITY_SHORTCUT
			|| !t.getQuests().isEmpty() || !t.getItemRequirements().isEmpty())
		{
			return true;
		}
		for (int level : t.getSkillLevels())
		{
			if (level > 0)
			{
				return true;
			}
		}
		return false;
	}

	private Pathfinder run(WorldPoint from, WorldPoint to) throws Exception
	{
		return run(from, to, new Transport[0]);
	}

	/** Runs a quest walk where the player owns {@code teleports} (their item teleports are usable). */
	@SuppressWarnings("unchecked")
	private Pathfinder run(WorldPoint from, WorldPoint to, Transport... teleports) throws Exception
	{
		PathfinderConfig config = new PathfinderConfig(collisionMap, all, Collections.emptyList(), null, null);
		Field f = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
		f.setAccessible(true);
		f.setLong(config, 15_000);
		for (Map.Entry<WorldPoint, Set<Transport>> e : all.entrySet())
		{
			if (e.getKey() != null)
			{
				Set<Transport> kept = new java.util.HashSet<>(e.getValue());
				kept.removeIf(excluded);
				if (kept.isEmpty())
				{
					continue;
				}
				config.getTransports().put(e.getKey(), kept);
				config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(e.getKey()), kept);
			}
		}
		Field owned = PathfinderConfig.class.getDeclaredField("usableTeleports");
		owned.setAccessible(true);
		for (Transport teleport : teleports)
		{
			((Set<Transport>) owned.get(config)).add(teleport);
		}
		Pathfinder pf = new Pathfinder(config, from, to);
		// a quest walk: the rule is on, crossing mode is on and the quest loop is walking
		QuestWalkRule.setCrossingMode(true);
		QuestWalkRule.markQuestThread(true);
		boolean walking = QuestWalkRule.beginQuestWalk(to);
		try
		{
			pf.run();
		}
		finally
		{
			QuestWalkRule.endQuestWalk(walking);
			QuestWalkRule.markQuestThread(false);
		}
		return pf;
	}

	private void assertNoRestricted(WorldPoint from, WorldPoint to) throws Exception
	{
		QuestWalkRule.setActive(true);
		Pathfinder pf = run(from, to);
		assertFalse(pf.getPath().isEmpty());
		assertTrue("ended at " + pf.getPath().get(pf.getPath().size() - 1),
			pf.getPath().get(pf.getPath().size() - 1).distanceTo2D(to) <= 3);
		for (PathEdge e : pf.getPathEdges())
		{
			assertFalse("used " + (e.isTransport() ? e.getTransport().getType() : ""),
				e.isTransport() && QuestWalkRule.isRestrictedType(e.getTransport().getType(), e.getTransport().getOrigin()));
		}
	}

	@Test
	public void geToRimmington() throws Exception
	{
		assertNoRestricted(new WorldPoint(3164, 3485, 0), new WorldPoint(2968, 3205, 0));
	}

	@Test
	public void rimmingtonToFaladorBank() throws Exception
	{
		assertNoRestricted(new WorldPoint(2969, 3206, 0), new WorldPoint(2946, 3368, 0));
	}

	@Test
	public void lumbridgeToDraynor() throws Exception
	{
		assertNoRestricted(new WorldPoint(3222, 3218, 0), new WorldPoint(3092, 3243, 0));
	}

	/** Hosidius (Zeah) to Elena in East Ardougne: no route on foot, so the walk crosses and says what it used. */
	@Test
	public void zeahReachesArdougneByCrossingAndNeverBySurpriseTeleport() throws Exception
	{
		QuestWalkRule.setActive(true);
		WorldPoint elena = new WorldPoint(2592, 3336, 0);
		Pathfinder pf = run(new WorldPoint(1884, 3481, 0), elena);
		assertFalse(pf.getPath().isEmpty());
		assertTrue(pf.getPath().get(pf.getPath().size() - 1).distanceTo2D(elena) <= 3);
		for (PathEdge e : pf.getPathEdges())
		{
			assertFalse("used " + e.getTransport() + " " + e.getFrom() + ">" + e.getTo(),
				e.isTransport() && TransportType.isTeleport(e.getTransport().getType(), e.getTransport().getOrigin()));
			if (e.isTransport() && e.getTransport().getType() == TransportType.SHIP)
			{
				String ferry = e.getTransport().getName();
				assertTrue("unexpected ferry " + ferry, "Veos".equals(ferry) || "Cabin Boy Herbert".equals(ferry));
			}
		}
	}

	/** Nothing reaches this tile: the on-foot result stands, with no boat or teleport left in the partial path. */
	@Test
	public void unreachableTargetLeavesNoRestrictedTransportInThePath() throws Exception
	{
		QuestWalkRule.setActive(true);
		// deep sea south of Karamja: on the map, walkable from nowhere, and no transport lands there
		Pathfinder pf = run(new WorldPoint(3222, 3218, 0), new WorldPoint(2900, 2800, 0));
		assertNotEquals(PathTerminationReason.TARGET_REACHED, pf.getTerminationReason());
		for (PathEdge e : pf.getPathEdges())
		{
			assertFalse("kept " + e.getTransport() + " in a failed search",
				e.isTransport() && QuestWalkRule.isRestricted(e.getTransport()));
		}
	}

	/** #89: Varrock (Guidor's house) to Elena in East Ardougne stays on the ground road, never over Falador castle. */
	@Test
	public void varrockToArdougneNeverUsesTheFaladorCastleStaircases() throws Exception
	{
		QuestWalkRule.setActive(true);
		Pathfinder pf = run(new WorldPoint(3283, 3382, 0), new WorldPoint(2592, 3336, 0));
		assertEquals(PathTerminationReason.TARGET_REACHED, pf.getTerminationReason());
		for (PathEdge e : pf.getPathEdges())
		{
			if (!e.isTransport())
			{
				continue;
			}
			int id = e.getTransport().getObjectId();
			assertFalse("used Falador castle object " + id + " " + e.getFrom() + ">" + e.getTo(), id >= 24070 && id <= 24075);
			boolean inFalador = e.getFrom().getX() > 2940 && e.getFrom().getX() < 3015 && e.getFrom().getY() < 3360;
			assertFalse("left the ground floor in Falador", inFalador && e.getTo().getPlane() > 0);
		}
	}

	private static Transport itemTeleport(String display, WorldPoint destination)
	{
		return new Transport(destination, display, TransportType.TELEPORTATION_ITEM, true, 31,
			Collections.<Set<Integer>>emptySet());
	}

	private static boolean usesTeleport(Pathfinder pf, String display)
	{
		for (PathEdge e : pf.getPathEdges())
		{
			if (e.isTransport() && display.equals(e.getTransport().getDisplayInfo()))
			{
				return true;
			}
		}
		return false;
	}

	private static final WorldPoint ARDOUGNE_CASTLE = new WorldPoint(2660, 3300, 0);
	private static final WorldPoint GRAND_EXCHANGE = new WorldPoint(3166, 3485, 0);

	/** Druidic Ritual: Ardougne to the Grand Exchange is long on foot, and the Ring of wealth lands beside it. */
	@Test
	public void aLongTripUsesTheRingOfWealthTheTeleportOwned() throws Exception
	{
		QuestWalkRule.setActive(true);
		Transport ring = itemTeleport("Ring of wealth: Grand Exchange", new WorldPoint(3163, 3478, 0));
		Pathfinder pf = run(ARDOUGNE_CASTLE, GRAND_EXCHANGE, ring);
		assertEquals(PathTerminationReason.TARGET_REACHED, pf.getTerminationReason());
		assertTrue("expected the ring in " + pf.getPathEdges(), usesTeleport(pf, "Ring of wealth: Grand Exchange"));
	}

	@Test
	public void aLongTripUsesTheAmuletOfGloryTheTeleportOwned() throws Exception
	{
		QuestWalkRule.setActive(true);
		Transport glory = itemTeleport("Amulet of glory: Edgeville", new WorldPoint(3087, 3496, 0));
		Pathfinder pf = run(ARDOUGNE_CASTLE, GRAND_EXCHANGE, glory);
		assertTrue("expected the amulet in " + pf.getPathEdges(), usesTeleport(pf, "Amulet of glory: Edgeville"));
	}

	@Test
	public void aShortTripStaysOnFootEvenWithJewellery() throws Exception
	{
		QuestWalkRule.setActive(true);
		Transport ring = itemTeleport("Ring of wealth: Grand Exchange", new WorldPoint(3163, 3478, 0));
		Pathfinder pf = run(new WorldPoint(3222, 3218, 0), new WorldPoint(3092, 3243, 0), ring);
		assertFalse(usesTeleport(pf, "Ring of wealth: Grand Exchange"));
	}

	@Test
	public void withoutJewelleryALongTripStaysOnFoot() throws Exception
	{
		QuestWalkRule.setActive(true);
		Pathfinder pf = run(ARDOUGNE_CASTLE, GRAND_EXCHANGE);
		for (PathEdge e : pf.getPathEdges())
		{
			assertFalse("used " + e.getTransport(),
				e.isTransport() && QuestWalkRule.isRestricted(e.getTransport()));
		}
	}

	@Test
	public void otherItemTeleportsAreNotJewellery() throws Exception
	{
		QuestWalkRule.setActive(true);
		Transport tablet = itemTeleport("Varrock Teleport (tablet)", new WorldPoint(3213, 3424, 0));
		Pathfinder pf = run(ARDOUGNE_CASTLE, GRAND_EXCHANGE, tablet);
		assertFalse(usesTeleport(pf, "Varrock Teleport (tablet)"));
	}

	@Test
	public void aTeleportThatLandsFartherAwayIsNotTaken() throws Exception
	{
		QuestWalkRule.setActive(true);
		// Yanille is a long walk from Ardougne, but the ring lands at the Grand Exchange, far the other way
		Transport ring = itemTeleport("Ring of wealth: Grand Exchange", new WorldPoint(3163, 3478, 0));
		Pathfinder pf = run(ARDOUGNE_CASTLE, new WorldPoint(2606, 3093, 0), ring);
		assertFalse(usesTeleport(pf, "Ring of wealth: Grand Exchange"));
	}

	private static final WorldPoint RIMMINGTON_COAST = new WorldPoint(2924, 3205, 0);
	private static final WorldPoint KARAMJA_MONKEYS = new WorldPoint(2887, 3168, 0);

	/** Rag and Bone Man I, "Kill a monkey on Karamja", walking from the Rimmington coast: an Amulet of glory is used. */
	@Test
	public void karamjaFromTheRimmingtonCoastUsesTheOwnedGlory() throws Exception
	{
		QuestWalkRule.setActive(true);
		QuestWalkRule.clearFare();
		excluded = QuestWalkRuleTest::needsSomethingAFreshAccountLacks;
		Transport glory = itemTeleport("Amulet of glory: Karamja", new WorldPoint(2918, 3176, 0));
		Pathfinder pf = run(RIMMINGTON_COAST, KARAMJA_MONKEYS, glory);
		assertEquals(PathTerminationReason.TARGET_REACHED, pf.getTerminationReason());
		assertTrue("expected the amulet in " + pf.getPathEdges(), usesTeleport(pf, "Amulet of glory: Karamja"));
		assertEquals("no ship, no fare", 0, QuestWalkRule.pendingFare());
	}

	/** Without jewellery the Port Sarim ship is the crossing, it costs 30 coins and the quest loop is told to fetch them. */
	@Test
	public void karamjaWithoutJewelleryCrossesByTheSeamanAndNotesTheFare() throws Exception
	{
		QuestWalkRule.setActive(true);
		QuestWalkRule.clearFare();
		excluded = QuestWalkRuleTest::needsSomethingAFreshAccountLacks;
		Pathfinder pf = run(RIMMINGTON_COAST, KARAMJA_MONKEYS);
		assertEquals(PathTerminationReason.TARGET_REACHED, pf.getTerminationReason());
		boolean paidShip = false;
		for (PathEdge e : pf.getPathEdges())
		{
			if (e.isTransport() && e.getTransport().getType() == TransportType.SHIP)
			{
				paidShip |= "Coins".equalsIgnoreCase(e.getTransport().getCurrencyName())
					&& e.getTransport().getCurrencyAmount() == 30;
			}
			assertFalse("no teleport expected: " + e.getTransport(),
				e.isTransport() && TransportType.isTeleport(e.getTransport().getType(), e.getTransport().getOrigin()));
		}
		StringBuilder used = new StringBuilder();
		for (PathEdge e : pf.getPathEdges())
		{
			if (e.isTransport())
			{
				used.append(e.getTransport().getType()).append(' ').append(e.getTransport().getName()).append(' ')
					.append(e.getTransport().getDisplayInfo()).append(" cur=").append(e.getTransport().getCurrencyName())
					.append(e.getTransport().getCurrencyAmount()).append("; ");
			}
		}
		assertTrue("expected a 30 coin ship in " + used, paidShip);
		assertEquals("the 30 coin fare is counted for the preflight: " + used, 30, QuestWalkRule.pendingFare());
		QuestWalkRule.clearFare();
	}

	/**
	 * Seaman Thresnor (NPC 14982) now runs the Port Sarim crossing and only offers "Talk-to" and "Travel" (the old
	 * "Pay-fare" is gone). With the older seamen out of the picture the route must still be found through his row.
	 */
	@Test
	public void karamjaCrossesByThresnorWhenTheOlderSeamenAreGone() throws Exception
	{
		QuestWalkRule.setActive(true);
		QuestWalkRule.clearFare();
		excluded = t -> needsSomethingAFreshAccountLacks(t)
			|| (t.getType() == TransportType.SHIP && t.getName() != null
			&& (t.getName().equals("Captain Tobias") || t.getName().equals("Seaman Lorris")));
		Pathfinder pf = run(new WorldPoint(2923, 3206, 0), new WorldPoint(2879, 3161, 0));
		assertEquals(PathTerminationReason.TARGET_REACHED, pf.getTerminationReason());
		Transport used = null;
		for (PathEdge e : pf.getPathEdges())
		{
			if (e.isTransport() && e.getTransport().getType() == TransportType.SHIP)
			{
				used = e.getTransport();
			}
		}
		assertNotEquals("no ship used in " + pf.getPathEdges(), null, used);
		assertEquals("Seaman Thresnor", used.getName());
		assertEquals(14982, used.getObjectId());
		assertEquals("Travel", used.getAction());
		assertEquals(30, used.getCurrencyAmount());
		assertEquals(30, QuestWalkRule.pendingFare());
		QuestWalkRule.clearFare();
	}

	/** From Karamja to Draynor Village the owned Amulet of glory is the way off the island. */
	@Test
	public void draynorFromKaramjaUsesTheOwnedGlory() throws Exception
	{
		QuestWalkRule.setActive(true);
		QuestWalkRule.clearFare();
		excluded = QuestWalkRuleTest::needsSomethingAFreshAccountLacks;
		Transport glory = itemTeleport("Amulet of glory: Draynor Village", new WorldPoint(3105, 3251, 0));
		Pathfinder pf = run(new WorldPoint(2880, 3160, 0), new WorldPoint(3092, 3243, 0), glory);
		assertEquals(PathTerminationReason.TARGET_REACHED, pf.getTerminationReason());
		assertTrue("expected the amulet in " + pf.getPathEdges(), usesTeleport(pf, "Amulet of glory: Draynor Village"));
		assertEquals("no ship fare when the amulet does it", 0, QuestWalkRule.pendingFare());
	}

	@Test
	public void islandFallsBackToTheShip() throws Exception
	{
		QuestWalkRule.setActive(true);
		Pathfinder pf = run(new WorldPoint(3029, 3217, 0), new WorldPoint(3724, 3808, 0));
		assertFalse(pf.getPath().isEmpty());
		boolean ship = false;
		for (PathEdge e : pf.getPathEdges())
		{
			ship |= e.isTransport() && (e.getTransport().getType() == TransportType.SHIP
				|| e.getTransport().getType() == TransportType.CHARTER_SHIP || e.getTransport().getType() == TransportType.BOAT);
		}
		StringBuilder sb = new StringBuilder(); for (PathEdge e : pf.getPathEdges()) if (e.isTransport()) sb.append(e.getTransport().getDisplayInfo()).append(e.getTransport().getType()).append(e.getFrom()).append(">").append(e.getTo()).append("; ");
		assertTrue("expected a ship to Fossil Island: " + sb + " end=" + pf.getPath().get(pf.getPath().size() - 1) + " reason=" + pf.getTerminationReason(), ship);
		assertEquals(true, pf.getPath().get(pf.getPath().size() - 1).distanceTo2D(new WorldPoint(3724, 3808, 0)) <= 3);
	}

	/** Varrock to the Clock Tower: the route never climbs Falador castle (its upper floors border open air and a moat). */
	@Test
	public void varrockToClockTowerNeverEntersFaladorCastleUpstairs() throws Exception
	{
		QuestWalkRule.setActive(true);
		excluded = QuestWalkRuleTest::needsSomethingAFreshAccountLacks;
		Pathfinder pf = run(new WorldPoint(3213, 3428, 0), new WorldPoint(2568, 3249, 0));
		assertFalse(pf.getPath().isEmpty());
		for (WorldPoint p : pf.getPath())
		{
			boolean inCastle = p.getX() >= 2955 && p.getX() <= 3000 && p.getY() >= 3318 && p.getY() <= 3350;
			assertFalse("upstairs in Falador castle at " + p, inCastle && p.getPlane() > 0);
		}
	}

	/** Dragon Slayer I: the way out of the Dwarven Mines magic-door room is the magic door itself. */
	@Test
	public void theMagicDoorRoomCanBeLeftThroughItsDoor() throws Exception
	{
		QuestWalkRule.setActive(true);
		Pathfinder pf = run(new WorldPoint(3056, 9841, 0), new WorldPoint(3046, 9838, 0));
		assertEquals(PathTerminationReason.TARGET_REACHED, pf.getTerminationReason());
		boolean usesDoor = false;
		for (PathEdge e : pf.getPathEdges())
		{
			usesDoor |= e.isTransport() && e.getTransport().getOrigin().equals(new WorldPoint(3051, 9839, 0));
		}
		assertTrue("expected the magic door in " + pf.getPathEdges(), usesDoor);
	}

	/** Dragon Slayer I's lesser demon fight: the step regressed and the walker planned a Ring of wealth teleport out. */
	@Test
	public void noJewelleryTeleportWhileFightingOrJustAfter() throws Exception
	{
		assertTrue(QuestWalkRule.shouldHoldLongTravel(true, 0, 1_000));
		assertTrue(QuestWalkRule.shouldHoldLongTravel(false, 1_000, 10_000));
		assertFalse(QuestWalkRule.shouldHoldLongTravel(false, 1_000, 1_000 + QuestWalkRule.COMBAT_HOLD_MS));
		assertFalse(QuestWalkRule.shouldHoldLongTravel(false, 0, 100_000));

		QuestWalkRule.setActive(true);
		Transport ring = itemTeleport("Ring of wealth: Grand Exchange", new WorldPoint(3163, 3478, 0));
		QuestWalkRule.setLongTravelHeld(true);
		try
		{
			Pathfinder held = run(ARDOUGNE_CASTLE, GRAND_EXCHANGE, ring);
			assertFalse("held: " + held.getPathEdges(), usesTeleport(held, "Ring of wealth: Grand Exchange"));
		}
		finally
		{
			QuestWalkRule.setLongTravelHeld(false);
		}
		Pathfinder free = run(ARDOUGNE_CASTLE, GRAND_EXCHANGE, ring);
		assertTrue(usesTeleport(free, "Ring of wealth: Grand Exchange"));
	}

	/** The on-foot search fails (an island): while fighting, the owned glory must not be the way off it either. */
	@Test
	public void noOwnedGloryOffAnIslandWhileFighting() throws Exception
	{
		QuestWalkRule.setActive(true);
		QuestWalkRule.clearFare();
		excluded = QuestWalkRuleTest::needsSomethingAFreshAccountLacks;
		Transport glory = itemTeleport("Amulet of glory: Draynor Village", new WorldPoint(3105, 3251, 0));
		QuestWalkRule.setLongTravelHeld(true);
		try
		{
			Pathfinder pf = run(new WorldPoint(2880, 3160, 0), new WorldPoint(3092, 3243, 0), glory);
			assertFalse("held: " + pf.getPathEdges(), usesTeleport(pf, "Amulet of glory: Draynor Village"));
		}
		finally
		{
			QuestWalkRule.setLongTravelHeld(false);
		}
	}
}
