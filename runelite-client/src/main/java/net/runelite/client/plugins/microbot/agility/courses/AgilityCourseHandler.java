package net.runelite.client.plugins.microbot.agility.courses;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

public interface AgilityCourseHandler
{

	WorldPoint getStartPoint();

	List<AgilityObstacleModel> getObstacles();

	Integer getRequiredLevel();

	default boolean canBeBoosted()
	{
		return true;
	}

	default TileObject getCurrentObstacle()
	{
		WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();

		List<AgilityObstacleModel> matchingObstacles = getObstacles().stream()
			.filter(o -> o.getOperationX().check(playerLocation.getX(), o.getRequiredX()) && o.getOperationY().check(playerLocation.getY(), o.getRequiredY()))
			.collect(Collectors.toList());

		List<Integer> objectIds = matchingObstacles.stream()
			.map(AgilityObstacleModel::getObjectID)
			.collect(Collectors.toList());

		Predicate<TileObject> validObjectPredicate = obj -> {
			if (!objectIds.contains(obj.getId()))
			{
				return false;
			}
			if (obj.getPlane() != playerLocation.getPlane())
			{
				return false;
			}

			if (obj instanceof GroundObject)
			{
				return Rs2GameObject.canReach(obj.getWorldLocation(), 2, 2);
			}

			if (obj instanceof GameObject)
			{
				GameObject _obj = (GameObject) obj;
				switch (obj.getId())
				{
					case ObjectID.ROOFTOPS_POLLNIVNEACH_MARKETSTALL:
						return Rs2GameObject.canReach(obj.getWorldLocation(), _obj.sizeX(), _obj.sizeY(), 4, 4);
					case ObjectID.SHAYZIEN_AGILITY_UP_SWING_JUMP_2:
						return _obj.getWorldLocation().distanceTo(playerLocation) < 6;
					default:
						return Rs2GameObject.canReach(_obj.getWorldLocation(), _obj.sizeX() + 2, _obj.sizeY() + 2, 4, 4);
				}
			}
			return true;
		};

		return Rs2GameObject.getAll(validObjectPredicate).stream().findFirst().orElse(null);
	}

	// Simple method to check if we should click or wait
	default boolean shouldClickObstacle(final int currentXp, final int lastXp)
	{
		// If animating/moving
		if (Rs2Player.isAnimating() || Rs2Player.isMoving())
		{
			// Only click if we got XP (signals completion)
			return currentXp > lastXp;
		}
		// Not animating, safe to click
		return true;
	}
	
	default boolean waitForCompletion(final int agilityExp, final int plane)
	{
		double initialHealth = Rs2Player.getHealthPercentage();
		int timeoutMs = 15000;
		long startTime = System.currentTimeMillis();
		long lastMovingTime = System.currentTimeMillis();
		int waitDelay = 1000; // Default 1 second wait after movement stops
		
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
			
			// Use the isObstacleComplete hook for course-specific completion logic
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

	default int getCurrentObstacleIndex()
	{
		WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();
		int playerPlane = Microbot.getClient().getTopLevelWorldView().getPlane();

		if (playerPlane == 0 && playerLoc.distanceTo(getStartPoint()) < 5)
		{
			return 0;
		}

		for (int i = 0; i < getObstacles().size(); i++)
		{
			AgilityObstacleModel o = getObstacles().get(i);

			if (o.getRequiredX() == -1 || o.getRequiredY() == -1)
			{
				continue;
			}

			boolean xMatches = o.getOperationX().check(playerLoc.getX(), o.getRequiredX());
			boolean yMatches = o.getOperationY().check(playerLoc.getY(), o.getRequiredY());

			if (xMatches && yMatches)
			{
				return i;
			}
		}

		int closestIndex = -1;
		int shortestDistance = Integer.MAX_VALUE;

		for (int i = 0; i < getObstacles().size(); i++)
		{
			AgilityObstacleModel o = getObstacles().get(i);

			TileObject nearestObstacle = Rs2GameObject.getAll(obj -> obj.getId() == o.getObjectID() && obj.getPlane() == playerPlane)
				.stream()
				.min(Comparator.comparing(obj -> obj.getWorldLocation().distanceTo(playerLoc)))
				.orElse(null);

			if (nearestObstacle != null)
			{
				int distance = nearestObstacle.getWorldLocation().distanceTo(playerLoc);
				if (distance < shortestDistance)
				{
					shortestDistance = distance;
					closestIndex = i;
				}
			}
		}

		return (closestIndex != -1) ? closestIndex : 0;
	}

	default boolean handleWalkToStart(WorldPoint playerWorldLocation)
	{
		if (Microbot.getClient().getTopLevelWorldView().getPlane() != 0)
		{
			return false;
		}

		if (getCurrentObstacleIndex() > 0)
		{
			return false;
		}

		if (playerWorldLocation.distanceTo(getStartPoint()) > 12)
		{
			Microbot.log("Going back to course's starting point");
			Rs2Walker.walkTo(getStartPoint(), 2);
			return true;
		}
		return false;
	}

	default int getLootDistance() {
		return 1;
	}

	default boolean isObstacleComplete(int currentXp, int previousXp, long lastMovingTime, int waitDelay) {
		// Check if we gained XP (obstacle complete)
		if (currentXp > previousXp) {
			return true;
		}
		
		// Check if still moving/animating
		if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
			return false;
		}
		
		// Check if we've waited long enough after movement stopped
		return System.currentTimeMillis() - lastMovingTime >= waitDelay;
	}
}
