package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/** The shipped map keeps doors passable, so doorways are found as gaps in a wall line. */
public class DoorwayCrossingsTest
{
	private static final CollisionMap MAP = new CollisionMap(SplitFlagMap.fromResources());

	@Test
	public void aBuiltUpAreaHasDoorwaysAndOpenGroundHasNone()
	{
		// Varrock's centre, full of buildings: walk every row and column and count the doorway steps
		int doorways = 0;
		for (int y = 3395; y <= 3435; y++)
		{
			List<WorldPoint> row = new ArrayList<>();
			for (int x = 3200; x <= 3250; x++)
			{
				row.add(new WorldPoint(x, y, 0));
			}
			doorways += PathJitter.doorwayCrossings(row, MAP).size();
		}
		assertTrue("expected doorways in Varrock, found " + doorways, doorways > 0);

		// the open fields south of Lumbridge's cow field
		List<WorldPoint> field = new ArrayList<>();
		for (int x = 3240; x <= 3250; x++)
		{
			field.add(new WorldPoint(x, 3270, 0));
		}
		assertEquals(0, PathJitter.doorwayCrossings(field, MAP).size());
	}

	@Test
	public void theSameDoorwayWalkedBothWaysIsOneCrossing()
	{
		for (int y = 3395; y <= 3435; y++)
		{
			for (int x = 3200; x <= 3250; x++)
			{
				List<WorldPoint> east = Arrays.asList(new WorldPoint(x, y, 0), new WorldPoint(x + 1, y, 0));
				if (!PathJitter.doorwayCrossings(east, MAP).isEmpty())
				{
					List<WorldPoint> west = Arrays.asList(new WorldPoint(x + 1, y, 0), new WorldPoint(x, y, 0));
					assertEquals(PathJitter.doorwayCrossings(east, MAP), PathJitter.doorwayCrossings(west, MAP));
					return;
				}
			}
		}
	}
}
