package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;

public class AlKharidCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(3273, 3195, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_WALLCLIMB),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_TIGHTROPE_1),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_ROPE_SWING),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_SLIDE_SIDE),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_BAMBOO_TREE_TOP),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_WALLCLIMB_2),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_TIGHTROPE_4),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_LEAPDOWN)
		);
	}
}
