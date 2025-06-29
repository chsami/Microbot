package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

public class RellekkaCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(2625, 3677, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_WALLCLIMB),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_GAP_1, -1, 3672, Operation.GREATER, Operation.GREATER),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_TIGHTROPE_1),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_GAP_2),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_GAP_3, -1, 3653, Operation.GREATER, Operation.LESS_EQUAL),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_TIGHTROPE_3),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_DROPOFF)
		);
	}
}
