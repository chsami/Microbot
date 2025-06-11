package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

public class ApeAtollCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(2754, 2742, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID._100_ILM_STEPPING_STONE, 2755, 2742, Operation.GREATER_EQUAL, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID._100_ILM_CLIMBABLE_TREE, -1, 2742, Operation.GREATER, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID._100_ILM_MONKEYBARS_START),
			new AgilityObstacleModel(ObjectID._100_ILM_CLIFF_CLIMB_1),
			new AgilityObstacleModel(ObjectID._100_ILM_ROPE_SWING, 2752, -1, Operation.LESS_EQUAL, Operation.GREATER),
			new AgilityObstacleModel(ObjectID._100_ILM_AGILITY_TREE_BASE, 2756, -1, Operation.GREATER_EQUAL, Operation.GREATER)
		);
	}
}
