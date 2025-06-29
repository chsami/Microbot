package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

public class ColossalWyrmAdvancedCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(1652, 2931, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_START_LADDER_TRIGGER),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_BALANCE_1_TRIGGER, -1, 2926, Operation.GREATER, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_LADDER_1_TRIGGER, -1, 2911, Operation.GREATER, Operation.LESS_EQUAL),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_JUMP_1_TRIGGER, 1647, -1, Operation.GREATER_EQUAL, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_BALANCE_1_TRIGGER),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_END_ZIPLINE_TRIGGER)
		);
	}
}
