package net.runelite.client.plugins.microbot.ddchopper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WoodcuttingTableTest
{
	private static final Axe[] AXES = {Axe.BRONZE, Axe.STEEL, Axe.MITHRIL, Axe.ADAMANT};

	@Test
	public void level1ChopsNormalTrees()
	{
		for (Axe axe : AXES)
		{
			assertEquals(TreeType.NORMAL, WoodcuttingTable.bestTree(1, axe, false, null));
			assertEquals(TreeType.NORMAL, WoodcuttingTable.bestTree(14, axe, false, null));
		}
	}

	@Test
	public void level15ChopsOaks()
	{
		for (Axe axe : AXES)
		{
			assertEquals(TreeType.OAK, WoodcuttingTable.bestTree(15, axe, false, null));
			assertEquals(TreeType.OAK, WoodcuttingTable.bestTree(29, axe, false, null));
		}
	}

	@Test
	public void level30ChopsWillowsOnFreeToPlay()
	{
		for (Axe axe : AXES)
		{
			assertEquals(TreeType.WILLOW, WoodcuttingTable.bestTree(30, axe, false, null));
			assertEquals(TreeType.WILLOW, WoodcuttingTable.bestTree(59, axe, false, null));
		}
	}

	@Test
	public void membersGetTeakFrom35AndMahoganyFrom50()
	{
		for (Axe axe : AXES)
		{
			assertEquals(TreeType.WILLOW, WoodcuttingTable.bestTree(34, axe, true, null));
			assertEquals(TreeType.TEAK, WoodcuttingTable.bestTree(35, axe, true, null));
			assertEquals(TreeType.MAHOGANY, WoodcuttingTable.bestTree(50, axe, true, null));
		}
	}

	@Test
	public void yewTakesOverOnFreeToPlayAt60()
	{
		assertEquals(TreeType.YEW, WoodcuttingTable.bestTree(60, Axe.ADAMANT, false, null));
	}

	@Test
	public void unreachableTreesAreSkipped()
	{
		assertEquals(TreeType.OAK, WoodcuttingTable.bestTree(40, Axe.RUNE, false, t -> t != TreeType.WILLOW));
	}

	@Test
	public void betterAxeNeverLowersXpPerHour()
	{
		for (TreeType tree : TreeType.values())
		{
			for (int i = 1; i < Axe.values().length; i++)
			{
				assertTrue(WoodcuttingTable.xpPerHour(tree, Axe.values()[i])
					>= WoodcuttingTable.xpPerHour(tree, Axe.values()[i - 1]));
			}
		}
	}
}
