package net.runelite.client.plugins.microbot.shortestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathTerminationReason;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule.Stage;
import org.junit.After;
import org.junit.Test;

/** The stage ladder (on foot, named routes, crossings, teleports) and the transport allowlist of each stage. */
public class QuestWalkStageTest
{
	private static final WorldPoint A = new WorldPoint(3055, 3245, 0);
	private static final WorldPoint B = new WorldPoint(1824, 3695, 1);
	private static final boolean ARDOUGNE = true;

	@After
	public void off()
	{
		QuestWalkRule.setActive(false);
		QuestWalkRule.setOnFootOnly(false);
	}

	private static Transport npc(TransportType type, String name, WorldPoint from, WorldPoint to)
	{
		return new Transport(from, to, "x", type, true, "Travel", name, 1);
	}

	private static Transport plain(TransportType type)
	{
		return new Transport(A, B, "x", type, true, 4);
	}

	private static Transport item(String display)
	{
		return new Transport(null, B, display, TransportType.TELEPORTATION_ITEM, true, 4);
	}

	@Test
	public void stagesWidenInOrderThenGiveUp()
	{
		assertEquals(Stage.OWNED_JEWELLERY, Stage.ON_FOOT.next());
		assertEquals(Stage.NAMED_ROUTES, Stage.OWNED_JEWELLERY.next());
		assertEquals(Stage.CROSSINGS, Stage.NAMED_ROUTES.next());
		assertEquals(Stage.TELEPORTS, Stage.CROSSINGS.next());
		assertNull(Stage.TELEPORTS.next());
	}

	@Test
	public void unrestrictedTransportsAreAlwaysPermitted()
	{
		Transport shortcut = plain(TransportType.AGILITY_SHORTCUT);
		for (Stage stage : Stage.values())
		{
			assertTrue(QuestWalkRule.permits(stage, shortcut, ARDOUGNE));
		}
	}

	@Test
	public void onFootPermitsNoRestrictedTransport()
	{
		assertFalse(QuestWalkRule.permits(Stage.ON_FOOT, npc(TransportType.SHIP, "Veos", A, B), ARDOUGNE));
		assertFalse(QuestWalkRule.permits(Stage.ON_FOOT, plain(TransportType.QUETZAL), ARDOUGNE));
		assertFalse(QuestWalkRule.permits(Stage.ON_FOOT, item("Ring of wealth: Grand Exchange"), ARDOUGNE));
		assertFalse(QuestWalkRule.permits(Stage.ON_FOOT, plain(TransportType.FAIRY_RING), ARDOUGNE));
	}

	@Test
	public void namedRoutesAreVeosAndTheSkillsNecklaceOnly()
	{
		assertTrue(QuestWalkRule.permits(Stage.NAMED_ROUTES, npc(TransportType.SHIP, "Veos", A, B), ARDOUGNE));
		assertTrue(QuestWalkRule.permits(Stage.NAMED_ROUTES, npc(TransportType.SHIP, "Cabin Boy Herbert", A, B), ARDOUGNE));
		assertTrue(QuestWalkRule.permits(Stage.NAMED_ROUTES, item("Skills necklace: Fishing Guild"), ARDOUGNE));
		assertFalse(QuestWalkRule.permits(Stage.NAMED_ROUTES, item("Skills necklace: Mining Guild"), ARDOUGNE));
		assertFalse(QuestWalkRule.permits(Stage.NAMED_ROUTES, npc(TransportType.SHIP, "Captain Barnaby", A, B), ARDOUGNE));
		assertFalse(QuestWalkRule.permits(Stage.NAMED_ROUTES, npc(TransportType.CHARTER_SHIP, "Trader Crewmember", A, B), ARDOUGNE));
	}

