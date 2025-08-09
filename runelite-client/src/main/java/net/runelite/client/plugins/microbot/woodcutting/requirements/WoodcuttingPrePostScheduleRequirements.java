package net.runelite.client.plugins.microbot.woodcutting.requirements;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.ItemRequirementCollection;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingConfig;
import net.runelite.client.plugins.microbot.woodcutting.data.WoodcuttingTreeLocations;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;

import java.util.List;

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
     * Registers location requirements based on the selected tree type.
     * Uses the WoodcuttingTreeLocations data to provide optimal locations with requirements.
     */
    private boolean registerTreeLocationRequirements() {
        WoodcuttingTree selectedTree = config.TREE();
         
        boolean success = true; // Mark as failure if no store found
        // Get the locations for the selected tree type
        List<LocationRequirement.LocationOption> treeLocations = WoodcuttingTreeLocations.getAccessibleLocationsForTree(selectedTree);
        
        if (!treeLocations.isEmpty()) {
            // Create a location requirement with all available locations for this tree type
            LocationRequirement treeLocationRequirement = new LocationRequirement(
                treeLocations,
                10, // Acceptable distance from tree areas
                true, // Use transports for efficient travel                
                ScheduleContext.PRE_SCHEDULE,
                Priority.MANDATORY,
                9, // High rating since location is critical for woodcutting
                "Must be at a suitable " + selectedTree.getName() + " location to begin woodcutting"
            );
            
            this.register(treeLocationRequirement);
            
            log.info("Registered {} location options for {} trees", 
                     treeLocations.size(), selectedTree.getName());
            
            // Log available locations for debugging
            for (LocationRequirement.LocationOption location : treeLocations) {
                log.debug("Available location: {} - Accessible: {}", 
                         location.getName(), location.hasRequirements());
            }
        } else {
            log.warn("No locations found for tree type: {}", selectedTree);
            this.getRegistry().clear();
            success = false; // Mark as failure if no store found
        }
        return success;
    }
    
    @Override
    protected boolean initializeRequirements() {
        if (config == null) {
            return false; // Ensure config is initialized before proceeding
        }
        this.getRegistry().clear();
        // Register complete outfit and equipment collections using ItemRequirementCollection        
        // Woodcutting axes - progression-based from bronze to crystal/3rd age
        ItemRequirementCollection.registerWoodcuttingAxes(this, Priority.MANDATORY, ScheduleContext.PRE_SCHEDULE, -1);  // -1 for no inventory slot means the axe can be placed in any inventory slot, and also be equipped, -2 would mean it can only be equipped      
        
        // Lumberjack outfit - provides XP bonus for woodcutting
        // Example: Skip head slot if user prefers to wear something else (like slayer helmet)
        ItemRequirementCollection.registerLumberjackOutfit(this, Priority.RECOMMENDED, 10, ScheduleContext.PRE_SCHEDULE, false, false, false, false);        

            
        // Register location requirements based on selected tree
        boolean successTreeLocationReq = registerTreeLocationRequirements();
        if (!successTreeLocationReq) {
            this.setInitialized(successTreeLocationReq);
            log.error("Failed to register tree location requirements. No locations available for selected tree.");
            return false; // Initialization failed
        }
        
        // Set post-schedule location requirements - go to bank after woodcutting
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE, true, ScheduleContext.POST_SCHEDULE, Priority.OPTIONAL));
        this.setInitialized(true);
        return this.isInitialized();
    }
    
    /**
     * Gets information about the current tree location requirements.
     * Useful for debugging and user interface display.
     */
    public String getLocationRequirementInfo() {
        WoodcuttingTree selectedTree = config.TREE();
        List<LocationRequirement.LocationOption> treeLocations = WoodcuttingTreeLocations.getLocationsForTree(selectedTree);
        
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
        
        for (LocationRequirement.LocationOption location : treeLocations) {
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
}
