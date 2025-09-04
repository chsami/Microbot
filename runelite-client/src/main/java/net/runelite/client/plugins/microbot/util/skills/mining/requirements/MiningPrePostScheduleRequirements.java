package net.runelite.client.plugins.microbot.util.skills.mining.requirements;
import net.runelite.client.plugins.microbot.util.skills.mining.data.MiningRockLocations;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.mining.AutoMiningConfig;
import net.runelite.client.plugins.microbot.mining.amethyst.AmethystMiningConfig;
import net.runelite.client.plugins.microbot.mining.enums.Rocks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.ItemRequirementCollection;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationOption;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

import java.util.List;

/**
 * Enhanced implementation showing how to use ItemRequirementCollection for a mining plugin.
 * Demonstrates the new standardized approach to equipment, outfit requirements, and location requirements.
 * 
 * Now includes dynamic location requirements based on the selected ore type with quest and skill requirements.
 */
@Slf4j
public class MiningPrePostScheduleRequirements extends PrePostScheduleRequirements {
    final AmethystMiningConfig amethystMiningConfig;
    final AutoMiningConfig autoMiningConfig;
    
    public MiningPrePostScheduleRequirements(AmethystMiningConfig config) {
        super("Mining", "Mining", false);
        this.amethystMiningConfig = config;
        this.autoMiningConfig = null;
        //TODO Set location pre-schedule requirements - near mining spots for amethyst

        
        initializeRequirements();
    }
    
    public MiningPrePostScheduleRequirements(AutoMiningConfig config) {
        super("Mining", "Mining", false);
        this.amethystMiningConfig = null;
        this.autoMiningConfig = config;
        initializeRequirements();
    }
    
    /**
     * Registers location requirements based on the selected ore type.
     * Uses the MiningRockLocations data to provide optimal locations with requirements.
     */
    private boolean registerRockLocationRequirements() {
        Rocks selectedRock = autoMiningConfig.ORE();
         
        boolean success = true; // Mark as failure if no locations found
        // Get the locations for the selected rock type
        List<LocationOption> rockLocations = MiningRockLocations.getAccessibleLocationsForRock(selectedRock);
        
        if (!rockLocations.isEmpty()) {
            // Create a location requirement with all available locations for this rock type
            LocationRequirement rockLocationRequirement = new LocationRequirement(
                rockLocations,
                10, // Acceptable distance from mining areas
                true, // Use transports for efficient travel                
                -1, // No specific world required, can be any world
                TaskContext.PRE_SCHEDULE,
                RequirementPriority.MANDATORY,
                9, // High rating since location is critical for mining
                "Must be at a suitable " + selectedRock.name() + " mining location to begin mining"
            );
            
            this.register(rockLocationRequirement);
            
            log.info("Registered {} location options for {} rocks", 
                     rockLocations.size(), selectedRock.name());
            
            // Log available locations for debugging
            for (LocationOption location : rockLocations) {
                log.debug("Available location: {} - Accessible: {}", 
                         location.getName(), location.hasRequirements());
            }
        } else {
            log.warn("No locations found for rock type: {}", selectedRock);
            this.getRegistry().clear();
            success = false; // Mark as failure if no locations found
        }
        return success;
    }
    
    @Override
    protected boolean initializeRequirements() {
        this.getRegistry().clear();
        // Register complete outfit and equipment collections using ItemRequirementCollection
        //set location post-schedule requirements - go to grand exchange after mining
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE,true,-1, TaskContext.POST_SCHEDULE, RequirementPriority.MANDATORY));
        // Mining pickaxes - progression-based from bronze to crystal
        ItemRequirementCollection.registerPickAxes(this,RequirementPriority.MANDATORY, TaskContext.PRE_SCHEDULE);
        
        // Prospector/Motherlode Mine outfit - provides XP bonus for mining (includes all variants)
        // we must ensure we equip the varrock armour if available ?  becasue of the bonus or is the motherlode outfit also providing the same bonus? ->  check wiki
        // TODO: Update ItemRequirementCollection.registerProspectorOutfit to accept TaskContext
         ItemRequirementCollection.registerProspectorOutfit(this, RequirementPriority.RECOMMENDED,8, TaskContext.PRE_SCHEDULE, false, false, false, false);
        
        // Varrock diary armour - provides benefits like chance of smelting ore while mining
        // TODO: Update ItemRequirementCollection.registerVarrockDiaryArmour to accept TaskContext
        ItemRequirementCollection.registerVarrockDiaryArmour(this, RequirementPriority.RECOMMENDED, TaskContext.PRE_SCHEDULE);
        
        if (autoMiningConfig != null) {
            // Register location requirements based on selected rock type
            boolean successRockLocationReq = registerRockLocationRequirements();
            if (!successRockLocationReq) {

                log.error("Failed to register rock location requirements. No locations available for selected rock.");
                return false; // Initialization failed
            }
            
            // Set post-schedule location requirements - go to bank after mining
            this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE, true, -1,TaskContext.POST_SCHEDULE, RequirementPriority.RECOMMENDED));
        }                        
        return this.isInitialized();
    }
    
    /**
     * Gets information about the current rock location requirements.
     * Useful for debugging and user interface display.
     */
    public String getLocationRequirementInfo() {
        if (autoMiningConfig == null) {
            return "No auto mining config available";
        }
        
        Rocks selectedRock = autoMiningConfig.ORE();
        List<LocationOption> rockLocations = MiningRockLocations.getLocationsForRock(selectedRock);
        
        StringBuilder info = new StringBuilder();
        info.append("Rock Type: ").append(selectedRock.name()).append("\n");
        info.append("Available Locations: ").append(rockLocations.size()).append("\n");
        
        long accessibleCount = rockLocations.stream()
                .mapToLong(location -> location.hasRequirements() ? 1 : 0)
                .sum();
        
        info.append("Accessible Locations: ").append(accessibleCount).append("\n");
        
        if (accessibleCount == 0) {
            info.append("WARNING: No accessible locations! Check quest/skill requirements.\n");
        }
        
        for (LocationOption location : rockLocations) {
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
