package net.runelite.client.plugins.microbot.pluginscheduler.condition.location;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Condition that is met when the player enters a specific region
 */
@Slf4j
public class RegionCondition extends LocationCondition {
    public static String getVersion() {
        return "0.0.1";
    }
    @Getter
    private final Set<Integer> targetRegions;

    /**
     * Create a condition that is met when the player enters any of the specified regions
     * 
     * @param regionIds The region IDs to check for
     */
    public RegionCondition(String name,int... regionIds) {
        super(name);
        if (regionIds == null || regionIds.length == 0) {
            throw new IllegalArgumentException("Region IDs cannot be null or empty");
        }
        this.targetRegions = new HashSet<>();
        for (int id : regionIds) {
            targetRegions.add(id);
        }
    }

    @Override
    protected void updateLocationStatus() {
        if (!canCheckLocation()) {
            return;
        }

        try {
            WorldPoint location = getCurrentLocation();
            if (location != null) {
                int currentRegion = location.getRegionID();
                satisfied = targetRegions.contains(currentRegion);
                
                if (satisfied) {
                    log.debug("Player entered target region: {}", currentRegion);
                }
            }
        } catch (Exception e) {
            log.error("Error checking player region", e);
        }
    }

    @Override
    public String getDescription() {
        WorldPoint location = getCurrentLocation();
        String currentRegionInfo = "";
        
        if (location != null) {
            int currentRegion = location.getRegionID();
            boolean inTargetRegion = targetRegions.contains(currentRegion);
            currentRegionInfo = String.format(" (current region: %d, %s)", 
                    currentRegion, inTargetRegion ? "matched" : "not matched");
        }
        
        return "Player in regions: " + Arrays.toString(targetRegions.toArray()) + currentRegionInfo;
    }

    @Override
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Basic description
        int regionCount = targetRegions.size();
        if (regionCount == 1) {
            sb.append("Region Condition: Player must be in a specific region\n");
        } else {
            sb.append("Region Condition: Player must be in one of ").append(regionCount)
              .append(" specified regions\n");
        }
        
        // Status information
        WorldPoint location = getCurrentLocation();
        int currentRegion = -1;
        boolean inTargetRegion = false;
        
        if (location != null) {
            currentRegion = location.getRegionID();
            inTargetRegion = targetRegions.contains(currentRegion);
        }
        
        sb.append("Status: ").append(inTargetRegion ? "Satisfied" : "Not satisfied").append("\n");
        
        // Target region details
        sb.append("Target Regions: ").append(Arrays.toString(targetRegions.toArray())).append("\n");
        
        // Current player region
        if (location != null) {
            sb.append("Current Region: ").append(currentRegion);
            sb.append(inTargetRegion ? " (matched)" : " (not matched)");
        } else {
            sb.append("Current Region: Unknown");
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Basic information
        sb.append("RegionCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        
        int regionCount = targetRegions.size();
        if (regionCount == 1) {
            sb.append("  │ Type: Region (Player must be in specific region)\n");
        } else {
            sb.append("  │ Type: Region (Player must be in one of ")
              .append(regionCount).append(" regions)\n");
        }
        
        sb.append("  │ Target Regions: ").append(Arrays.toString(targetRegions.toArray())).append("\n");
        
        // Status information
        sb.append("  └─ Status ──────────────────────────────────\n");
        WorldPoint location = getCurrentLocation();
        
        if (location != null) {
            int currentRegion = location.getRegionID();
            boolean inTargetRegion = targetRegions.contains(currentRegion);
            
            sb.append("    Current Region: ").append(currentRegion).append("\n");
            sb.append("    Matched: ").append(inTargetRegion).append("\n");
            sb.append("    Satisfied: ").append(inTargetRegion);
        } else {
            sb.append("    Current Region: Unknown\n");
            sb.append("    Satisfied: false");
        }
        
        return sb.toString();
    }
}