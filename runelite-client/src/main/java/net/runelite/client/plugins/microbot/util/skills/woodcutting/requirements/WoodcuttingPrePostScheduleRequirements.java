package net.runelite.client.plugins.microbot.util.skills.woodcutting.requirements;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.ItemRequirementCollection;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationOption;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.ResourceLocationOption;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.skills.woodcutting.data.WoodcuttingTreeLocations;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingConfig;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;

/**
 * Enhanced implementation showing how to use ItemRequirementCollection for a woodcutting plugin.
 * Demonstrates the new standardized approach to equipment, outfit requirements, and location requirements.
 * 
 * Now includes dynamic location requirements based on the selected tree type with quest and skill requirements.
 */
@Slf4j
public class WoodcuttingPrePostScheduleRequirements extends PrePostScheduleRequirements {
    
    private final AutoWoodcuttingConfig config;
    
    public WoodcuttingPrePostScheduleRequirements(AutoWoodcuttingConfig config) {
        super("Woodcutting", "Woodcutting", false);        
        this.config = config;    
        initializeRequirements();
    }
    
    /**
     * Enhanced location requirement registration that considers resource availability.
     * This method uses ResourceLocationOption to find locations with sufficient tree spawns.
     * 
     * @param minTreeSpawns Minimum number of tree spawns required at the location
     * @return true if suitable locations are found and registered
     */
    private boolean registerResourceAwareTreeLocationRequirements(int minTreeSpawns) {
        WoodcuttingTree selectedTree = config.TREE();
        
        // Get resource-aware locations with tree count information
        List<ResourceLocationOption> resourceLocations = WoodcuttingTreeLocations.getLocationsForTree(selectedTree);
        
        if (resourceLocations.isEmpty()) {
            log.warn("No resource locations found for tree type: {}", selectedTree);
            return false;
        }
        
        // Find the best location with minimum resource requirements
        ResourceLocationOption bestLocation = WoodcuttingTreeLocations.getBestAccessibleResourceLocation(
                selectedTree, minTreeSpawns);
        
        if (bestLocation == null) {
            log.warn("No accessible locations found with minimum {} tree spawns for {} we select a accessible location instead", 
                     minTreeSpawns, selectedTree);
            // Fall back to any accessible location
            List<ResourceLocationOption> accessibleLocations = WoodcuttingTreeLocations.getAccessibleLocationsForTree(selectedTree);
            if (!accessibleLocations.isEmpty()) {
                bestLocation = accessibleLocations.get(0);
                log.info("Using fallback location: {} with {} tree spawns", 
                         bestLocation.getName(), bestLocation.getNumberOfResources());
            } else {
                return false;
            }
        }
        
        // Make bestLocation effectively final for use in lambda
        final ResourceLocationOption finalBestLocation = bestLocation;
        
        // Convert ResourceLocationOption to regular LocationOption list for LocationRequirement
        List<LocationOption> locationOptions = new ArrayList<>();
        
        // Add the best location first (highest priority)
        locationOptions.add(finalBestLocation);
        
        // Add other suitable locations as alternatives
        List<ResourceLocationOption> suitableLocations = WoodcuttingTreeLocations.getAccessibleLocationsForTree(selectedTree)
                .stream()
                .filter(loc -> loc.hasMinimumResources(Math.max(1, minTreeSpawns - 2))) // Allow locations with slightly fewer trees as backup
                .filter(loc -> !loc.equals(finalBestLocation)) // Don't duplicate the best location
                .sorted((loc1, loc2) -> Integer.compare(loc2.getNumberOfResources(), loc1.getNumberOfResources())) // Sort by resource count descending
                .collect(Collectors.toList());
        
        locationOptions.addAll(suitableLocations);
        
        if (locationOptions.isEmpty()) {
            log.error("No suitable locations found for {} trees", selectedTree);
            return false;
        }
        
        // Create and register the location requirement
        LocationRequirement treeLocationRequirement = new LocationRequirement(
                locationOptions,
                10, // Acceptable distance from tree location
                true, // Use transports for efficient travel
                -1, // No specific world required, can be any world
                TaskContext.PRE_SCHEDULE,
                RequirementPriority.MANDATORY,
                9, // High rating since location is critical for woodcutting
                String.format("Must be at a suitable %s location with at least %d tree spawns", 
                             selectedTree.getName(), minTreeSpawns)
        );
        
        this.register(treeLocationRequirement);
        
        log.info("Registered resource-aware location requirement for {} trees. Best location: {} ({} tree spawns)", 
                 selectedTree.getName(), finalBestLocation.getName(), finalBestLocation.getNumberOfResources());
        
        // Log all registered locations for debugging
        for (LocationOption location : locationOptions) {
            if (location instanceof ResourceLocationOption) {
                ResourceLocationOption resLoc = (ResourceLocationOption) location;
                log.debug("Registered location: {} - Trees: {} - Accessible: {}", 
                         resLoc.getName(), resLoc.getNumberOfResources(), resLoc.hasRequirements());
            } else {
                log.debug("Registered location: {} - Accessible: {}", 
                         location.getName(), location.hasRequirements());
            }
        }
        
        return true;
    }
    
   
    
