package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class Rs2WalkerTransportsCombatTest
{
	private static final int CAST_START_TIMEOUT_MS = 300;
	private static final int LANDING_TIMEOUT_MS = 30_000;

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
	public void refusedHomeTeleportCastDoesNotWaitForTheLanding()
	{
		try (MockedStatic<Microbot> microbot = mockClient())
		{
			long startedAt = System.currentTimeMillis();
			boolean landed = Rs2WalkerTransports.awaitHomeTeleportLanding(
				() -> false, () -> false, CAST_START_TIMEOUT_MS, LANDING_TIMEOUT_MS);
			long elapsed = System.currentTimeMillis() - startedAt;

			assertFalse(landed);
			assertTrue("waited " + elapsed + "ms", elapsed < LANDING_TIMEOUT_MS / 3);
		}
	}

	@Test
	public void startedHomeTeleportCastWaitsForTheLanding()
	{
		try (MockedStatic<Microbot> microbot = mockClient())
		{
			AtomicInteger landingPolls = new AtomicInteger();
			boolean landed = Rs2WalkerTransports.awaitHomeTeleportLanding(
				() -> true, () -> landingPolls.incrementAndGet() >= 3, CAST_START_TIMEOUT_MS, LANDING_TIMEOUT_MS);

			assertTrue(landed);
		}
	}

	@Test
	public void homeTeleportThatAlreadyLandedNeedsNoCastAnimation()
	{
		try (MockedStatic<Microbot> microbot = mockClient())
		{
			assertTrue(Rs2WalkerTransports.awaitHomeTeleportLanding(
				() -> false, () -> true, CAST_START_TIMEOUT_MS, LANDING_TIMEOUT_MS));
		}
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
