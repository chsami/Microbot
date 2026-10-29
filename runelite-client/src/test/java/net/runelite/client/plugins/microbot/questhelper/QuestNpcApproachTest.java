package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;

import net.runelite.client.plugins.microbot.questhelper.QuestNpcApproach.Move;
import org.junit.Test;

/** Click what is in sight and beside the player or reachable; walk to what may be behind a door the server cannot open. */
public class QuestNpcApproachTest
{
	@Test
	public void reachableNpcInSightIsClickedAtAnyDistance()
	{
		assertEquals(Move.INTERACT, QuestNpcApproach.decide(true, true, 15, false));
	}

	@Test
	public void npcRightBesideThePlayerInSightIsClickedWithoutAskingThePlanner()
	{
		assertEquals(Move.INTERACT, QuestNpcApproach.decide(true, false, 1, false));
		assertEquals(Move.INTERACT, QuestNpcApproach.decide(true, false, QuestNpcApproach.POINT_BLANK, false));
	}

	@Test
	public void nearbyNpcTheServerMayNotReachIsWalkedTo()
	{
		// bars, counters and fences do not block line of sight, a closed door beyond them blocks the route
		assertEquals(Move.WALK, QuestNpcApproach.decide(true, false, 5, false));
		assertEquals(Move.WALK, QuestNpcApproach.decide(true, false, 8, false));
	}

	@Test
	public void npcOutOfSightIsWalkedTo()
	{
		assertEquals(Move.WALK, QuestNpcApproach.decide(false, true, 2, false));
	}

	@Test
	public void aClickThatJustFailedGivesWayToWalking()
	{
		assertEquals(Move.WALK, QuestNpcApproach.decide(true, true, 1, true));
	}
}
