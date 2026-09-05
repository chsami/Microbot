package net.runelite.client.plugins.microbot.util.walker.puzzles;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DraynorBasementSolverTest
{
	private static final WorldPoint OIL_ROOM = new WorldPoint(3093, 9755, 0);

	@Test
	public void basementTargetRequiresEntryFromSurface()
	{
		assertTrue(DraynorBasementSolver.requiresBasementEntry(
			new WorldPoint(3092, 3361, 0), OIL_ROOM));
	}

	@Test
	public void entryStagesOnSurfaceSideSoTheBasementHookCannotInterceptIt()
	{
		assertEquals(new WorldPoint(3092, 3361, 0),
			DraynorBasementSolver.BASEMENT_ENTRY_STAGING_TILE);
		assertFalse(DraynorBasementSolver.isBasementTarget(
			DraynorBasementSolver.BASEMENT_ENTRY_STAGING_TILE));
	}

	@Test
	public void basementTargetDoesNotRestageEntryFromInside()
	{
		assertFalse(DraynorBasementSolver.requiresBasementEntry(
			new WorldPoint(3117, 9753, 0), OIL_ROOM));
	}

	@Test
	public void surfaceTargetDoesNotRequireBasementEntry()
	{
		assertFalse(DraynorBasementSolver.requiresBasementEntry(
			new WorldPoint(2957, 3341, 3), new WorldPoint(3092, 3361, 0)));
	}
}
