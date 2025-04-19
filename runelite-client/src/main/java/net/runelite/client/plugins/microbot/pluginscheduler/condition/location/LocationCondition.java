package net.runelite.client.plugins.microbot.pluginscheduler.condition.location;

import com.formdev.flatlaf.json.Location;

import lombok.EqualsAndHashCode;
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
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Base class for all location-based conditions.
 * Provides common functionality for conditions that depend on player location.
 */
@Slf4j
@EqualsAndHashCode(callSuper = false)
public abstract class LocationCondition implements Condition {
    transient boolean satisfied = false;
    /**
     * Indicates whether the condition is satisfied based on the player's location.
     */
    @Getter
    protected final String name;
    public LocationCondition(String name) {
        this.name = name;
    }
    @Override
    public boolean isSatisfied() {       
        return satisfied; //update in the child class, via updateLocationStatus
    }
    
    @Override
    public void reset() {
        
    }
    
    @Override
    public void reset(boolean randomize) {
        
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.LOCATION;
    }
    
    @Override
    public void onGameTick(GameTick event) {
        updateLocationStatus();
    }
    
    /**
     * Updates the satisfaction status based on the player's current location.
     * Each subclass will implement its own location check logic.
     */
    protected abstract void updateLocationStatus();
    
    /**
     * Gets the player's current location if available.
     * 
     * @return The player's current WorldPoint or null if unavailable
     */
    protected WorldPoint getCurrentLocation() {
        if (!canCheckLocation()) {
            return null;
        }
        return Rs2Player.getWorldLocation();
    }
    
    /**
     * Checks if the game client is in a state where location checks can be performed.
     * 
     * @return True if location checks can be performed, false otherwise
     */
    protected boolean canCheckLocation() {
        Client client = Microbot.getClient();
        return client != null && client.getGameState() == GameState.LOGGED_IN;
    }
    
    /**
     * Returns a detailed description of the location condition with additional status information.
     */
    public abstract String getDetailedDescription();
    
    /**
     * Creates a condition that is satisfied when the player is at any of the locations for the given bank
     * 
     * @param bank The bank location
     * @param distance The maximum distance from the bank point
     * @return A condition that is satisfied when the player is within distance of the bank
     */
    public static Condition atBank(BankLocation bank, int distance) {
       
        WorldPoint baPoint = bank.getWorldPoint();        
        log .info("Bank location: " + bank.name() + " - " + baPoint);
        return new PositionCondition("At " + bank.name(), baPoint, distance);
       
        
        
    }
    
    
    // Probelem for now is the enums are protect in worldmap plugin.... 
    // import net.runelite.client.plugins.worldmap.RareTreeLocation;
    // /**
    //  * Creates a condition that is satisfied when the player is at any of the locations for the given farming patch
    //  * 
    //  * @param patch The farming patch location
    //  * @param distance The maximum distance from any patch point
    //  * @return A condition that is satisfied when the player is at the farming patch
    //  */
    // public static Condition atFarmingPatch(FarmingPatchLocation patch, int distance) {
    //     WorldPoint[] locations = patch.getLocations();
    //     if (locations.length == 1) {
    //         return new PositionCondition("At " + patch.name(), locations[0], distance);
    //     } else {
    //         OrCondition orCondition = new OrCondition();
    //         for (WorldPoint point : locations) {
    //             orCondition.addCondition(
    //                 new PositionCondition("At " + patch.name() + " location", point, distance)
    //             );
    //         }
    //         return orCondition;
    //     }
    // }

