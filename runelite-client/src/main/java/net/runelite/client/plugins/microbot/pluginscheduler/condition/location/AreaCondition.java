package net.runelite.client.plugins.microbot.pluginscheduler.condition.location;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;


/**
 * Condition that is met when the player is inside a rectangular area
 */
@Slf4j
public class AreaCondition implements Condition {
    @Getter
    private final WorldArea area;
    private boolean isInArea = false;    

    /**
     * Create a condition that is met when the player is inside the specified area
     * 
     * @param x1 Southwest corner x
     * @param y1 Southwest corner y
     * @param x2 Northeast corner x
     * @param y2 Northeast corner y
     * @param plane The plane the area is on
     */
    public AreaCondition(int x1, int y1, int x2, int y2, int plane) {
        int width = Math.abs(x2 - x1) + 1;
        int height = Math.abs(y2 - y1) + 1;
        int startX = Math.min(x1, x2);
        int startY = Math.min(y1, y2);
        this.area = new WorldArea(startX, startY, width, height, plane);
    }

    /**
     * Create a condition that is met when the player is inside the specified area
     * 
     * @param area The area to check
     */
    public AreaCondition(WorldArea area) {
        this.area = area;
    }

    @Override
    public boolean isSatisfied() {
        
            

            // Check immediately on first call
            checkArea();
        
        return isInArea;
    }

    @Override
    public void reset() {
        isInArea = false;
    }
    @Override
    public void reset(boolean randomize) {
        reset();
    }

    @Override
    public ConditionType getType() {
        return ConditionType.LOCATION;
    }

    @Override
    public String getDescription() {
        WorldPoint location = Rs2Player.getWorldLocation();
        String statusInfo = "";
        
        if (location != null) {
            boolean inArea = area.contains(location);
            statusInfo = String.format(" (currently %s)", inArea ? "inside area" : "outside area");
        }
        
        return String.format("Player in area: %d,%d to %d,%d (plane %d)%s",
                area.getX(), area.getY(),
                area.getX() + area.getWidth() - 1,
                area.getY() + area.getHeight() - 1,
                area.getPlane(),
                statusInfo);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        checkArea();
    }

    private void checkArea() {
        Client client = Microbot.getClient();
        if (client == null || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        try {
            WorldPoint location = Rs2Player.getWorldLocation();
            if (location != null) {
                isInArea = area.contains(location);
                
                if (isInArea) {
                    log.debug("Player entered target area");
                }
            }
        } catch (Exception e) {
            log.error("Error checking if player is in area", e);
        }
    }

    
}