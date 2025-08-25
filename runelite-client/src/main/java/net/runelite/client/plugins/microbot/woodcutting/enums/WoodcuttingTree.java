package net.runelite.client.plugins.microbot.woodcutting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationOption;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.ResourceLocationOption;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.skills.woodcutting.data.WoodcuttingTreeLocations;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum WoodcuttingTree {
    TREE("tree" , "Logs", ItemID.LOGS, 1, "Chop down"),
    OAK("oak tree", "Oak logs", ItemID.OAK_LOGS,15, "Chop down"),
    WILLOW("willow tree", "Willow logs", ItemID.WILLOW_LOGS, 30, "Chop down"),
    TEAK_TREE("teak tree", "Teak logs", ItemID.TEAK_LOGS, 35, "Chop down"),
    MAPLE("maple tree", "Maple logs", ItemID.MAPLE_LOGS, 45, "Chop down"),
    MAHOGANY("mahogany tree", "Mahogany logs", ItemID.MAHOGANY_LOGS, 50, "Chop down"),
    YEW("yew tree", "Yew logs", ItemID.YEW_LOGS, 60, "Chop down"),
    BLISTERWOOD("blisterwood tree", "Blisterwood logs", ItemID.BLISTERWOOD_LOGS, 62, "Chop"),
    MAGIC("magic tree", "Magic logs", ItemID.MAGIC_LOGS, 75, "Chop down"),
    REDWOOD("redwood tree", "Redwood logs", ItemID.REDWOOD_LOGS, 90, "Cut"),
    EVERGREEN_TREE("evergreen tree" , "Logs", ItemID.LOGS, 1, "Chop down"),
    DEAD_TREE("dead tree" , "Logs", ItemID.LOGS, 1, "Chop down"),
    INFECTED_ROOT("infected root", "Logs", ItemID.LOGS, 80, "Chop");

    private final String name;
    private final String log;
    private final int logID;
    private final int woodcuttingLevel;
    private final String action;

    @Override
    public String toString() {
        return name;
    }

    public boolean hasRequiredLevel() {
        return Rs2Player.getSkillRequirement(Skill.WOODCUTTING, this.woodcuttingLevel);
    }
    
    /**
     * Gets all available locations for this tree type.
     * @return List of LocationOption objects containing location data with requirements
     */
    public List<ResourceLocationOption> getLocations() {
        return WoodcuttingTreeLocations.getLocationsForTree(this);
    }
    
    /**
     * Gets all accessible locations for this tree type based on current player requirements.
     * @return List of LocationOption objects that the player can access
     */
    public List<ResourceLocationOption> getAccessibleLocations() {
        return getLocations().stream()
                .filter(LocationOption::hasRequirements)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets the best available location for this tree type.
     * Prioritizes locations with no requirements, then closest accessible location.
     * @return The optimal LocationOption for this tree type, or null if none accessible
     */
    public ResourceLocationOption getBestLocation() {
        List<ResourceLocationOption> locations = getLocations();
        
        // First try to find locations with no requirements
        ResourceLocationOption noReqLocation = locations.stream()
                .filter(loc -> loc.getRequiredQuests().isEmpty() && loc.getRequiredSkills().isEmpty())
                .findFirst()
                .orElse(null);
        
        if (noReqLocation != null) {
            return noReqLocation;
        }
        
        // If no unrestricted locations, return first accessible one
        return locations.stream()
                .filter(LocationOption::hasRequirements)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Checks if the player has access to at least one location for this tree type.
     * @return true if at least one location is accessible, false otherwise
     */
    public boolean hasAccessibleLocation() {
        return getLocations().stream()
                .anyMatch(LocationOption::hasRequirements);
    }
    
    /**
     * Gets a summary of location accessibility for this tree type.
     * Useful for debugging and user interface display.
     * @return String containing location accessibility information
     */
    public String getLocationSummary() {
        List<ResourceLocationOption> allLocations = getLocations();
        long accessibleCount = allLocations.stream()
                .mapToLong(loc -> loc.hasRequirements() ? 1 : 0)
                .sum();
        
        return String.format("%s: %d/%d locations accessible", 
                           name, accessibleCount, allLocations.size());
    }
}
