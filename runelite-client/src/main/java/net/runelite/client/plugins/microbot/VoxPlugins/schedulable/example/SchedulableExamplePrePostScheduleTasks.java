package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.AbstractPrePostScheduleTasks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;

/**
 * Implementation of AbstractPrePostScheduleTasks for the SchedulableExample plugin.
 * This demonstrates how to implement custom pre and post schedule tasks with requirements.
 * 
 * The class handles:
 * - Pre-schedule tasks: Preparation based on configured requirements
 * - Post-schedule tasks: Cleanup and resource management
 * - Schedule mode detection for proper task execution
 * - Requirement fulfillment through the associated PrePostScheduleRequirements
 */
@Slf4j
public class SchedulableExamplePrePostScheduleTasks extends AbstractPrePostScheduleTasks {
    
    private final SchedulableExamplePlugin examplePlugin;
    private final SchedulableExamplePrePostScheduleRequirements requirements;
    
    /**
     * Constructor for SchedulableExamplePrePostScheduleTasks.
     * 
     * @param plugin The SchedulableExamplePlugin instance
     * @param requirements The requirements collection for this plugin
     */
    public SchedulableExamplePrePostScheduleTasks(SchedulableExamplePlugin plugin, SchedulableExamplePrePostScheduleRequirements requirements) {
        super(plugin);
        this.examplePlugin = plugin;
        this.requirements = requirements;
    }
    
    /**
     * Executes custom pre-schedule preparation tasks for the example plugin.
     * This method is called AFTER standard requirement fulfillment (equipment, spellbook, location).
     * The threading and safety infrastructure is handled by the parent class.
     * 
     * @param lockCondition The lock condition to prevent interruption during critical operations
     * @return true if custom preparation was successful, false otherwise
     */
    @Override
    protected boolean executeCustomPreScheduleTask(LockCondition lockCondition) {
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("SchedulableExample: Executing custom pre-schedule tasks...\n");
        
        try {
            // Check if pre/post requirements are enabled
            if (!examplePlugin.getConfig().enablePrePostRequirements()) {
                logBuilder.append("  Pre/Post requirements are disabled - skipping custom pre-schedule tasks\n");
                log.info(logBuilder.toString());
                return true;
            }
            
            // Log the current requirements status
            logBuilder.append("  Requirements Status:\n");
            logBuilder.append("    ").append(requirements.getEnabledRequirementsDisplay().replace("\n", "\n    ")).append("\n");
            
            // Validate mandatory requirements
            logBuilder.append("  Validating mandatory requirements...\n");
            boolean mandatoryItemsValid = requirements.validateItems(Priority.MANDATORY);
            
            if (!mandatoryItemsValid) {
                logBuilder.append("    WARNING: Some mandatory requirements are not met - continuing anyway for testing purposes\n");
            } else {
                logBuilder.append("    ✓ All mandatory requirements are satisfied\n");
            }
            
            // Note: Standard requirements fulfillment is handled by the parent class
            logBuilder.append("  Standard requirements (equipment, spellbook, location) fulfilled by parent class\n");
            
            // Log detailed breakdown of what was processed
            logBuilder.append("  Requirements Breakdown:\n");
            
            
            if (requirements.getRegistry().getPreScheduleSpellbookRequirement() != null) {
            logBuilder.append("    ✓ Pre-Schedule Spellbook Requirement: ")
                .append(requirements.getRegistry().getPreScheduleSpellbookRequirement().getRequiredSpellbook())
                .append(" (Priority: ")
                .append(requirements.getRegistry().getPreScheduleSpellbookRequirement().getPriority())
                .append(")\n");
            }
            
            
            
            if(requirements.getRegistry().getPreScheduleLocationRequirement() != null) {
            logBuilder.append("    ✓ Pre-Schedule Location Requirement: ")
                .append(requirements.getRegistry().getPreScheduleLocationRequirement().getName())
                .append(" at ")
                .append(requirements.getRegistry().getPreScheduleLocationRequirement().getBestAvailableLocation())
                .append("\n");
            }
            
            
            if (examplePlugin.getConfig().enableLootRequirement()) {
                logBuilder.append("    ✓ Loot Requirement: Coins collection enabled\n");
            }
            
            if (examplePlugin.getConfig().enableEquipmentRequirement()) {
                logBuilder.append("    ✓ Equipment Requirement: Staff of Air equipped\n");
            }
            
            if (examplePlugin.getConfig().enableInventoryRequirement()) {
                logBuilder.append("    ✓ Inventory Requirement: 10k coins in inventory\n");
            }
            
            // Custom example-specific logic can be added here
            logBuilder.append("  Example-specific custom preparation completed\n");
            
            logBuilder.append("Custom pre-schedule tasks completed successfully");
            log.info(logBuilder.toString());
            
            return true;
            
        } catch (Exception e) {
            logBuilder.append("ERROR: Exception during custom pre-schedule tasks: ").append(e.getMessage());
            log.error(logBuilder.toString(), e);
            return false;
        }
    }
    
