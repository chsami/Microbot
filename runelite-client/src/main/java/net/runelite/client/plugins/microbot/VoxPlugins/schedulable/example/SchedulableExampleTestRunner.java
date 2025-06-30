package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;

/**
 * Test runner for the SchedulableExample plugin's pre/post schedule tasks.
 * This class provides methods to test the functionality of the requirements and tasks
 * without needing to run the full plugin or scheduler system.
 */
@Slf4j
public class SchedulableExampleTestRunner {
    
    
    
    /**
     * Test method to validate requirements configuration.
     * 
     * @param plugin The SchedulableExamplePlugin instance
     */
    public static void testRequirementsConfiguration(SchedulableExamplePlugin plugin) {
        log.info("=== SchedulableExample Requirements Configuration Test ===");
        
        try {
            SchedulableExampleConfig config = plugin.getConfig();
            SchedulableExamplePrePostScheduleTasks tasks = plugin.getPrePostScheduleTasks();
            
            if (tasks == null) {
                log.error("PrePostScheduleTasks not initialized in plugin");
                return;
            }
            
            SchedulableExamplePrePostScheduleRequirements requirements = tasks.getRequirements();
            
            log.info("Configuration Status:");
            log.info("  Pre/Post Requirements Enabled: {}", config.enablePrePostRequirements());            
            log.info("  Loot Requirement: {}", config.enableLootRequirement());
            log.info("  Equipment Requirement: {}", config.enableEquipmentRequirement());
            log.info("  Inventory Requirement: {}", config.enableInventoryRequirement());
            
            if (requirements != null) {
                log.info("\nRequirements Validation:");
                boolean mandatoryValid = requirements.validateItems(Priority.MANDATORY);
                log.info("  Mandatory Items Valid: {}", mandatoryValid);
                
                log.info("\nDetailed Requirements:");
                log.info(requirements.getEnabledRequirementsDisplay());
            }
            
        } catch (Exception e) {
            log.error("Error during requirements configuration test", e);
        }
        
        log.info("=== Requirements Configuration Test Complete ===");
    }
    
    /**
     * Test method to simulate ONLY pre-schedule task execution.
     * This allows testing pre-schedule tasks in isolation.
     * 
     * @param plugin The SchedulableExamplePlugin instance
     */
    public static void testPreScheduleTasksOnly(SchedulableExamplePlugin plugin) {
        log.info("=== SchedulableExample Pre-Schedule Tasks ONLY Test ===");
        
        try {
            // Get the tasks instance from the plugin
            SchedulableExamplePrePostScheduleTasks tasks = plugin.getPrePostScheduleTasks();
            if (tasks == null) {
                log.error("PrePostScheduleTasks not initialized in plugin");
                return;
            }
            
            // Get the requirements instance through the tasks
            SchedulableExamplePrePostScheduleRequirements requirements = tasks.getRequirements();
            if (requirements == null) {
                log.error("Requirements not initialized in tasks");
                return;
            }
            
            log.info("Testing Requirements Display:");
            log.info(requirements.getEnabledRequirementsDisplay());
            
            log.info("\nStarting Pre-Schedule Tasks ONLY (using proper threading infrastructure):");
            // Note: We use the proper public API methods that ensure threading safety
            tasks.executePreScheduleTasks(() -> {
                log.info("=== Pre-Schedule Tasks ONLY Test Complete - SUCCESS ===");
            });
            
            log.info("Pre-Schedule Tasks test initiated - completion will be logged asynchronously");
            
        } catch (Exception e) {
            log.error("Error during pre-schedule test execution", e);
            log.info("=== Pre-Schedule Tasks ONLY Test Complete - FAILED ===");
        }
    }
    
    /**
     * Test method to simulate ONLY post-schedule task execution.
     * This allows testing post-schedule tasks in isolation.
     * 
     * @param plugin The SchedulableExamplePlugin instance
     */
    public static void testPostScheduleTasksOnly(SchedulableExamplePlugin plugin) {
        log.info("=== SchedulableExample Post-Schedule Tasks ONLY Test ===");
        
        try {
            // Get the tasks instance from the plugin
            SchedulableExamplePrePostScheduleTasks tasks = plugin.getPrePostScheduleTasks();
            if (tasks == null) {
                log.error("PrePostScheduleTasks not initialized in plugin");
                return;
            }
            
            // Get the requirements instance through the tasks
            SchedulableExamplePrePostScheduleRequirements requirements = tasks.getRequirements();
            if (requirements == null) {
                log.error("Requirements not initialized in tasks");
                return;
            }
            
            log.info("Testing Requirements Display:");
            log.info(requirements.getEnabledRequirementsDisplay());
            
            log.info("\nStarting Post-Schedule Tasks ONLY (using proper threading infrastructure):");
            tasks.executePostScheduleTasks(() -> {
                log.info("=== Post-Schedule Tasks ONLY Test Complete - SUCCESS ===");
            });
            
            log.info("Post-Schedule Tasks test initiated - completion will be logged asynchronously");
            
        } catch (Exception e) {
            log.error("Error during post-schedule test execution", e);
            log.info("=== Post-Schedule Tasks ONLY Test Complete - FAILED ===");
        }
    }
}
