package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

public class PollnivneachCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(3351, 2961, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.ROOFTOPS_POLLNIVNEACH_BASKET),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_POLLNIVNEACH_MARKETSTALL, -1, 2968, Operation.GREATER, Operation.LESS_EQUAL),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_POLLNIVNEACH_HANGINGBANNER, -1, 2976, Operation.GREATER, Operation.LESS_EQUAL),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_POLLNIVNEACH_GAP, 3362, -1, Operation.LESS_EQUAL, Operation.GREATER),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_POLLNIVNEACH_TREE, 3366, -1, Operation.LESS_EQUAL, Operation.GREATER),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_POLLNIVNEACH_WALLCLIMB, -1, 2982, Operation.GREATER, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_POLLNIVNEACH_MONKEYBARS_START),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_POLLNIVNEACH_TREETOP, -1, 2996, Operation.GREATER, Operation.LESS_EQUAL),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_POLLNIVNEACH_LINE)
		);
	}
}
