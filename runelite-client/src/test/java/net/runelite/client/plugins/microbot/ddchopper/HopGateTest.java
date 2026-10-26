package net.runelite.client.plugins.microbot.ddchopper;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HopGateTest
{
	@Test
	public void notCrowdedStays()
	{
		HopGate gate = new HopGate(2, 6);
		assertEquals(HopGate.Decision.STAY, gate.decide(0, 0));
		assertEquals(HopGate.Decision.STAY, gate.decide(2, 1_000));
	}

	@Test
	public void crowdedWaitsForGraceThenHops()
	{
		HopGate gate = new HopGate(2, 6);
		assertEquals(HopGate.Decision.WAIT, gate.decide(3, 0));
		assertEquals(HopGate.Decision.WAIT, gate.decide(5, 9_999));
		assertEquals(HopGate.Decision.HOP, gate.decide(3, 10_000));
	}

	@Test
	public void crowdClearingResetsTheTimer()
	{
		HopGate gate = new HopGate(2, 6);
		gate.decide(3, 0);
		gate.decide(1, 5_000);
		assertEquals(HopGate.Decision.WAIT, gate.decide(3, 12_000));
		assertEquals(HopGate.Decision.HOP, gate.decide(3, 22_000));
	}

	@Test
	public void afterHopNewWorldGetsFreshGraceAndHopsAgainIfStillCrowded()
	{
		HopGate gate = new HopGate(2, 6);
		gate.decide(4, 0);
		assertEquals(HopGate.Decision.HOP, gate.decide(4, 10_000));
		gate.recordHop(10_000);
		assertEquals(HopGate.Decision.WAIT, gate.decide(4, 12_000));
		assertEquals(HopGate.Decision.WAIT, gate.decide(4, 21_999));
		assertEquals(HopGate.Decision.HOP, gate.decide(4, 22_000));
	}

	@Test
	public void minimumGapBetweenHopsIsHonoured()
	{
		HopGate gate = new HopGate(2, 0L, 10_000L, 6);
		assertEquals(HopGate.Decision.HOP, gate.decide(4, 0));
		gate.recordHop(0);
		assertEquals(HopGate.Decision.WAIT, gate.decide(4, 9_999));
		assertEquals(HopGate.Decision.HOP, gate.decide(4, 10_000));
	}

	@Test
	public void hourlyCapStopsHopping()
	{
		HopGate gate = new HopGate(0, 0L, 0L, 2);
		long t = 0;
		for (int i = 0; i < 2; i++)
		{
			assertEquals(HopGate.Decision.HOP, gate.decide(1, t));
			gate.recordHop(t);
			t += 60_000;
		}
		assertEquals(HopGate.Decision.CAPPED, gate.decide(1, t));
		// Once the first hop is over an hour old, one more is allowed.
		assertEquals(HopGate.Decision.HOP, gate.decide(1, 3_600_000L));
	}

	@Test
	public void avoidListParsing()
	{
		Set<Integer> worlds = HopGate.parseWorlds("301, 302;303  x 304,,");
		assertEquals(4, worlds.size());
		assertTrue(worlds.contains(301) && worlds.contains(304));
		assertTrue(HopGate.parseWorlds(null).isEmpty());
		assertTrue(HopGate.parseWorlds("").isEmpty());
	}
}
