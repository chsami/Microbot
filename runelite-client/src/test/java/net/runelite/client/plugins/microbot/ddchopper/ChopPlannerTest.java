package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ChopPlannerTest
{
	private static ChopPlanner.Input input(int level)
	{
		ChopPlanner.Input in = new ChopPlanner.Input();
		in.level = level;
		in.axe = Axe.MITHRIL;
		return in;
	}

	@Test
	public void autoProgressesNormalOakWillow()
	{
		assertEquals(TreeType.NORMAL, ChopPlanner.plan(input(1)).tree);
		assertEquals(TreeType.OAK, ChopPlanner.plan(input(15)).tree);
		assertEquals(TreeType.WILLOW, ChopPlanner.plan(input(30)).tree);
		assertEquals(Spot.DRAYNOR_WILLOWS, ChopPlanner.plan(input(30)).spot);
	}

	@Test
	public void freeToPlayNeverGetsMembersSpots()
	{
		for (TreeType tree : TreeType.values())
		{
			for (Spot s : Spot.forTree(tree, 99, false, true))
			{
				assertFalse(s.getLabel(), s.isMembers());
			}
		}
		assertEquals(TreeType.YEW, ChopPlanner.plan(input(75)).tree);
		assertFalse(ChopPlanner.plan(input(75)).spot.isMembers());
	}

	@Test
	public void boatAndGatedSpotsAreOffByDefault()
	{
		for (Spot s : Spot.forTree(TreeType.YEW, 99, true, false))
		{
			assertFalse(s.getLabel(), s.isRestricted());
		}
		assertTrue(Spot.forTree(TreeType.REDWOOD, 99, true, false).isEmpty());
		assertTrue(Spot.forTree(TreeType.TEAK, 99, true, false).isEmpty());
		assertFalse(Spot.forTree(TreeType.REDWOOD, 99, true, true).isEmpty());
	}

	@Test
	public void autoTreeSkipsTreesWithoutAReachableSpot()
	{
		ChopPlanner.Input in = input(40);
		in.members = true;
		// Teak is members, but every teak spot needs a boat, so Auto stays on willow.
		assertEquals(TreeType.WILLOW, ChopPlanner.plan(in).tree);
		in.allowRestricted = true;
		assertEquals(TreeType.TEAK, ChopPlanner.plan(in).tree);
	}

	@Test
	public void guildSpotNeedsLevel60()
	{
		assertFalse(Spot.GUILD_YEWS.eligible(59, true, true));
		assertTrue(Spot.GUILD_YEWS.eligible(60, true, true));
		assertFalse(Spot.GUILD_YEWS.eligible(60, true, false));
	}

	@Test
	public void specificSpotIsUsedWhenItSuitsTheTree()
	{
		ChopPlanner.Input in = input(60);
		in.spotChoice = Spot.VARROCK_PALACE_YEWS;
		ChopPlanner.Plan plan = ChopPlanner.plan(in);
		assertEquals(Spot.VARROCK_PALACE_YEWS, plan.spot);
		assertEquals(TreeType.YEW, plan.tree);
		assertTrue(plan.notes.isEmpty());
	}

	@Test
	public void specificSpotThatIsNotUsableFallsBackToAuto()
	{
		ChopPlanner.Input in = input(60);
		in.spotChoice = Spot.SEERS_YEWS;
		ChopPlanner.Plan plan = ChopPlanner.plan(in);
		assertFalse(plan.notes.isEmpty());
		assertNotNull(plan.spot);
		assertFalse(plan.spot.isMembers());
	}

	@Test
	public void specificSpotWithWrongForcedTreeFallsBackToAuto()
	{
		ChopPlanner.Input in = input(60);
		in.treeChoice = TreeChoice.WILLOW;
		in.spotChoice = Spot.EDGEVILLE_YEWS;
		ChopPlanner.Plan plan = ChopPlanner.plan(in);
		assertEquals(TreeType.WILLOW, plan.tree);
		assertEquals(TreeType.WILLOW, plan.spot.getTree());
		assertFalse(plan.notes.isEmpty());
	}

	@Test
	public void spotsListedForATreeAreFilteredToThatTree()
	{
		List<Spot> oaks = Spot.forTree(TreeType.OAK, 30, false, false);
		assertFalse(oaks.isEmpty());
		for (Spot s : oaks)
		{
			assertEquals(TreeType.OAK, s.getTree());
			assertFalse(s.isMembers());
		}
		assertTrue(oaks.contains(Spot.DRAYNOR_OAKS));
		assertTrue(oaks.contains(Spot.VARROCK_WEST_OAKS));
		assertFalse(oaks.contains(Spot.SEERS_OAKS));
	}

	@Test
	public void forcedTreeBelowLevelFallsBackToAuto()
	{
		ChopPlanner.Input in = input(20);
		in.treeChoice = TreeChoice.YEW;
		ChopPlanner.Plan plan = ChopPlanner.plan(in);
		assertEquals(TreeType.OAK, plan.tree);
		assertFalse(plan.notes.isEmpty());
	}

	@Test
	public void optimalSpotDependsOnMode()
	{
		ChopPlanner.Input in = input(60);
		in.mode = ChopMode.BANK;
		assertEquals(Spot.EDGEVILLE_YEWS, ChopPlanner.plan(in).spot);
		in.mode = ChopMode.POWER_CHOP;
		Spot power = ChopPlanner.plan(in).spot;
		for (Spot s : Spot.forTree(TreeType.YEW, 60, false, false))
		{
			assertTrue(power.getTrees() >= s.getTrees());
		}
	}

	@Test
	public void autoSpotTieBreaksOnDistance()
	{
		ChopPlanner.Input in = input(1);
		in.player = new WorldPoint(3105, 3245, 0);
		ChopPlanner.Plan plan = ChopPlanner.plan(in);
		assertEquals(TreeType.NORMAL, plan.tree);
		assertNotNull(plan.spot);
	}
}