    @Override
    protected boolean initializeRequirements() {
        if (config == null) {
            return false; // Ensure config is initialized before proceeding
        }
        this.getRegistry().clear();
        // Register complete outfit and equipment collections using ItemRequirementCollection        
        // Woodcutting axes - progression-based from bronze to crystal/3rd age
        ItemRequirementCollection.registerWoodcuttingAxes(this, RequirementPriority.MANDATORY, TaskContext.PRE_SCHEDULE, -1);  // -1 for no inventory slot means the axe can be placed in any inventory slot, and also be equipped, 
        
        // Lumberjack outfit - provides XP bonus for woodcutting
        // Example: Skip head slot if user prefers to wear something else (like slayer helmet)
        ItemRequirementCollection.registerLumberjackOutfit(this, RequirementPriority.RECOMMENDED, 10, TaskContext.PRE_SCHEDULE, false, false, false, false);        

        // Two approaches for location requirements:
        
        // Option 1: Resource-aware location selection (RECOMMENDED for better efficiency)
        // This approach considers the number of tree spawns at each location for optimal woodcutting
        int minTreeSpawns = 3; // Require at least 3 tree spawns for efficient woodcutting
        boolean successResourceAwareReq = registerResourceAwareTreeLocationRequirements(minTreeSpawns);
        
        if (successResourceAwareReq) {
            log.info("Successfully registered resource-aware location requirements with {} minimum tree spawns", minTreeSpawns);
        } else {
            return false; // Initialization failed if no suitable locations found
           
        }        
        // Set post-schedule location requirements - go to bank after woodcutting
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE, true, -1,TaskContext.POST_SCHEDULE, RequirementPriority.RECOMMENDED));
        return this.isInitialized();
    }
    
    /**
     * Gets information about the current tree location requirements.
     * Useful for debugging and user interface display.
     */
    public String getLocationRequirementInfo() {
        WoodcuttingTree selectedTree = config.TREE();
        List<ResourceLocationOption> treeLocations = WoodcuttingTreeLocations.getLocationsForTree(selectedTree);
        
        StringBuilder info = new StringBuilder();
        info.append("Tree Type: ").append(selectedTree.getName()).append("\n");
        info.append("Available Locations: ").append(treeLocations.size()).append("\n");
        
        long accessibleCount = treeLocations.stream()
                .mapToLong(location -> location.hasRequirements() ? 1 : 0)
                .sum();
        
        info.append("Accessible Locations: ").append(accessibleCount).append("\n");
        
        if (accessibleCount == 0) {
            info.append("WARNING: No accessible locations! Check quest/skill requirements.\n");
        }
        
        for (LocationOption location : treeLocations) {
            info.append("  - ").append(location.getName());
            if (!location.hasRequirements()) {
                info.append(" (INACCESSIBLE)");
                if (!location.getRequiredQuests().isEmpty()) {
                    info.append(" - Requires quests: ");
                    location.getRequiredQuests().forEach((quest, state) -> 
                        info.append(quest.name()).append(" (").append(state.name()).append(") "));
                }
                if (!location.getRequiredSkills().isEmpty()) {
                    info.append(" - Requires skills: ");
                    location.getRequiredSkills().forEach((skill, level) -> 
                        info.append(skill.name()).append(" ").append(level).append(" "));
                }
            }
            info.append("\n");
        }
        
        return info.toString();
    }
    @Override
    public void reset() {
        this.getRegistry().clear(); // Clear the registry to remove all requirements
        initializeRequirements(); // Reinitialize requirements  
    }
}
