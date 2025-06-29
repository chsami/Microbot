package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

public class DraynorCourse implements AgilityCourseHandler
{

	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(3103, 3279, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_WALLCLIMB),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_TIGHTROPE_1),// 3102,3279
			new AgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_TIGHTROPE_2),// 3090,3276
			new AgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_WALLCROSSING,-1,3266,Operation.GREATER,Operation.GREATER_EQUAL), // 3092,3266
			new AgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_WALLSCRAMBLE, -1, 3261, Operation.GREATER, Operation.GREATER_EQUAL),// 3088,3261
			new AgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_LEAPDOWN, -1, 3255, Operation.GREATER, Operation.LESS_EQUAL),// 3088 3255
			new AgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_CRATE) // 3096,3256
		);
	}
}
