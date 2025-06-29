package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

public class GnomeStrongholdCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(2474, 3436, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.GNOME_LOG_BALANCE1, -1, 3436, Operation.GREATER, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.OBSTICAL_NET2, 2476, 3426, Operation.LESS_EQUAL, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.CLIMBING_BRANCH),
			new AgilityObstacleModel(ObjectID.BALANCING_ROPE, 2477, -1, Operation.LESS_EQUAL, Operation.GREATER),
			new AgilityObstacleModel(ObjectID.CLIMBING_TREE),
			new AgilityObstacleModel(ObjectID.OBSTICAL_NET3, 2483, 3425, Operation.GREATER_EQUAL, Operation.LESS_EQUAL),
			new AgilityObstacleModel(ObjectID.OBSTICAL_PIPE3_1, -1, 3430, Operation.GREATER, Operation.LESS_EQUAL)
		);
	}
}
