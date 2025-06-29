package net.runelite.client.plugins.microbot.agility.courses;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.misc.Operation;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

public class PrifddinasCourse implements AgilityCourseHandler
{
	private final Set<Integer> PORTAL_OBSTACLE_IDS = ImmutableSet.of(
		ObjectID.PRIF_AGILITY_SHORTCUT_PORTAL_1,
		ObjectID.PRIF_AGILITY_SHORTCUT_PORTAL_2,
		ObjectID.PRIF_AGILITY_SHORTCUT_PORTAL_3,
		ObjectID.PRIF_AGILITY_SHORTCUT_PORTAL_4,
		ObjectID.PRIF_AGILITY_SHORTCUT_PORTAL_5,
		ObjectID.PRIF_AGILITY_SHORTCUT_PORTAL_6
	);

	private final WorldArea prifFallArea = new WorldArea(3260, 6103, 15, 9, 0);

	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(3253, 6109, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_START_LADDER, 3253, -1, Operation.LESS_EQUAL, Operation.GREATER), // before 3253,6109, after 3255,6109
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_TIGHTROPE_START1, 3258, -1, Operation.LESS_EQUAL, Operation.GREATER), // before 3255,6109 , after 3272, 6105
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_CHIMNEY_JUMP, -1, 6106, Operation.GREATER, Operation.LESS_EQUAL), // before 3273,6105 , after 3269,6112
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_ROOF_JUMP, -1, 6115, Operation.GREATER, Operation.LESS_EQUAL), // before 3269,6112 , after 3269,6117
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_DARK_HOLE_ACTIVE, 3267, 6117, Operation.GREATER, Operation.LESS_EQUAL), // before 3269,6117  , after 2269,3389
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_TREE_LADDER_LONG, -1, 3392, Operation.GREATER, Operation.LESS_EQUAL), // before 2269,3389 , after 2269,3393
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_ROPE_BRIDGE1, 2265, -1, Operation.GREATER_EQUAL, Operation.GREATER), // before 2269,3393 , after 2257,3390
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_TIGHTROPE1, 2254, -1, Operation.GREATER_EQUAL, Operation.GREATER), // before 2257,3390, after 2247,3397
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_TREE_LADDER_SHORT, -1, 6140, Operation.GREATER, Operation.GREATER), // if fail TIGHTROPE_36234 , before 3274,6147, after
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_ROPE_BRIDGE2, -1, 3398, Operation.GREATER, Operation.LESS_EQUAL), // before 2247,3397, after 2246,3406
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_TIGHTROPE2, -1, 3409, Operation.GREATER, Operation.LESS_EQUAL), // before 2246,3406, after 2250,3416
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_TIGHTROPE3, -1, 3419, Operation.GREATER, Operation.LESS_EQUAL), // before 2250,3416 , after 2260 3425 //2253,3417 portal spawn
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_DARK_HOLE_END, -1, 3431, Operation.GREATER, Operation.LESS_EQUAL) // before 2260,3425 , after 3240,6109
		);
	}

	public boolean handlePortal()
	{
		TileObject portal = Rs2GameObject.findObject(PORTAL_OBSTACLE_IDS.toArray(new Integer[0]));
		if (portal != null && Microbot.getClientThread().runOnClientThreadOptional(portal::getClickbox).isPresent())
		{
			if (Rs2GameObject.interact(portal, "travel"))
			{
				Global.sleep(2000, 3000);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean handleWalkToStart(WorldPoint playerWorldLocation, LocalPoint playerLocalLocation)
	{
		if (Microbot.getClient().getTopLevelWorldView().getPlane() != 0)
		{
			return false;
		}

		if (prifFallArea.contains(playerWorldLocation))
		{
			Rs2Walker.walkTo(getStartPoint(), 8);
			Microbot.log("Going back to course's starting point");
			return true;
		}
		return false;
	}
}
