package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.util.walker.Rs2WalkerTransports.HomeTeleportOutcome;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

public class Rs2WalkerTransportsCombatTest
{
	private static final int CAST_START_TIMEOUT_MS = 300;
	private static final int LANDING_TIMEOUT_MS = 30_000;
	private static final int SHORT_LANDING_TIMEOUT_MS = 700;
	private static final WorldPoint VARROCK = new WorldPoint(3212, 3428, 0);
	private static final WorldPoint FALADOR = new WorldPoint(2965, 3380, 0);
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3221, 3218, 0);

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

	@Test
	public void everySpellbookHomeTeleportSharesTheHomeTeleportWait()
	{
		assertTrue(Rs2WalkerTransports.isHomeTeleport(spell("Lumbridge Home Teleport")));
		assertTrue(Rs2WalkerTransports.isHomeTeleport(spell("Edgeville Home Teleport")));
		assertTrue(Rs2WalkerTransports.isHomeTeleport(spell("Lunar Home Teleport")));
		assertTrue(Rs2WalkerTransports.isHomeTeleport(spell("Arceuus Home Teleport")));
		assertFalse(Rs2WalkerTransports.isHomeTeleport(spell("Varrock Teleport")));
		assertFalse(Rs2WalkerTransports.isHomeTeleport(null));
	}

	@Test
	public void onlyAnAnimationAfterTheClickCountsAsACastStart()
	{
		Instant beforeCast = Instant.now();
		assertFalse("an animation that ended before the click is stale",
			Rs2WalkerTransports.animationStartedAfter(beforeCast, beforeCast));
		assertFalse(Rs2WalkerTransports.animationStartedAfter(beforeCast, beforeCast.minusMillis(200)));
		assertFalse(Rs2WalkerTransports.animationStartedAfter(null, null));
		assertTrue(Rs2WalkerTransports.animationStartedAfter(beforeCast, beforeCast.plusMillis(600)));
		assertTrue(Rs2WalkerTransports.animationStartedAfter(null, beforeCast));
	}

	@Test
	public void refusedHomeTeleportCastDoesNotWaitForTheLanding()
	{
		try (MockedStatic<Microbot> microbot = mockClient())
		{
			long startedAt = System.currentTimeMillis();
			HomeTeleportOutcome outcome = Rs2WalkerTransports.awaitHomeTeleportLanding(
				() -> false, () -> false, () -> false, CAST_START_TIMEOUT_MS, LANDING_TIMEOUT_MS);
			long elapsed = System.currentTimeMillis() - startedAt;

			assertEquals(HomeTeleportOutcome.CAST_NOT_STARTED, outcome);
			assertTrue("waited " + elapsed + "ms", elapsed < LANDING_TIMEOUT_MS / 3);
		}
	}

	@Test
	public void abortedWaitIsNotReportedAsARefusedCast()
	{
		try (MockedStatic<Microbot> microbot = mockClient())
		{
			assertEquals(HomeTeleportOutcome.WAIT_ABORTED, Rs2WalkerTransports.awaitHomeTeleportLanding(
				() -> false, () -> false, () -> true, CAST_START_TIMEOUT_MS, LANDING_TIMEOUT_MS));
			assertEquals(HomeTeleportOutcome.WAIT_ABORTED, Rs2WalkerTransports.awaitHomeTeleportLanding(
				() -> true, () -> false, () -> true, CAST_START_TIMEOUT_MS, SHORT_LANDING_TIMEOUT_MS));
		}
	}

	@Test
	public void startedHomeTeleportCastWaitsForTheLanding()
	{
		try (MockedStatic<Microbot> microbot = mockClient())
		{
			AtomicInteger landingPolls = new AtomicInteger();
			HomeTeleportOutcome outcome = Rs2WalkerTransports.awaitHomeTeleportLanding(
				() -> true, () -> landingPolls.incrementAndGet() >= 3, () -> false,
				CAST_START_TIMEOUT_MS, LANDING_TIMEOUT_MS);

			assertEquals(HomeTeleportOutcome.LANDED, outcome);
		}
	}

	@Test
	public void startedHomeTeleportCastThatNeverLandsTimesOut()
	{
		try (MockedStatic<Microbot> microbot = mockClient())
		{
			assertEquals(HomeTeleportOutcome.LANDING_TIMED_OUT, Rs2WalkerTransports.awaitHomeTeleportLanding(
				() -> true, () -> false, () -> false, CAST_START_TIMEOUT_MS, SHORT_LANDING_TIMEOUT_MS));
		}
	}

	@Test
	public void homeTeleportThatAlreadyLandedNeedsNoCastAnimation()
	{
		try (MockedStatic<Microbot> microbot = mockClient())
		{
			assertEquals(HomeTeleportOutcome.LANDED, Rs2WalkerTransports.awaitHomeTeleportLanding(
				() -> false, () -> true, () -> false, CAST_START_TIMEOUT_MS, LANDING_TIMEOUT_MS));
		}
	}

	@Test
	public void onlyARefusedCastBlocksTheHomeTeleportForTheWalk()
	{
		Transport homeTeleport = spell("Lumbridge Home Teleport");
		try (MockedStatic<Rs2PathApi> pathApi = Mockito.mockStatic(Rs2PathApi.class))
		{
			pathApi.when(() -> Rs2PathApi.blockTeleportForWalk(anyString(), anyString())).thenReturn(false);

			assertTrue(Rs2WalkerTransports.settleHomeTeleport(homeTeleport, HomeTeleportOutcome.LANDED));
			assertFalse(Rs2WalkerTransports.settleHomeTeleport(homeTeleport, HomeTeleportOutcome.LANDING_TIMED_OUT));
			assertFalse(Rs2WalkerTransports.settleHomeTeleport(homeTeleport, HomeTeleportOutcome.WAIT_ABORTED));
			pathApi.verify(() -> Rs2PathApi.blockTeleportForWalk(anyString(), anyString()), never());

			assertFalse(Rs2WalkerTransports.settleHomeTeleport(homeTeleport, HomeTeleportOutcome.CAST_NOT_STARTED));
			pathApi.verify(() -> Rs2PathApi.blockTeleportForWalk(eq("Lumbridge Home Teleport"), anyString()));
			assertEquals("Lumbridge Home Teleport refused, taking another route", Microbot.status);
		}
	}

	@Test
	public void refusedHomeTeleportIsNotPlannedAgainFromAnyStartTileUntilTheNextWalk() throws Exception
	{
		Transport homeTeleport = new Transport(
			LUMBRIDGE, "Lumbridge Home Teleport",
			TransportType.TELEPORTATION_SPELL, false, 20, Collections.<Set<Integer>>emptySet());
		PathfinderConfig config = TransportRefusalStrikeOutTest.configWithTransports(Collections.emptyMap());
		Set<Transport> usable = ConcurrentHashMap.newKeySet();
		usable.add(homeTeleport);
		config.setUsableTeleports(usable);

		assertTrue(usesTeleport(TransportRefusalStrikeOutTest.plan(config, VARROCK, LUMBRIDGE)));

		assertTrue(config.blockTeleportForWalk("Lumbridge Home Teleport", "cast refused"));
		assertFalse("already blocked", config.blockTeleportForWalk("lumbridge home teleport", "cast refused"));
		assertTrue(config.isWalkBlockedTeleport(homeTeleport));
		assertFalse(usesTeleport(TransportRefusalStrikeOutTest.plan(config, VARROCK, LUMBRIDGE)));
		assertFalse("a replan from another tile must not cast it either",
			usesTeleport(TransportRefusalStrikeOutTest.plan(config, FALADOR, LUMBRIDGE)));

		config.clearWalkBlockedTeleports();
		assertFalse(config.isWalkBlockedTeleport(homeTeleport));
		assertTrue(usesTeleport(TransportRefusalStrikeOutTest.plan(config, VARROCK, LUMBRIDGE)));
	}

	private static boolean usesTeleport(List<WorldPoint> path)
	{
		assertFalse(path.isEmpty());
		assertEquals(LUMBRIDGE, path.get(path.size() - 1));
		for (int i = 0; i + 1 < path.size(); i++)
		{
			if (path.get(i).distanceTo2D(path.get(i + 1)) > 2)
			{
				return true;
			}
		}
		return false;
	}

	private static MockedStatic<Microbot> mockClient()
	{
		Client client = mock(Client.class);
		MockedStatic<Microbot> microbot = Mockito.mockStatic(Microbot.class);
		microbot.when(Microbot::getClient).thenReturn(client);
		return microbot;
	}

	private static Transport spell(String displayInfo)
	{
		return new Transport(
			null, new WorldPoint(3200, 3200, 0), displayInfo,
			TransportType.TELEPORTATION_SPELL, false, 1);
	}
}
