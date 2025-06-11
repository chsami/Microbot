package net.runelite.client.plugins.microbot.agility.courses;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

public interface AgilityCourseHandler
{
	int MAX_DISTANCE = 2300;

	WorldPoint getStartPoint();
	List<AgilityObstacleModel> getObstacles();

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
					case 14936: // MARKET_STALL
						return Rs2GameObject.canReach(obj.getWorldLocation(), _obj.sizeX(), _obj.sizeY(), 4, 4);
					case 42220: // BEAM
						return _obj.getWorldLocation().distanceTo(playerLocation) < 6;
					default:
						return Rs2GameObject.canReach(_obj.getWorldLocation(), _obj.sizeX() + 2, _obj.sizeY() + 2, 4, 4);
				}
			}
			return true;
		};

		return Rs2GameObject.getAll(validObjectPredicate).stream().findFirst().orElse(null);
	}

	default boolean waitForCompletion(final int agilityExp, final int plane)
	{
		double initialHealth = Rs2Player.getHealthPercentage();
		int timeoutMs = 15000;

		Global.sleepUntil(() -> Microbot.getClient().getSkillExperience(Skill.AGILITY) != agilityExp || Rs2Player.getHealthPercentage() < initialHealth || Microbot.getClient().getTopLevelWorldView().getPlane() != plane, timeoutMs);

		boolean gainedExp = Microbot.getClient().getSkillExperience(Skill.AGILITY) != agilityExp;
		boolean planeChanged = Microbot.getClient().getTopLevelWorldView().getPlane() != plane;
		boolean lostHealth = Rs2Player.getHealthPercentage() < initialHealth;

		return gainedExp || planeChanged || lostHealth;
	}

	default int getCurrentObstacleIndex()
	{
		WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();

		for (int i = 0; i < getObstacles().size(); i++)
		{
			AgilityObstacleModel o = getObstacles().get(i);

			boolean xMatches = o.getOperationX().check(playerLoc.getX(), o.getRequiredX());
			boolean yMatches = o.getOperationY().check(playerLoc.getY(), o.getRequiredY());

			if (xMatches && yMatches)
			{
				return i;
			}
		}

		return -1;
	}

	default boolean handleWalkToStart(WorldPoint playerWorldLocation, LocalPoint playerLocalLocation)
	{
		if (Microbot.getClient().getTopLevelWorldView().getPlane() != 0)
		{
			return false;
		}

		LocalPoint startLocal = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), getStartPoint());
		if (startLocal == null || playerLocalLocation.distanceTo(startLocal) >= MAX_DISTANCE)
		{
			if (playerWorldLocation.distanceTo(getStartPoint()) < 100)
			{
				Rs2Walker.walkTo(getStartPoint(), 8);
				Microbot.log("Going back to course's starting point");
				return true;
			}
		}
		return false;
	}
}
