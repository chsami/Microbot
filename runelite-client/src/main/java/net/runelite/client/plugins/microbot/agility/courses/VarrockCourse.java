package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

public class VarrockCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(3221, 3414, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_WALLCLIMB),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_CLOTHESLINE),//3219,3414
			new AgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_LEAPTORUINS),//3208,3414
			new AgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_WALLSWING),//3197,3416
			new AgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_WALLSCRAMBLE,3192,-1,Operation.LESS_EQUAL,Operation.GREATER), // 3192, 3406 //
			new AgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_LEAPTOBALCONY), // 3193,3398
			new AgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_LEAPDOWN, -1, 3402, Operation.GREATER, Operation.LESS_EQUAL), // 3218,3399
			new AgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_STEPUPROOF, -1, 3408, Operation.GREATER, Operation.LESS_EQUAL), //3236,3403
			new AgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_FINISH)//3236,3410
		);
	}
}
