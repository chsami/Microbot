package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.EscortSupport.Action;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EscortSupportTest
{
	private static final WorldPoint TARGET = new WorldPoint(1757, 3069, 0);

	private static WorldPoint p(int x, int y)
	{
		return new WorldPoint(x, y, 0);
	}

	@Test
	public void matcherAcceptsEscortText()
	{
		assertTrue(EscortSupport.matchesEscortText("Lead the knight to the fountain to the east. Whenever they stop following you, talk to them again."));
		assertTrue(EscortSupport.matchesEscortText("Escort Bob to the bridge."));
		assertTrue(EscortSupport.matchesEscortText("Wait while she is following you."));
		assertTrue(EscortSupport.matchesEscortText("Take him to the church."));
	}

	@Test
	public void matcherRejectsOrdinaryText()
	{
		assertFalse(EscortSupport.matchesEscortText("Take the key to the door."));
		assertFalse(EscortSupport.matchesEscortText("Follow the path north."));
		assertFalse(EscortSupport.matchesEscortText("Use the lead on the furnace."));
		assertFalse(EscortSupport.matchesEscortText(null));
	}

	@Test
	public void arrivedWhenAtTarget()
	{
		assertEquals(Action.ARRIVED, EscortSupport.decide(p(1756, 3069), p(1755, 3069), true, false, TARGET));
	}

	@Test
	public void hopsWhenFollowerClose()
	{
		assertEquals(Action.HOP, EscortSupport.decide(p(1730, 3075), p(1729, 3075), true, false, TARGET));
	}

	@Test
	public void waitsWhenFollowerSlightlyBehind()
	{
		assertEquals(Action.WAIT_FOR_FOLLOWER, EscortSupport.decide(p(1730, 3075), p(1726, 3075), true, false, TARGET));
	}

	@Test
	public void catchesUpWhenFarBehind()
	{
		assertEquals(Action.CATCH_UP, EscortSupport.decide(p(1740, 3075), p(1730, 3075), true, false, TARGET));
	}

	@Test
	public void talksWhenWaitedOutAndNotFollowingButAdjacent()
	{
		assertEquals(Action.CATCH_UP, EscortSupport.decide(p(1730, 3075), p(1726, 3075), false, true, TARGET));
		assertEquals(Action.HOP, EscortSupport.decide(p(1730, 3075), p(1729, 3075), false, true, TARGET));
	}

	@Test
	public void findsFollowerWhenNoneVisible()
	{
		assertEquals(Action.FIND_FOLLOWER, EscortSupport.decide(p(1730, 3075), null, false, false, TARGET));
	}

	@Test
	public void hopNeverExceedsMax()
	{
		WorldPoint hop = EscortSupport.nextHop(p(1721, 3075), TARGET, 3);
		assertTrue(p(1721, 3075).distanceTo(hop) <= 3);
		assertEquals(TARGET, EscortSupport.nextHop(p(1755, 3069), TARGET, 3));
	}

	@Test
	public void progressTokenChangesWithActionsAndGap()
	{
		assertFalse(EscortSupport.progressToken(1, 4) == EscortSupport.progressToken(2, 4));
		assertFalse(EscortSupport.progressToken(1, 4) == EscortSupport.progressToken(1, 3));
	}
}
