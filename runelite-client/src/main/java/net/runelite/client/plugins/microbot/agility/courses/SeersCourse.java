package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;

public class SeersCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(2729, 3486, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_WALLCLIMB),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_JUMP),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_TIGHTROPE),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_JUMP_1),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_JUMP_2),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_LEAPDOWN)
		);
	}
}
