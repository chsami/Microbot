package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

public class ArdougneCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(2673, 3298, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_WALLCLIMB),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_JUMP),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_PLANK),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_JUMP_2, -1, 3318, Operation.GREATER, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_JUMP_3, -1, 3310, Operation.GREATER, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_WALLCROSSING),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_JUMP_4)
		);
	}
}