	@Test
	public void ownedJewelleryStagePermitsOnlyTheJewelleryTeleports()
	{
		assertTrue(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, item("Ring of wealth: Grand Exchange"), false));
		assertTrue(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, item("Amulet of glory: Edgeville"), false));
		assertTrue(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, item("Games necklace: Burthorpe"), false));
		assertTrue(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, item("Ring of dueling: Castle Wars"), false));
		assertFalse(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, item("Varrock Teleport (tablet)"), false));
		assertFalse(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, npc(TransportType.SHIP, "Veos", A, B), false));
		assertFalse(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, plain(TransportType.QUETZAL), false));
		assertFalse(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, plain(TransportType.FAIRY_RING), false));
		assertFalse(QuestWalkRule.permits(Stage.OWNED_JEWELLERY,
			new Transport(null, B, "Varrock Teleport", TransportType.TELEPORTATION_SPELL, true, 4), false));
		assertFalse(QuestWalkRule.permits(Stage.OWNED_JEWELLERY,
			new Transport(null, B, "Ring of wealth: x", TransportType.TELEPORTATION_MINIGAME, true, 4), false));
	}

	@Test
	public void skillsNecklaceJoinsTheJewelleryStageForArdougneTargetsOnly()
	{
		Transport necklace = item("Skills necklace: Fishing Guild");
		assertTrue(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, necklace, true));
		assertFalse(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, necklace, false));
		assertFalse(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, item("Skills necklace: Mining Guild"), true));
	}

	@Test
	public void jewelleryReplacesAWalkOnlyWhenTheWalkIsLongAndTheTeleportMuchCheaper()
	{
		assertFalse("short walk", QuestWalkRule.jewelleryWorthTrying(120));
		assertTrue(QuestWalkRule.jewelleryWorthTrying(400));
		assertTrue(QuestWalkRule.jewelleryBeatsWalking(400, 100));
		assertTrue(QuestWalkRule.jewelleryBeatsWalking(400, 239));
		assertFalse("only just cheaper", QuestWalkRule.jewelleryBeatsWalking(400, 300));
		assertFalse("lands farther away", QuestWalkRule.jewelleryBeatsWalking(400, 500));
		assertFalse("short walk is never replaced", QuestWalkRule.jewelleryBeatsWalking(100, 5));
		assertFalse("no jewellery route found", QuestWalkRule.jewelleryBeatsWalking(400, -1));
	}

	@Test
	public void jewelleryAnnouncementNamesTheItemAndDestination()
	{
		assertEquals("Quest walk: teleporting via [Ring of wealth: Grand Exchange] toward T",
			QuestWalkRule.describe(Stage.OWNED_JEWELLERY, "T", Collections.singleton("Ring of wealth: Grand Exchange")));
	}

	@Test
	public void thePortSarimToKaramjaSeamanIsANamedRoute()
	{
		Transport tobias = new Transport(A, B, "Musa Point", TransportType.SHIP, false, "Musa Point", "Captain Tobias", 3644);
		Transport lorris = new Transport(A, B, "Musa Point", TransportType.SHIP, false, "Musa Point", "Seaman Lorris", 14980);
		Transport pandemonium = new Transport(A, B, "The Pandemonium", TransportType.SHIP, true, "The Pandemonium", "Captain Tobias", 14979);
		assertTrue(QuestWalkRule.permits(Stage.NAMED_ROUTES, tobias, false));
		assertTrue(QuestWalkRule.permits(Stage.NAMED_ROUTES, lorris, false));
		assertFalse("only the Karamja crossing is named", QuestWalkRule.permits(Stage.NAMED_ROUTES, pandemonium, false));
	}

	@Test
	public void crossingsPermitBoatsButNoTeleports()
	{
		assertTrue(QuestWalkRule.permits(Stage.CROSSINGS, npc(TransportType.SHIP, "Captain Tobias", A, B), ARDOUGNE));
		assertFalse("a charter costs hundreds of coins: last resort only",
			QuestWalkRule.permits(Stage.CROSSINGS, npc(TransportType.CHARTER_SHIP, "Trader Crewmember", A, B), ARDOUGNE));
		assertFalse("charter ships are off in quest walks", QuestWalkRule.permits(Stage.TELEPORTS, npc(TransportType.CHARTER_SHIP, "Trader Crewmember", A, B), ARDOUGNE));
		assertTrue(QuestWalkRule.permits(Stage.CROSSINGS, plain(TransportType.BOAT), ARDOUGNE));
		assertFalse(QuestWalkRule.permits(Stage.CROSSINGS, item("Ring of wealth: Grand Exchange"), ARDOUGNE));
		assertFalse(QuestWalkRule.permits(Stage.CROSSINGS, plain(TransportType.FAIRY_RING), ARDOUGNE));
		assertFalse(QuestWalkRule.permits(Stage.CROSSINGS, plain(TransportType.SPIRIT_TREE), ARDOUGNE));
	}

	@Test
	public void teleportsStagePermitsEverything()
	{
		assertTrue(QuestWalkRule.permits(Stage.TELEPORTS, item("Ring of wealth: Grand Exchange"), ARDOUGNE));
		assertTrue(QuestWalkRule.permits(Stage.TELEPORTS, plain(TransportType.FAIRY_RING), ARDOUGNE));
	}

	@Test
	public void longNpcFerriesAreCrossingsButLocalGuidesAreNot()
	{
		Transport primio = npc(TransportType.NPC, "Primio", new WorldPoint(3280, 3412, 0), new WorldPoint(1700, 3141, 0));
		Transport guide = npc(TransportType.NPC, "Mistag", new WorldPoint(3319, 9615, 0), new WorldPoint(3232, 9610, 0));
		assertTrue(QuestWalkRule.isRestricted(primio));
		assertFalse(QuestWalkRule.permits(Stage.ON_FOOT, primio, ARDOUGNE));
		assertTrue(QuestWalkRule.permits(Stage.CROSSINGS, primio, ARDOUGNE));
		assertFalse(QuestWalkRule.isRestricted(guide));
		assertTrue(QuestWalkRule.permits(Stage.ON_FOOT, guide, ARDOUGNE));
	}

	@Test
	public void necklaceRouteIsOnlyForArdougneTargets()
	{
		Transport necklace = item("Skills necklace: Fishing Guild");
		assertTrue(QuestWalkRule.permits(Stage.NAMED_ROUTES, necklace, true));
		assertFalse(QuestWalkRule.permits(Stage.NAMED_ROUTES, necklace, false));
		assertTrue(QuestWalkRule.permits(Stage.NAMED_ROUTES, npc(TransportType.SHIP, "Veos", A, B), false));
		assertTrue(QuestWalkRule.isArdougneArea(Set.of(WorldPointUtil.packWorldPoint(new WorldPoint(2592, 3336, 0)))));
		assertFalse(QuestWalkRule.isArdougneArea(Set.of(WorldPointUtil.packWorldPoint(new WorldPoint(1884, 3481, 0)))));
		assertFalse(QuestWalkRule.isArdougneArea(Set.of(WorldPointUtil.packWorldPoint(new WorldPoint(3222, 3218, 0)))));
	}

	@Test
	public void longRidesOfEveryNpcLikeTypeAreCrossings()
	{
		Transport primio = new Transport(new WorldPoint(1703, 3140, 0), new WorldPoint(3280, 3412, 0), "Varrock",
			TransportType.TRANSPORT, false, "Travel", "Primio", 12889);
		Transport waydar = new Transport(new WorldPoint(2896, 2727, 0), new WorldPoint(2393, 3465, 0), "x",
			TransportType.TRANSPORT, false, "Travel", "Waydar", 1446);
		Transport fremennikBoat = new Transport(new WorldPoint(2621, 3692, 0), new WorldPoint(2472, 3994, 0), "x",
			TransportType.TRANSPORT, false, "Travel", "Fremennik Boat", 37432);
		Transport ladder = new Transport(new WorldPoint(3200, 3200, 0), new WorldPoint(3200, 3200, 1), "x",
			TransportType.TRANSPORT, false, "Climb", "Ladder", 1);
		Transport underground = new Transport(new WorldPoint(3000, 3200, 0), new WorldPoint(3000, 9600, 0), "x",
			TransportType.TRANSPORT, false, "Climb", "Trapdoor", 2);
		for (Transport ride : new Transport[] {primio, waydar, fremennikBoat})
		{
			assertTrue(QuestWalkRule.isRestricted(ride));
			assertFalse(QuestWalkRule.permits(Stage.ON_FOOT, ride, false));
			assertTrue(QuestWalkRule.permits(Stage.CROSSINGS, ride, false));
		}
		assertFalse(QuestWalkRule.isRestricted(ladder));
		assertFalse(QuestWalkRule.isRestricted(underground));
	}

	@Test
	public void widensOnlyWhenTheOnFootSearchProvedTheTargetUnreachable()
	{
		assertTrue(QuestWalkRule.shouldWiden(true, PathTerminationReason.SEARCH_EXHAUSTED, false));
		assertFalse(QuestWalkRule.shouldWiden(true, PathTerminationReason.CUTOFF_REACHED, false));
		assertFalse(QuestWalkRule.shouldWiden(true, PathTerminationReason.FAILED, false));
		assertFalse(QuestWalkRule.shouldWiden(true, PathTerminationReason.CANCELLED, false));
		assertFalse(QuestWalkRule.shouldWiden(true, PathTerminationReason.TARGET_REACHED, false));
		assertFalse(QuestWalkRule.shouldWiden(true, PathTerminationReason.SEARCH_EXHAUSTED, true));
		assertFalse(QuestWalkRule.shouldWiden(false, PathTerminationReason.SEARCH_EXHAUSTED, false));
	}

	@Test
	public void restrictionBlocksOnlyWhileActive()
	{
		Transport veos = npc(TransportType.SHIP, "Veos", A, B);
		assertFalse(QuestWalkRule.blocks(veos));
		QuestWalkRule.setActive(true);
		assertTrue(QuestWalkRule.blocks(veos));
		QuestWalkRule.setActive(false);
		assertFalse(QuestWalkRule.blocks(veos));
	}

	@Test
	public void onFootRestrictionAloneNeverForcesCrossings()
	{
		// the DD Chopper case: restriction on, no crossing mode
		QuestWalkRule.setActive(true);
		assertFalse(QuestWalkRule.forcesToggle("useShips"));
		assertFalse(QuestWalkRule.forcesTeleportItems());
		assertFalse(QuestWalkRule.crossingEnabled());
	}

	@Test
	public void crossingNeedsModeAndAQuestWalkInFlight()
	{
		QuestWalkRule.setActive(true);
		QuestWalkRule.setCrossingMode(true);
		assertFalse("no quest walk running", QuestWalkRule.forcesToggle("useShips"));
		assertFalse("an unmarked thread is never a quest walk", QuestWalkRule.beginQuestWalk(A));
		QuestWalkRule.markQuestThread(true);
		boolean walking = QuestWalkRule.beginQuestWalk(A);
		try
		{
			assertTrue(walking);
			assertTrue(QuestWalkRule.forcesToggle("useShips"));
			assertTrue(QuestWalkRule.forcesToggle("useCharterShips"));
			assertFalse(QuestWalkRule.forcesToggle("useAgilityShortcuts"));
			assertTrue(QuestWalkRule.forcesTeleportItems());
			QuestWalkRule.setCrossingMode(false);
			assertFalse(QuestWalkRule.forcesToggle("useShips"));
		}
		finally
		{
			QuestWalkRule.endQuestWalk(walking);
			QuestWalkRule.markQuestThread(false);
		}
		assertFalse(QuestWalkRule.isQuestWalkInFlight());
	}

	@Test
	public void stoppingTheRuleEndsCrossingMode()
	{
		QuestWalkRule.setActive(true);
		QuestWalkRule.setCrossingMode(true);
		QuestWalkRule.markQuestThread(true);
		boolean walking = QuestWalkRule.beginQuestWalk(A);
		try
		{
			QuestWalkRule.setActive(false);
			QuestWalkRule.setActive(true);
			assertFalse("a restart must not inherit crossing mode", QuestWalkRule.crossingEnabled());
		}
		finally
		{
			QuestWalkRule.endQuestWalk(walking);
			QuestWalkRule.markQuestThread(false);
		}
	}

	@Test
	public void unreachableTargetIsReportedOnlyWhileTheStepTargetsIt()
	{
		WorldPoint elena = new WorldPoint(2592, 3336, 0);
		WorldPoint zeah = new WorldPoint(1884, 3481, 0);
		QuestWalkRule.setActive(true);
		QuestWalkRule.markQuestThread(true);
		boolean walking = QuestWalkRule.beginQuestWalk(elena);
		try
		{
			assertTrue(QuestWalkRule.reportUnreachable(elena, zeah));
			assertFalse(QuestWalkRule.reportUnreachable(elena, zeah));
			assertTrue(QuestWalkRule.isBackedOff(elena, zeah));
			assertEquals(elena, QuestWalkRule.unreachableTarget(zeah));
			// the player crossed by hand
			assertFalse(QuestWalkRule.isBackedOff(elena, new WorldPoint(3055, 3245, 0)));
			assertNull(QuestWalkRule.unreachableTarget(new WorldPoint(3055, 3245, 0)));
		}
		finally
		{
			QuestWalkRule.endQuestWalk(walking);
		}
		// the quest moved on to another step
		boolean next = QuestWalkRule.beginQuestWalk(new WorldPoint(3222, 3218, 0));
		try
		{
			assertNull(QuestWalkRule.unreachableTarget(zeah));
		}
		finally
		{
			QuestWalkRule.endQuestWalk(next);
			QuestWalkRule.markQuestThread(false);
		}
	}

	@Test
	public void arrivingClearsTheBackoff()
	{
		WorldPoint elena = new WorldPoint(2592, 3336, 0);
		WorldPoint zeah = new WorldPoint(1884, 3481, 0);
		QuestWalkRule.setActive(true);
		QuestWalkRule.markQuestThread(true);
		try
		{
			QuestWalkRule.reportUnreachable(elena, zeah);
			QuestWalkRule.walkArrived(elena);
			assertFalse(QuestWalkRule.isBackedOff(elena, zeah));
		}
		finally
		{
			QuestWalkRule.markQuestThread(false);
		}
	}

	@Test
	public void announcementTextNamesTheTransports()
	{
		Set<String> used = Collections.singleton("SHIP Port Sarim");
		assertEquals("Quest walk: T not reachable on foot; crossing via [SHIP Port Sarim]",
			QuestWalkRule.describe(Stage.CROSSINGS, "T", used));
		assertTrue(QuestWalkRule.describe(Stage.TELEPORTS, "T", used).contains("teleporting via"));
	}

	private static Set<Integer> packed(WorldPoint... points)
	{
		Set<Integer> set = new java.util.HashSet<>();
		for (WorldPoint point : points)
		{
			set.add(WorldPointUtil.packWorldPoint(point));
		}
		return set;
	}

	@Test
	public void crossingIsLimitedToThePathfindersOfQuestWalkTargets()
	{
		WorldPoint questTarget = new WorldPoint(2592, 3336, 0);
		WorldPoint someoneElsesTarget = new WorldPoint(3222, 3218, 0);
		QuestWalkRule.setActive(true);
		QuestWalkRule.setCrossingMode(true);
		QuestWalkRule.markQuestThread(true);
		boolean walking = QuestWalkRule.beginQuestWalk(questTarget);
		try
		{
			assertTrue(QuestWalkRule.crossingEnabledFor(packed(questTarget)));
			assertTrue(QuestWalkRule.crossingEnabledFor(packed(someoneElsesTarget, questTarget)));
			assertFalse("another plugin's pathfinder must not widen", QuestWalkRule.crossingEnabledFor(packed(someoneElsesTarget)));
		}
		finally
		{
			QuestWalkRule.endQuestWalk(walking);
			QuestWalkRule.markQuestThread(false);
		}
		assertFalse(QuestWalkRule.crossingEnabledFor(packed(questTarget)));
	}

	@Test
	public void nestedQuestWalksEachKeepTheirOwnTarget()
	{
		WorldPoint outer = new WorldPoint(2592, 3336, 0);
		WorldPoint inner = new WorldPoint(3222, 3218, 0);
		QuestWalkRule.setActive(true);
		QuestWalkRule.setCrossingMode(true);
		QuestWalkRule.markQuestThread(true);
		boolean first = QuestWalkRule.beginQuestWalk(outer);
		boolean second = QuestWalkRule.beginQuestWalk(inner);
		try
		{
			QuestWalkRule.endQuestWalk(second);
			assertTrue(QuestWalkRule.crossingEnabledFor(packed(outer)));
			assertFalse(QuestWalkRule.crossingEnabledFor(packed(inner)));
		}
		finally
		{
			QuestWalkRule.endQuestWalk(first);
			QuestWalkRule.markQuestThread(false);
		}
	}

	@Test
	public void theOnFootRestrictionOfAnotherPluginDoesNotTouchTheQuestState()
	{
		WorldPoint elena = new WorldPoint(2592, 3336, 0);
		WorldPoint zeah = new WorldPoint(1884, 3481, 0);
		Transport veos = npc(TransportType.SHIP, "Veos", A, B);
		QuestWalkRule.setActive(true);
		QuestWalkRule.setCrossingMode(true);
		QuestWalkRule.markQuestThread(true);
		boolean walking = QuestWalkRule.beginQuestWalk(elena);
		try
		{
			QuestWalkRule.reportUnreachable(elena, zeah);
			QuestWalkRule.setOnFootOnly(true);
			QuestWalkRule.setOnFootOnly(false);
			assertTrue(QuestWalkRule.crossingEnabled());
			assertTrue(QuestWalkRule.isBackedOff(elena, zeah));
			assertTrue(QuestWalkRule.blocks(veos));
		}
		finally
		{
			QuestWalkRule.endQuestWalk(walking);
			QuestWalkRule.markQuestThread(false);
		}
	}

	@Test
	public void theOnFootRestrictionAloneBlocksButNeverWidens()
	{
		Transport veos = npc(TransportType.SHIP, "Veos", A, B);
		QuestWalkRule.setOnFootOnly(true);
		assertTrue(QuestWalkRule.isActive());
		assertTrue(QuestWalkRule.blocks(veos));
		assertFalse(QuestWalkRule.crossingEnabledFor(packed(A)));
		QuestWalkRule.setOnFootOnly(false);
		assertFalse(QuestWalkRule.isActive());
	}

	@Test
	public void theWalkerWaitsForTheWidenedSearchesOfAQuestWalk()
	{
		// the walker gives up on a pathfinder after this long: it must outlast the usual cutoff, the longer on-foot
		// look and the three widened searches, or the quest would retry a partial walk every few seconds forever
		long usualCutoff = 3_000L;
		assertTrue(QuestWalkRule.QUEST_PATHFINDER_TIMEOUT_MS
			>= usualCutoff + QuestWalkRule.EXTENDED_CUTOFF_MS + 3 * usualCutoff);
		assertTrue("the position-based stuck watchdog (60 s) must not fire while the search runs",
			QuestWalkRule.QUEST_PATHFINDER_TIMEOUT_MS
				< net.runelite.client.plugins.microbot.questhelper.QuestStuckWatchdog.DEFAULT_STAGE_INTERVAL_MS);
		assertEquals(QuestWalkRule.PATHFINDER_TIMEOUT_MS, QuestWalkRule.pathfinderTimeoutMs(A));

		QuestWalkRule.setActive(true);
		QuestWalkRule.setCrossingMode(true);
		QuestWalkRule.markQuestThread(true);
		boolean walking = QuestWalkRule.beginQuestWalk(A);
		try
		{
			assertEquals(QuestWalkRule.QUEST_PATHFINDER_TIMEOUT_MS, QuestWalkRule.pathfinderTimeoutMs(A));
			assertEquals("another walk's target gets the usual patience",
				QuestWalkRule.PATHFINDER_TIMEOUT_MS, QuestWalkRule.pathfinderTimeoutMs(B));
		}
		finally
		{
			QuestWalkRule.endQuestWalk(walking);
			QuestWalkRule.markQuestThread(false);
		}
		assertEquals(QuestWalkRule.PATHFINDER_TIMEOUT_MS, QuestWalkRule.pathfinderTimeoutMs(A));
	}

	@Test
	public void theLastResortNeverEnablesTabletsScrollsSpellsOrMinigameTeleports()
	{
		assertFalse(QuestWalkRule.permits(Stage.TELEPORTS, item("Varrock Teleport (tablet)"), false));
		assertTrue(QuestWalkRule.permits(Stage.TELEPORTS, item("Ring of wealth: Grand Exchange"), false));
		assertFalse(QuestWalkRule.permits(Stage.TELEPORTS,
			new Transport(null, B, "Varrock Teleport", TransportType.TELEPORTATION_SPELL, true, 4), false));
		assertFalse(QuestWalkRule.permits(Stage.TELEPORTS,
			new Transport(null, B, "Clan Wars", TransportType.TELEPORTATION_MINIGAME, true, 4), false));
		QuestWalkRule.setActive(true);
		QuestWalkRule.setCrossingMode(true);
		QuestWalkRule.markQuestThread(true);
		boolean walking = QuestWalkRule.beginQuestWalk(A);
		try
		{
			assertFalse("spell and minigame teleports are not even loaded", QuestWalkRule.forcesToggle("useTeleportationSpells"));
			assertFalse(QuestWalkRule.forcesToggle("useTeleportationMinigames"));
		}
		finally
		{
			QuestWalkRule.endQuestWalk(walking);
			QuestWalkRule.markQuestThread(false);
		}
	}

	@Test
	public void wildernessAndBossJewelleryDestinationsAreExcluded()
	{
		assertFalse(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, item("Ring of dueling: Ferox Enclave"), false));
		assertFalse(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, item("Games necklace: Corporeal Beast"), false));
		assertFalse(QuestWalkRule.permits(Stage.TELEPORTS, item("Ring of dueling: Ferox Enclave"), false));
		assertTrue(QuestWalkRule.permits(Stage.OWNED_JEWELLERY, item("Ring of dueling: Castle Wars"), false));
	}

	@Test
	public void aCutOffOnFootSearchIsRepeatedLongerNeverWidened()
	{
		assertTrue(QuestWalkRule.shouldRetryOnFootLonger(true, PathTerminationReason.CUTOFF_REACHED, false));
		assertFalse(QuestWalkRule.shouldRetryOnFootLonger(true, PathTerminationReason.SEARCH_EXHAUSTED, false));
		assertFalse(QuestWalkRule.shouldRetryOnFootLonger(true, PathTerminationReason.CUTOFF_REACHED, true));
		assertFalse(QuestWalkRule.shouldRetryOnFootLonger(false, PathTerminationReason.CUTOFF_REACHED, false));
		assertTrue(QuestWalkRule.EXTENDED_CUTOFF_MS > 10_000L);
	}
}
