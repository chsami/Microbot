package net.runelite.client.plugins.microbot.pluginscheduler.condition.location;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Condition that is met when the player is within a certain distance of a specific position
 */
@Slf4j
public class PositionCondition implements Condition {
    private final WorldPoint targetPosition;
    private final int maxDistance;
    private boolean isAtPosition = false;
    private boolean registered = false;

    /**
     * Create a condition that is met when the player is within the specified distance of the target position
     * 
     * @param x The target x coordinate
     * @param y The target y coordinate
     * @param plane The target plane
     * @param maxDistance The maximum distance from the target position (in tiles)
     */
    public PositionCondition(int x, int y, int plane, int maxDistance) {
        this.targetPosition = new WorldPoint(x, y, plane);
        this.maxDistance = maxDistance;
    }

    /**
     * Create a condition that is met when the player is at the exact position
     * 
     * @param x The target x coordinate
     * @param y The target y coordinate
     * @param plane The target plane
     */
    public PositionCondition(int x, int y, int plane) {
        this(x, y, plane, 0);
    }

    /**
     * Create a condition that is met when the player is within the specified distance of the target position
     * 
     * @param position The target position
     * @param maxDistance The maximum distance from the target position (in tiles)
     */
    public PositionCondition(WorldPoint position, int maxDistance) {
        this.targetPosition = position;
        this.maxDistance = maxDistance;
    }

    @Override
    public boolean isMet() {
        if (!registered) {
            Microbot.getEventBus().register(this);
            registered = true;
            // Check immediately on first call
            checkPosition();
        }
        return isAtPosition;
    }

    @Override
    public void reset() {
        isAtPosition = false;
    }

    @Override
    public ConditionType getType() {
        return ConditionType.LOCATION;
    }

    @Override
    public String getDescription() {
        if (maxDistance == 0) {
            return "Player at position: " + targetPosition.getX() + ", " + targetPosition.getY() + ", " + targetPosition.getPlane();
        } else {
            return "Player within " + maxDistance + " tiles of: " + targetPosition.getX() + ", " + targetPosition.getY() + ", " + targetPosition.getPlane();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        checkPosition();
    }

    private void checkPosition() {
        Client client = Microbot.getClient();
        if (client == null || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        try {
            WorldPoint currentPosition = Rs2Player.getWorldLocation();
            if (currentPosition != null && currentPosition.getPlane() == targetPosition.getPlane()) {
                int distance = currentPosition.distanceTo(targetPosition);
                isAtPosition = distance <= maxDistance;
                
                if (isAtPosition) {
                    log.debug("Player reached target position, distance: {}", distance);
                }
            }
        } catch (Exception e) {
            log.error("Error checking player position", e);
        }
    }

    @Override
    public void unregisterEvents() {
        if (registered) {
            Microbot.getEventBus().unregister(this);
            registered = false;
        }
    }
}