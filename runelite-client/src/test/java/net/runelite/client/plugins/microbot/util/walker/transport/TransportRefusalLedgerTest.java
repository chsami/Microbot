package net.runelite.client.plugins.microbot.util.walker.transport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/** A failed staircase on the way to a ground-level target is a detour: one refusal blocks it and the walk re-plans. */
public class TransportRefusalLedgerTest
{
	private static final WorldPoint ARDOUGNE = new WorldPoint(2592, 3336, 0);

	@Test
	public void leavingTheTargetsFloorIsAShortcutToWalkRound()
	{
		assertTrue(TransportRefusalLedger.onFootRouteLikely(
			new WorldPoint(3010, 3338, 0), new WorldPoint(3011, 3337, 1), ARDOUGNE));
	}

	@Test
	public void climbingFurtherUpFromAnUpstairsFloorIsAShortcutToWalkRound()
	{
		// Falador castle, first floor to second floor, with the target on the ground
		assertTrue(TransportRefusalLedger.onFootRouteLikely(
			new WorldPoint(2960, 3340, 1), new WorldPoint(2959, 3339, 2), ARDOUGNE));
	}

	@Test
	public void comingBackDownTowardTheTargetsFloorIsNotADetour()
	{
		assertFalse(TransportRefusalLedger.onFootRouteLikely(
			new WorldPoint(2956, 3338, 1), new WorldPoint(2955, 3337, 0), ARDOUGNE));
		assertFalse(TransportRefusalLedger.onFootRouteLikely(
			new WorldPoint(2959, 3339, 2), new WorldPoint(2960, 3340, 1), ARDOUGNE));
	}

	@Test
	public void aTargetUpstairsMakesTheStaircaseTheRoute()
	{
		assertFalse(TransportRefusalLedger.onFootRouteLikely(
			new WorldPoint(3010, 3338, 0), new WorldPoint(3011, 3337, 1), new WorldPoint(3011, 3340, 1)));
	}
}
