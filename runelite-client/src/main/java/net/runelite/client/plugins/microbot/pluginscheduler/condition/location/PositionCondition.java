package net.runelite.client.plugins.microbot.pluginscheduler.condition.location;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
/**
 * Condition that is met when the player is within a certain distance of a specific position
 */
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class PositionCondition extends LocationCondition {
    public static String getVersion() {
        return "0.0.1";
    }
    @Getter
    private final WorldPoint targetPosition;
    @Getter
    private final int maxDistance;

    /**
     * Create a condition that is met when the player is within the specified distance of the target position
     * 
     * @param x The target x coordinate
     * @param y The target y coordinate
     * @param plane The target plane
     * @param maxDistance The maximum distance from the target position (in tiles)
     */
    public PositionCondition(String name, int x, int y, int plane, int maxDistance) {
        super(name);
        if (maxDistance < 0) {
            throw new IllegalArgumentException("Max distance cannot be negative");
        }
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Coordinates and plane must be non-negative");
        }        
        if (maxDistance > 0 && maxDistance > 104) {
            throw new IllegalArgumentException("Max distance must be within the valid range (0-104)");
        }
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
    public PositionCondition(String name, int x, int y, int plane) {
        this(name, x, y, plane, 0);
    }

    /**
     * Create a condition that is met when the player is within the specified distance of the target position
     * 
     * @param position The target position
     * @param maxDistance The maximum distance from the target position (in tiles)
     */
    public PositionCondition(String name, WorldPoint position, int maxDistance) {
        super(name);
        if (maxDistance < 0) {
            throw new IllegalArgumentException("Max distance cannot be negative");
        }
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        
        this.targetPosition = position;
        this.maxDistance = maxDistance;
    }

    @Override
    protected void updateLocationStatus() {
        if (Microbot.isDebug()){
            log.info("Checking player position against target position: {}", targetPosition);
        }
        
        if (!canCheckLocation()) {
            return;
        }        
        try {
            WorldPoint currentPosition = getCurrentLocation();  
            if (Microbot.isDebug()){
                log.info("Current position: {}", currentPosition);
                log.info("Target position: {}", targetPosition);
                log.info("Max distance: {}", maxDistance);
                log.info("Current plane: {}", currentPosition != null ? currentPosition.getPlane() : "null");
            }
            if (currentPosition != null && currentPosition.getPlane() == targetPosition.getPlane()) {
                int distance = currentPosition.distanceTo(targetPosition);
                if (Microbot.isDebug()){
                    log.info("Distance to target position: {}", distance);
                    log.info("Max distance: {}", maxDistance);
                }
                this.satisfied = distance <= maxDistance;
                
                if (this.satisfied) {
                    log.debug("Player reached target position, distance: {}", distance);
                }
            }
        } catch (Exception e) {
            log.error("Error checking player position", e);
        }
    }

    @Override
    public String getDescription() {
        WorldPoint currentLocation = getCurrentLocation();
        String distanceInfo = "";
        String playerPositionInfo = "";
        
        if (currentLocation != null) {
            int distance = -1;
            boolean onSamePlane = currentLocation.getPlane() == targetPosition.getPlane();
            
            if (onSamePlane) {
                distance = currentLocation.distanceTo(targetPosition);
                distanceInfo = String.format(" (current distance: %d tiles)", distance);
            } else {
                distanceInfo = " (not on same plane)";
            }
            
            playerPositionInfo = String.format(" | Player at: %d, %d, %d", 
                currentLocation.getX(), currentLocation.getY(), currentLocation.getPlane());
        } else {
            playerPositionInfo = " | Player position unknown";
        }
        
        if (maxDistance == 0) {
            return String.format("Player at position: %d, %d, %d%s%s", 
                    targetPosition.getX(), targetPosition.getY(), targetPosition.getPlane(), distanceInfo, playerPositionInfo);
        } else {
            return String.format("Player within %d tiles of: %d, %d, %d%s%s", 
                    maxDistance, targetPosition.getX(), targetPosition.getY(), targetPosition.getPlane(), distanceInfo, playerPositionInfo);
        }
    }

    @Override
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Basic description
        if (maxDistance == 0) {
            sb.append("Position Condition: Player must be at exact position\n");
        } else {
            sb.append("Position Condition: Player must be within ").append(maxDistance)
              .append(" tiles of target position\n");
        }
        
        // Status information
        WorldPoint currentPosition = getCurrentLocation();
        int distance = -1;
        boolean onSamePlane = false;
        
        if (currentPosition != null) {
            onSamePlane = currentPosition.getPlane() == targetPosition.getPlane();
            if (onSamePlane) {
                distance = currentPosition.distanceTo(targetPosition);
            }
        }
        
        boolean isSatisfied = onSamePlane && distance <= maxDistance && distance != -1;
        sb.append("Status: ").append(isSatisfied ? "Satisfied" : "Not satisfied").append("\n");
        
        // Target details
        sb.append("Target Position: (").append(targetPosition.getX()).append(", ")
          .append(targetPosition.getY()).append(", ").append(targetPosition.getPlane()).append(")\n");
        
        if (maxDistance > 0) {
            sb.append("Max Distance: ").append(maxDistance).append(" tiles\n");
        }
        
        // Current player position and distance
        if (currentPosition != null) {
            sb.append("Player Position: (").append(currentPosition.getX()).append(", ")
              .append(currentPosition.getY()).append(", ").append(currentPosition.getPlane()).append(")\n");
              
            if (onSamePlane) {
                sb.append("Current Distance: ").append(distance).append(" tiles");
                if (distance <= maxDistance) {
                    sb.append(" (within range)");
                } else {
                    sb.append(" (outside range)");
                }
            } else {
                sb.append("Current Distance: Not on same plane");
            }
        } else {
            sb.append("Player Position: Unknown");
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Basic information
        sb.append("PositionCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        if (maxDistance == 0) {
            sb.append("  │ Type: Position (Player must be at exact position)\n");
        } else {
            sb.append("  │ Type: Position (Player must be within ").append(maxDistance)
              .append(" tiles of target)\n");
        }
        sb.append("  │ Target: (").append(targetPosition.getX()).append(", ")
          .append(targetPosition.getY()).append(", ").append(targetPosition.getPlane()).append(")\n");
        
        if (maxDistance > 0) {
            sb.append("  │ Max Distance: ").append(maxDistance).append(" tiles\n");
        }
        
        // Status information
        sb.append("  └─ Status ──────────────────────────────────\n");
        WorldPoint currentPosition = getCurrentLocation();
        int distance = -1;
        boolean onSamePlane = false;
        
        if (currentPosition != null) {
            onSamePlane = currentPosition.getPlane() == targetPosition.getPlane();
            if (onSamePlane) {
                distance = currentPosition.distanceTo(targetPosition);
            }
            
            sb.append("    Player Position: (").append(currentPosition.getX()).append(", ")
              .append(currentPosition.getY()).append(", ").append(currentPosition.getPlane()).append(")\n");
              
            if (onSamePlane) {
                sb.append("    Current Distance: ").append(distance).append(" tiles\n");
            } else {
                sb.append("    Current Distance: Not on same plane\n");
            }
            
            boolean isSatisfied = onSamePlane && distance <= maxDistance;
            sb.append("    Satisfied: ").append(isSatisfied);
        } else {
            sb.append("    Player Position: Unknown\n");
            sb.append("    Satisfied: false");
        }
        
        return sb.toString();
    }
}