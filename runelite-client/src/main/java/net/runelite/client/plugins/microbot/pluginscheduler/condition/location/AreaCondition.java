package net.runelite.client.plugins.microbot.pluginscheduler.condition.location;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Condition that is met when the player is inside a rectangular area
 */
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class AreaCondition extends LocationCondition {
    public static String getVersion() {
        return "0.0.1";
    }
    @Getter
    private final WorldArea area;

    /**
     * Create a condition that is met when the player is inside the specified area
     * 
     * @param x1 Southwest corner x
     * @param y1 Southwest corner y
     * @param x2 Northeast corner x
     * @param y2 Northeast corner y
     * @param plane The plane the area is on
     */
    public AreaCondition(String name, int x1, int y1, int x2, int y2, int plane) {
        super(name);
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
    public AreaCondition(String name, WorldArea area) {
        super(name);
        if (area == null) {
            throw new IllegalArgumentException("Area cannot be null");
        }
        this.area = area;
    }

    @Override
    protected void updateLocationStatus() {
        if (!canCheckLocation()) {
            return;
        }

        try {
            WorldPoint location = getCurrentLocation();
            if (location != null) {
                satisfied = area.contains(location);
                
                if (satisfied) {
                    log.debug("Player entered target area");
                }
            }
        } catch (Exception e) {
            log.error("Error checking if player is in area", e);
        }
    }

    @Override
    public String getDescription() {
        WorldPoint location = getCurrentLocation();
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

    @Override
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Basic description
        sb.append("Area Condition: Player must be within a specific area\n");
        
        // Status information
        WorldPoint location = getCurrentLocation();
        boolean inArea = location != null && area.contains(location);
        sb.append("Status: ").append(inArea ? "Satisfied" : "Not satisfied").append("\n");
        
        // Area details
        sb.append("Area Coordinates: (").append(area.getX()).append(", ").append(area.getY()).append(") to (")
          .append(area.getX() + area.getWidth() - 1).append(", ")
          .append(area.getY() + area.getHeight() - 1).append(")\n");
        sb.append("Area Size: ").append(area.getWidth()).append(" x ").append(area.getHeight()).append(" tiles\n");
        sb.append("Plane: ").append(area.getPlane()).append("\n");
        
        // Current player position
        if (location != null) {
            sb.append("Player Position: (").append(location.getX()).append(", ")
              .append(location.getY()).append(", ").append(location.getPlane()).append(")\n");
            sb.append("Player In Area: ").append(inArea ? "Yes" : "No");
        } else {
            sb.append("Player Position: Unknown");
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Basic information
        sb.append("AreaCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Type: Area (Player must be within area)\n");
        sb.append("  │ Coordinates: (").append(area.getX()).append(", ").append(area.getY()).append(") to (")
          .append(area.getX() + area.getWidth() - 1).append(", ")
          .append(area.getY() + area.getHeight() - 1).append(")\n");
        sb.append("  │ Size: ").append(area.getWidth()).append(" x ").append(area.getHeight()).append(" tiles\n");
        sb.append("  │ Plane: ").append(area.getPlane()).append("\n");
        
        // Status information
        sb.append("  └─ Status ──────────────────────────────────\n");
        WorldPoint location = getCurrentLocation();
        boolean inArea = location != null && area.contains(location);
        sb.append("    Satisfied: ").append(inArea).append("\n");
        
        // Player location
        if (location != null) {
            sb.append("    Player Position: (").append(location.getX()).append(", ")
              .append(location.getY()).append(", ").append(location.getPlane()).append(")\n");
            sb.append("    In Target Area: ").append(inArea ? "Yes" : "No");
        } else {
            sb.append("    Player Position: Unknown");
        }
        
        return sb.toString();
    }
}