    // /**
    //  * Creates a condition that is satisfied when the player is at any of the locations for the given tree type
    //  * 
    //  * @param treeLocation The rare tree location
    //  * @param distance The maximum distance from any tree location
    //  * @return A condition that is satisfied when the player is at the tree
    //  */
    // public static Condition atRareTree(RareTreeLocation treeLocation, int distance) {
    //     WorldPoint[] locations = treeLocation.getLocations();
    //     if (locations.length == 1) {
    //         return new PositionCondition("At " + treeLocation.name(), locations[0], distance);
    //     } else {
    //         OrCondition orCondition = new OrCondition();
    //         for (WorldPoint point : locations) {
    //             orCondition.addCondition(
    //                 new PositionCondition("At " + treeLocation.name() + " location", point, distance)
    //             );
    //         }
    //         return orCondition;
    //     }
    // }

    /**
     * Creates a condition that is satisfied when the player is at any of the given points
     * 
     * @param name Descriptive name for the condition
     * @param points Array of points to check
     * @param distance The maximum distance from any point
     * @return A condition that is satisfied when the player is at any of the points
     */
    public static Condition atAnyPoint(String name, WorldPoint[] points, int distance) {
        if (points.length == 1) {
            return new PositionCondition(name, points[0], distance);
        } else {
            OrCondition orCondition = new OrCondition();
            for (int i = 0; i < points.length; i++) {
                orCondition.addCondition(
                    new PositionCondition(name + " (point " + (i+1) + ")", points[i], distance)
                );
            }
            return orCondition;
        }
    }

    /**
     * Creates a rectangle area condition centered on the given point
     * 
     * @param name Descriptive name for the condition
     * @param center The center point of the rectangle
     * @param width Width of the area (in tiles)
     * @param height Height of the area (in tiles)
     * @return A condition that is satisfied when the player is within the area
     */
    public static AreaCondition createArea(String name, WorldPoint center, int width, int height) {
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        int x1 = center.getX() - halfWidth;
        int y1 = center.getY() - halfHeight;
        int x2 = center.getX() + halfWidth;
        int y2 = center.getY() + halfHeight;
        return new AreaCondition(name, x1, y1, x2, y2, center.getPlane());
    }

    /**
     * Creates a condition that is satisfied when the player is within any of the given areas
     * 
     * @param name Descriptive name for the condition
     * @param areas Array of WorldAreas to check
     * @return A condition that is satisfied when the player is in any of the areas
     */
    public static Condition inAnyArea(String name, WorldArea[] areas) {
        if (areas.length == 0) {
            throw new IllegalArgumentException("At least one area must be provided");
        }
        
        if (areas.length == 1) {
            return new AreaCondition(name, areas[0]);
        } else {
            OrCondition orCondition = new OrCondition();
            for (int i = 0; i < areas.length; i++) {
                orCondition.addCondition(
                    new AreaCondition(name + " (area " + (i+1) + ")", areas[i])
                );
            }
            return orCondition;
        }
    }

    /**
     * Creates a condition that is satisfied when the player is within any of the given rectangular areas
     * 
     * @param name Descriptive name for the condition
     * @param areaDefinitions Array of area definitions, each containing [x1, y1, x2, y2, plane]
     * @return A condition that is satisfied when the player is in any of the areas
     */
    public static Condition inAnyArea(String name, int[][] areaDefinitions) {
        if (areaDefinitions.length == 0) {
            throw new IllegalArgumentException("At least one area must be provided");
        }
        
        if (areaDefinitions.length == 1) {
            int[] def = areaDefinitions[0];
            if (def.length != 5) {
                throw new IllegalArgumentException("Each area definition must contain [x1, y1, x2, y2, plane]");
            }
            return new AreaCondition(name, def[0], def[1], def[2], def[3], def[4]);
        } else {
            OrCondition orCondition = new OrCondition();
            for (int i = 0; i < areaDefinitions.length; i++) {
                int[] def = areaDefinitions[i];
                if (def.length != 5) {
                    throw new IllegalArgumentException("Each area definition must contain [x1, y1, x2, y2, plane]");
                }
                orCondition.addCondition(
                    new AreaCondition(name + " (area " + (i+1) + ")", def[0], def[1], def[2], def[3], def[4])
                );
            }
            return orCondition;
        }
    }
}