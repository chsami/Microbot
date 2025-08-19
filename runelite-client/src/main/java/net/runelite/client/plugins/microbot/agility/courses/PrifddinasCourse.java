package net.runelite.client.plugins.microbot.agility.courses;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.misc.Operation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
public class PrifddinasCourse implements AgilityCourseHandler
{
	private final Set<Integer> PORTAL_OBSTACLE_IDS = ImmutableSet.of(
		ObjectID.PRIF_AGILITY_SHORTCUT_PORTAL_1,
		ObjectID.PRIF_AGILITY_SHORTCUT_PORTAL_2,
		ObjectID.PRIF_AGILITY_SHORTCUT_PORTAL_3,
		ObjectID.PRIF_AGILITY_SHORTCUT_PORTAL_4,
		ObjectID.PRIF_AGILITY_SHORTCUT_PORTAL_5,
		ObjectID.PRIF_AGILITY_SHORTCUT_PORTAL_6
	);

	private final WorldPoint fallRecoveryPoint = new WorldPoint(3254, 6108, 0);
	private double lastKnownHealth = -1;

	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(3253, 6109, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_START_LADDER, 3254, -1, Operation.LESS_EQUAL, Operation.GREATER), // before 3254,6108, after 3255,6109
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_TIGHTROPE_START1, 3258, -1, Operation.LESS_EQUAL, Operation.GREATER), // before 3255,6109 , after 3272, 6105
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_CHIMNEY_JUMP, -1, 6106, Operation.GREATER, Operation.LESS_EQUAL), // before 3273,6105 , after 3269,6112
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_ROOF_JUMP, -1, 6115, Operation.GREATER, Operation.LESS_EQUAL), // before 3269,6112 , after 3269,6117
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_DARK_HOLE_ACTIVE, 3269	, 6117, Operation.EQUAL, Operation.LESS_EQUAL), // before 3269,6117  , after 2269,3389
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_TREE_LADDER_LONG, -1, 3392, Operation.GREATER, Operation.LESS_EQUAL), // before 2269,3389 , after 2269,3393
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_ROPE_BRIDGE1, 2265, -1, Operation.GREATER_EQUAL, Operation.GREATER), // before 2269,3393 , after 2257,3390
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_TIGHTROPE1, 2254, -1, Operation.GREATER_EQUAL, Operation.GREATER), // before 2257,3390, after 2247,3397
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_TREE_LADDER_SHORT, -1, 6140, Operation.GREATER, Operation.GREATER), // if fail TIGHTROPE_36234 , before 3274,6147, after
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_ROPE_BRIDGE2, -1, 3398, Operation.GREATER, Operation.LESS_EQUAL), // before 2247,3397, after 2246,3406
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_TIGHTROPE2, -1, 3409, Operation.GREATER, Operation.LESS_EQUAL), // before 2246,3406, after 2250,3416
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_TIGHTROPE3, -1, 3419, Operation.GREATER, Operation.LESS_EQUAL), // before 2250,3416 , after 2260 3425 //2253,3417 portal spawn
			new AgilityObstacleModel(ObjectID.PRIF_AGILITY_DARK_HOLE_END, -1, 3431, Operation.GREATER, Operation.LESS_EQUAL) // before 2260,3425 , after 3240,6109
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 75;
	}

	public boolean handlePortal()
	{
		TileObject portal = Rs2GameObject.getGameObject(PORTAL_OBSTACLE_IDS.toArray(new Integer[0]), 10);
		if (portal != null && Microbot.getClientThread().runOnClientThreadOptional(portal::getClickbox).isPresent())
		{
			if (Rs2GameObject.interact(portal, "travel"))
			{
				Global.sleep(2000, 3000);
				return true;
			}
		}
		return false;
	}

	public boolean handleHealthMonitoring()
	{
		double currentHealth = Rs2Player.getHealthPercentage();

		// initialize health tracking on first call
		if (lastKnownHealth == -1)
		{
			lastKnownHealth = currentHealth;
			log.info("Initialized health tracking at {}%", currentHealth);
			return false;
		}

		// check if health dropped (indicating a fall)
		if (currentHealth < lastKnownHealth)
		{
			log.info("Health dropped from {}% to {}% - fall detected", lastKnownHealth, currentHealth);
			WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
			log.info("Player location when fall detected: {}", playerLocation);

			// walk to exact fall recovery point
			log.info("Walking to exact fall recovery point: {}", fallRecoveryPoint);
			Rs2Walker.walkTo(fallRecoveryPoint, 0);
			Microbot.log("Fell from agility course, walking back to start");

			// update health tracking
			lastKnownHealth = currentHealth;
			return true;
		}

		// update health tracking (health stayed same or increased due to healing)
		lastKnownHealth = currentHealth;
		return false;
	}

	@Override
	public TileObject getCurrentObstacle()
	{
		WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();

		// if we're near the start point, just find the first obstacle by ID within 5 tiles
		if (playerLocation.distanceTo(getStartPoint()) < 5)
		{
			log.info("Near start point, searching for first obstacle within 5 tiles");
			TileObject startLadder = Rs2GameObject.getGameObject(ObjectID.PRIF_AGILITY_START_LADDER, playerLocation, 5);
			if (startLadder != null)
			{
				log.info("Found start ladder at: {}", startLadder.getWorldLocation());
				return startLadder;
			}
		}

		// fallback to default behavior for other obstacles
		return AgilityCourseHandler.super.getCurrentObstacle();
	}

	@Override
	public boolean handleWalkToStart(WorldPoint playerWorldLocation)
	{
		log.info("=== PrifddinasCourse.handleWalkToStart() called ===");
		log.info("Player location: {}", playerWorldLocation);
		log.info("Start point: {}", getStartPoint());
		log.info("Distance to start: {}", playerWorldLocation.distanceTo(getStartPoint()));
		log.info("Player plane: {}", Microbot.getClient().getTopLevelWorldView().getPlane());
		log.info("getCurrentObstacleIndex(): {}", getCurrentObstacleIndex());

		// check for health-based fall detection
		if (handleHealthMonitoring())
		{
			return true;
		}

		log.info("PrifddinasCourse.handleWalkToStart() returning false");
		return false;
	}
}
