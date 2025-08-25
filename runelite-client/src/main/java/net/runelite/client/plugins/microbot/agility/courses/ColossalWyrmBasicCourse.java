package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.misc.Operation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

public class ColossalWyrmBasicCourse implements AgilityCourseHandler
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
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_BALANCE_1_TRIGGER, 1647, -1, Operation.GREATER_EQUAL, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_MONKEYBARS_1_TRIGGER, 1635, -1, Operation.LESS_EQUAL, Operation.GREATER_EQUAL),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_LADDER_1_TRIGGER, 1628, -1, Operation.LESS_EQUAL, Operation.GREATER),
			new AgilityObstacleModel(ObjectID.VARLAMORE_WYRM_AGILITY_END_ZIPLINE_TRIGGER)
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 50;
	}

	@Override
	public boolean shouldClickObstacle(int currentXp, int lastXp) {
		// Colossal Wyrm courses have multi-XP drop obstacles
		// Don't allow early clicking based on XP - wait for animation to finish
		return !Rs2Player.isMoving() && !Rs2Player.isAnimating();
	}
	
	@Override
	public boolean waitForCompletion(final int agilityExp, final int plane)
	{
		double initialHealth = Rs2Player.getHealthPercentage();
		int timeoutMs = 15000;
		long startTime = System.currentTimeMillis();
		long lastMovingTime = System.currentTimeMillis();
		int waitDelay = 2000; // Colossal Wyrm needs longer wait after movement stops
		
		// Check every 100ms for completion
		while (System.currentTimeMillis() - startTime < timeoutMs)
		{
			// Update last moving time if player is still moving/animating
			if (Rs2Player.isMoving() || Rs2Player.isAnimating())
			{
				lastMovingTime = System.currentTimeMillis();
			}
			
			// Get current XP
			int currentXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);
			
			// Use our custom completion logic that ignores XP
			if (isObstacleComplete(currentXp, agilityExp, lastMovingTime, waitDelay))
			{
				return true;
			}
			
			// Check other completion conditions (health loss, plane change)
			if (Rs2Player.getHealthPercentage() < initialHealth || 
				Microbot.getClient().getTopLevelWorldView().getPlane() != plane)
			{
				return true;
			}
			
			// Sleep before next check
			Global.sleep(100);
		}
		
		// Timeout reached
		return false;
	}
	
	@Override
	public boolean isObstacleComplete(int currentXp, int previousXp, long lastMovingTime, int waitDelay) {
		// Colossal Wyrm courses have multi-XP drop obstacles
		// We ignore XP checks and only rely on movement/animation
		if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
			return false;
		}
		
		// Check if we've waited long enough after movement stopped
		return System.currentTimeMillis() - lastMovingTime >= waitDelay;
	}
}
