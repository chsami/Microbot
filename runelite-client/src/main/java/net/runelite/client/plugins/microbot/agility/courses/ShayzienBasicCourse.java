package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

public class ShayzienBasicCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(1551, 3632, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_BOTH_START_LADDER),
			new AgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_BOTH_ROPE_CLIMB),
			new AgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_BOTH_ROPE_WALK, -1, 3635, Operation.GREATER, Operation.LESS_EQUAL),
			new AgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_LOW_BAR_CLIMB),
			new AgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_LOW_ROPE_WALK_1),
			new AgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_LOW_ROPE_WALK_2, -1, 3643, Operation.GREATER, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_LOW_END_JUMP)
		);
	}
}
