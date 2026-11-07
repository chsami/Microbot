package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SealedRimRetargetGuardTest
{
	private static final WorldPoint PLAYER = new WorldPoint(3276, 3186, 0);
	private static final WorldPoint TANNER = new WorldPoint(3273, 3192, 0);

	@Test
	public void rimOnThePlayerTileWithTheGoalBeyondFinishDistanceIsRejected()
	{
		assertTrue(Rs2Walker.isStationarySealedRim(PLAYER, PLAYER, TANNER, 5));
	}

	@Test
	public void rimElsewhereIsAccepted()
	{
		assertFalse(Rs2Walker.isStationarySealedRim(new WorldPoint(3275, 3188, 0), PLAYER, TANNER, 5));
	}

	@Test
	public void rimOnThePlayerTileBesideTheGoalIsAccepted()
	{
		assertFalse(Rs2Walker.isStationarySealedRim(PLAYER, PLAYER, new WorldPoint(3276, 3187, 0), 0));
	}

	@Test
	public void rimOnThePlayerTileWithinFinishDistanceIsAccepted()
	{
		assertFalse(Rs2Walker.isStationarySealedRim(PLAYER, PLAYER, TANNER, 6));
	}

	@Test
	public void missingInputsAreAccepted()
	{
		assertFalse(Rs2Walker.isStationarySealedRim(null, PLAYER, TANNER, 5));
		assertFalse(Rs2Walker.isStationarySealedRim(PLAYER, null, TANNER, 5));
		assertFalse(Rs2Walker.isStationarySealedRim(PLAYER, PLAYER, null, 5));
	}
}
