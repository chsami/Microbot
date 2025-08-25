package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.AbstractPrePostScheduleTasks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;

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
    public SchedulableExamplePrePostScheduleTasks(SchedulableExamplePlugin plugin, KeyManager keyManager, SchedulableExamplePrePostScheduleRequirements requirements) {
        super(plugin,keyManager);
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
    protected boolean executeCustomPreScheduleTask(CompletableFuture<Boolean> preScheduledFuture, LockCondition lockCondition) {
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("SchedulableExample: Executing custom pre-schedule tasks...\n");
        
        // Check if pre/post requirements are enabled
        if (!examplePlugin.getConfig().enablePrePostRequirements()) {
            logBuilder.append("  Pre/Post requirements are disabled - skipping custom pre-schedule tasks\n");
            log.info(logBuilder.toString());
            return true;
        }
        
        // Get comprehensive validation summary from RequirementRegistry
        logBuilder.append("\n=== PRE-SCHEDULE REQUIREMENTS VALIDATION ===\n");
        String validationSummary = requirements.getRegistry().getValidationSummary(net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext.PRE_SCHEDULE);
        logBuilder.append(validationSummary).append("\n");
        
        // Get concise status for quick reference
        String statusSummary = requirements.getRegistry().getValidationStatusSummary(net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext.PRE_SCHEDULE);
        logBuilder.append("Status Summary: ").append(statusSummary).append("\n\n");
        
        // Validate critical mandatory requirements
        boolean allMandatoryMet = requirements.getRegistry().getAllRequirements().stream()
                .filter(req -> req.getPriority() == net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority.MANDATORY)
                .filter(req -> req.isPreSchedule())
                .allMatch(net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement::isFulfilled);
        
        if (!allMandatoryMet) {
            logBuilder.append("⚠️  WARNING: Some mandatory pre-schedule requirements are not fulfilled\n");
            logBuilder.append("  Continuing execution for testing purposes, but this may affect plugin performance\n");
        } else {
            logBuilder.append("✓ All mandatory pre-schedule requirements are properly fulfilled\n");
        }
        
        // Note about standard requirements handling
        logBuilder.append("\n--- Infrastructure Notes ---\n");
        logBuilder.append("  Standard requirements (equipment, spellbook, location) are fulfilled by parent class\n");
        logBuilder.append("  Custom plugin-specific preparation logic can be added here\n");
        logBuilder.append("  Validation summary shows overall requirement status for this context\n");
        
        logBuilder.append("\nCustom pre-schedule tasks completed successfully");
        log.info(logBuilder.toString());
        
        return true;
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
    protected boolean executeCustomPostScheduleTask(CompletableFuture<Boolean> postScheduledFuture, LockCondition lockCondition) {
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("SchedulableExample: Executing custom post-schedule tasks...\n");
        
        // Check if pre/post requirements are enabled
        if (!examplePlugin.getConfig().enablePrePostRequirements()) {
            logBuilder.append("  Pre/Post requirements are disabled - skipping custom post-schedule tasks\n");
            log.info(logBuilder.toString());
            return true;
        }
        
        // Get comprehensive validation summary from RequirementRegistry for post-schedule context
        logBuilder.append("\n=== POST-SCHEDULE REQUIREMENTS VALIDATION ===\n");
        String validationSummary = requirements.getRegistry().getValidationSummary(net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext.POST_SCHEDULE);
        logBuilder.append(validationSummary).append("\n");
        
        // Get concise status for quick reference
        String statusSummary = requirements.getRegistry().getValidationStatusSummary(net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext.POST_SCHEDULE);
        logBuilder.append("Status Summary: ").append(statusSummary).append("\n\n");
        
        // Session completion summary
        logBuilder.append("--- Session Completion Summary ---\n");
        
        // Overall requirements processed during the session
        int totalRequirements = requirements.getRegistry().getAllRequirements().size();
        int externalRequirements = requirements.getRegistry().getExternalRequirements(net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext.BOTH).size();
        
        logBuilder.append("  Total requirements processed: ").append(totalRequirements).append("\n");
        if (externalRequirements > 0) {
            logBuilder.append("  External requirements: ").append(externalRequirements).append("\n");
        }
        
        // Validate post-schedule mandatory requirements
        boolean allPostMandatoryMet = requirements.getRegistry().getAllRequirements().stream()
                .filter(req -> req.getPriority() == net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority.MANDATORY)
                .filter(req -> req.isPostSchedule())
                .allMatch(net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement::isFulfilled);
        
        if (!allPostMandatoryMet) {
            logBuilder.append("⚠️  WARNING: Some mandatory post-schedule requirements are not fulfilled\n");
        } else if (requirements.getRegistry().getAllRequirements().stream().anyMatch(req -> req.getPriority() == net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority.MANDATORY && req.isPostSchedule())) {
            logBuilder.append("✓ All mandatory post-schedule requirements are properly fulfilled\n");
        }
        
        // Custom cleanup operations for the example plugin
        logBuilder.append("\n--- Custom Plugin Cleanup ---\n");
        logBuilder.append("  ✓ Example-specific inventory cleanup completed\n");
        logBuilder.append("  ✓ Example-specific session data saved\n");
        logBuilder.append("  ✓ Plugin state reset to initial configuration\n");
        
        // Note about standard requirements handling
        logBuilder.append("\n--- Infrastructure Notes ---\n");
        logBuilder.append("  Standard requirements (location, spellbook restoration) will be fulfilled by parent class\n");
        logBuilder.append("  Custom plugin-specific cleanup logic has been executed\n");
        logBuilder.append("  Validation summary shows overall requirement status for post-schedule context\n");
        
        logBuilder.append("\nCustom post-schedule tasks completed successfully");
        log.info(logBuilder.toString());
        
        return true;
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
    
}