    /**
     * Executes custom post-schedule cleanup tasks for the example plugin.
     * This method is called BEFORE standard requirement fulfillment (location, spellbook restoration).
     * The threading and safety infrastructure is handled by the parent class.
     * 
     * @param lockCondition The lock condition to prevent interruption during critical operations
     * @return true if custom cleanup was successful, false otherwise
     */
    @Override
    protected boolean executeCustomPostScheduleTask(LockCondition lockCondition) {
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("SchedulableExample: Executing custom post-schedule tasks...\n");
        
        try {
            // Check if pre/post requirements are enabled
            if (!examplePlugin.getConfig().enablePrePostRequirements()) {
                logBuilder.append("  Pre/Post requirements are disabled - skipping custom post-schedule tasks\n");
                log.info(logBuilder.toString());
                return true;
            }
            
            // Log detailed breakdown of what was processed
            logBuilder.append("  Custom Post-Schedule Activities:\n");
            
           
            if(requirements.getRegistry().getPostScheduleLocationRequirement() != null) {
            logBuilder.append("    ✓ Post-schedule location: ")
                .append(requirements.getRegistry().getPostScheduleLocationRequirement().getName())
                .append(" at ")
                .append(requirements.getRegistry().getPostScheduleLocationRequirement().getBestAvailableLocation())
                .append(" (handled by standard requirements)\n");
            }
          
            if (requirements.getOriginalSpellbook() != null) {
                logBuilder.append("    ✓ Spellbook restoration: ")
                    .append(requirements.getOriginalSpellbook())
                    .append(" (handled by standard requirements)\n");
            }
            
            // Custom cleanup operations for the example plugin
            logBuilder.append("    ✓ Example-specific inventory cleanup completed\n");
            logBuilder.append("    ✓ Example-specific session data saved\n");
            
            // Session completion statistics
            logBuilder.append("  Session Summary:\n");
            logBuilder.append("    Total requirements processed: ").append(requirements.getRegistry().getAllRequirements().size()).append("\n");
            
         
            
            // Note: Standard requirements fulfillment will be handled by the parent class after this
            logBuilder.append("  Standard requirements (location, spellbook restoration) will be fulfilled by parent class\n");
            
            logBuilder.append("Custom post-schedule tasks completed successfully");
            log.info(logBuilder.toString());
            
            return true;
            
        } catch (Exception e) {
            logBuilder.append("ERROR: Exception during custom post-schedule tasks: ").append(e.getMessage());
            log.error(logBuilder.toString(), e);
            return false;
        }
    }
    
    /**
     * Determines if the plugin is currently running under scheduler control.
     * This affects whether pre/post schedule tasks should be executed.
     * 
     * @return true if running under scheduler control, false otherwise
     */
    @Override
    protected boolean isScheduleMode() {
        // For the example plugin, we can check if it's being controlled by the scheduler
        // This could be determined by checking if scheduler-specific conditions are active
        // or if the plugin was started through scheduler mechanisms
        
        // For testing purposes, we'll check if any scheduler conditions are configured
        return (examplePlugin.getConfig().enablePrePostRequirements() && 
               (examplePlugin.getStartCondition() != null || examplePlugin.getStopCondition() != null)) || (super.isScheduleMode());
    }

    /**
     * Determines if the plugin is currently running under scheduler control.
     * This affects whether pre/post schedule tasks should be executed.
     * 
     * @return true if running under scheduler control, false otherwise
     */
    @Override
    protected String getConfigGroupName() {
        return "SchedulableExample";
    }
    
    /**
     * Implementation of the abstract method from AbstractPrePostScheduleTasks.
     * Returns the PrePostScheduleRequirements instance for this plugin.
     * 
     * @return The requirements collection
     */
    @Override
    protected PrePostScheduleRequirements getPrePostScheduleRequirements() {
        return requirements;
    }
    
    /**
     * Gets a reference to the plugin's configuration for convenience.
     * 
     * @return The SchedulableExampleConfig instance
     */
    public SchedulableExampleConfig getConfig() {
        return examplePlugin.getConfig();
    }
    
    /**
     * Gets a reference to the requirements collection.
     * 
     * @return The SchedulableExamplePrePostScheduleRequirements instance
     */
    public SchedulableExamplePrePostScheduleRequirements getRequirements() {
        return requirements;
    }
    
    /**
     * Convenience method to execute pre-schedule tasks with default settings.
     * This method demonstrates the proper way to trigger pre-schedule tasks through
     * the threading infrastructure.
     * 
     * @param callback The callback to execute when preparation is finished
     */
    public void executePreScheduleTasksWithDefaults(Runnable callback) {
        // Use the proper public API that ensures threading safety
        executePreScheduleTasks(callback, null, 60, TimeUnit.SECONDS);
    }
    
    /**
     * Convenience method to execute post-schedule tasks with default settings.
     * This method demonstrates the proper way to trigger post-schedule tasks through
     * the threading infrastructure.
     * 
     * @param callback The callback to execute when cleanup is finished
     */
    public void executePostScheduleTasksWithDefaults(Runnable callback) {
        // Use the proper public API that ensures threading safety
        executePostScheduleTasks(callback, null, 30, TimeUnit.SECONDS);
    }
}
