package net.runelite.client.plugins.microbot.agility.tasks;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.AgilityScript;
import net.runelite.client.plugins.microbot.agility.MicroAgilityConfig;
import net.runelite.client.plugins.microbot.agility.MicroAgilityPlugin;
import net.runelite.client.plugins.microbot.agility.requirement.MicroAgilityPrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.AbstractPrePostScheduleTasks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import net.runelite.client.plugins.microbot.util.Global;
import static net.runelite.client.plugins.microbot.util.Global.sleep;

import java.util.concurrent.CompletableFuture;

/**
 * Handles pre and post schedule tasks for the Micro Agility plugin when operating under scheduler control.
 * Provides setup/preparation logic before scheduled runs and cleanup logic after completion.
 * <p>
 * This implementation extends {@link AbstractPrePostScheduleTasks} to provide agility-specific
 * preparation and cleanup logic while leveraging the common task management infrastructure.
 */
@Slf4j
public class MicroAgilityPrePostScheduleTasks extends AbstractPrePostScheduleTasks {
    
    private MicroAgilityPrePostScheduleRequirements agilityRequirements;
    private final MicroAgilityConfig config;

    /**
     * Constructor for MicroAgilityPrePostScheduleTasks.
     * Initializes the task manager with the provided plugin instance and sets up agility requirements.
     * 
     * @param plugin The SchedulablePlugin instance to manage
     * @param agilityRequirements The pre/post schedule requirements for agility
     */
    public MicroAgilityPrePostScheduleTasks(SchedulablePlugin plugin, MicroAgilityPrePostScheduleRequirements agilityRequirements) {
        super(plugin, null);
        this.agilityRequirements = agilityRequirements;
        this.config = ((MicroAgilityPlugin) plugin).getConfig();
    }
    
    /**
     * Provides the agility requirements for the default implementation to use.
     * 
     * @return The MicroAgilityPrePostScheduleRequirements instance
     */
    @Override
    protected PrePostScheduleRequirements getPrePostScheduleRequirements() {
        return agilityRequirements;
    }
    
   
    
    public boolean isInitialized() {
        return agilityRequirements != null && agilityRequirements.isInitialized();
    }
    
    /**
     * Executes custom pre-schedule actions specific to agility training preparation.
     * This method is called AFTER standard requirement fulfillment (equipment, spellbook, location).
     * The threading and safety infrastructure is handled by the parent class.
     * 
     * @param preScheduledFuture The future representing the pre-schedule task execution
     * @param lockCondition The lock condition to prevent interruption during critical operations
     * @return true if custom preparation was successful, false otherwise
     */
    @Override
    protected boolean executeCustomPreScheduleTask(CompletableFuture<Boolean> preScheduledFuture, LockCondition lockCondition) {
        if (lockCondition != null) {
            lockCondition.lock();
        }
        
        try {
            StringBuilder logMessage = new StringBuilder();
            logMessage.append("Executing agility-specific pre-schedule preparation...");            
            // Check if we have the required agility level for the selected course
            int requiredLevel = config.agilityCourse().getHandler().getRequiredLevel();
            int currentLevel = Rs2Player.getRealSkillLevel(Skill.AGILITY);            
            if (currentLevel < requiredLevel) {
                log.warn("\n\tAgility level {} is below required level {} for course {}. Cannot proceed.", 
                    currentLevel, requiredLevel, config.agilityCourse().name());
                return false;
            }            
            // Additional validation for alchemy if enabled
            if (config.alchemy()) {
                final String items = config.itemsToAlch();
                if (items == null || config.itemsToAlch().trim().isEmpty()) {
                    logMessage.append("Alchemy is enabled but no items to alch specified. Continuing without alchemy.");
                } else {
                    logMessage.append(String.format("Alchemy is enabled with items: {}", config.itemsToAlch()));                    
                    // Check if we have sufficient magic level for High Alchemy
                    int magicLevel = Rs2Player.getRealSkillLevel(Skill.MAGIC);
                    if (magicLevel < 55) {
                        ConfigDescriptor agilityPluginConfigDescripor = this.plugin.getConfigDescriptor();
                        if (agilityPluginConfigDescripor != null && agilityPluginConfigDescripor.getGroup() != null) {
                            log.warn("Magic level {} is below 55 required for High Alchemy. Alchemy disabled.", magicLevel);
                            Microbot.getConfigManager().setConfiguration(agilityPluginConfigDescripor.getGroup().value(), "alchemy", (boolean)false);
                            // Note: We could disable alchemy in config here, but that might not be desired
                        }else {
                            log.warn("Cannot disable alchemy in config (descriptor unavailable)");                            
                        }
                    }
                }
            }
            
            // Log readiness status
            logMessage.append(System.lineSeparator())
                .append("Agility plugin preparation summary:").append(System.lineSeparator())
                .append("  Course: ").append(config.agilityCourse().name())
                .append(" (Level ").append(currentLevel).append("/").append(requiredLevel).append(")").append(System.lineSeparator())
                .append("  Alchemy: ").append(config.alchemy() ? "Enabled" : "Disabled").append(System.lineSeparator())
                .append("  Membership: ").append(Rs2Player.isMember() ? "Yes" : "No").append(System.lineSeparator())
                .append("Agility-specific pre-schedule preparation completed successfully");
            log.info(logMessage.toString());
            return true;
            
        } finally {
            if (lockCondition != null) {
                lockCondition.unlock();
            }
        }
    }
    
    /**
     * Executes custom post-schedule actions specific to agility training cleanup.
     * This method is called BEFORE standard requirement fulfillment (banking, location cleanup).
     * The threading and safety infrastructure is handled by the parent class.
     * 
     * @param postScheduledFuture The future representing the post-schedule task execution
     * @param lockCondition The lock condition to prevent interruption during critical operations
     * @return true if custom cleanup was successful, false otherwise
     */
    @Override
    protected boolean executeCustomPostScheduleTask(CompletableFuture<Boolean> postScheduledFuture, LockCondition lockCondition) {
        if (lockCondition != null) {
            lockCondition.lock();
        }
        
        try {
            log.info("Executing agility-specific post-schedule cleanup...");
            
            // Stop the agility script if it's running
            AgilityScript script = ((MicroAgilityPlugin) plugin).getAgilityScript();
            if (script != null && script.isRunning()) {
                log.info("Stopping agility script...");
                script.shutdown();
                
                // Wait for script to stop
                Global.sleepUntil(() -> !script.isRunning(), 5000);
                
                if (script.isRunning()) {
                    log.warn("Agility script did not stop within timeout");
                } else {
                    log.info("Agility script stopped successfully");
                }
            }
                                
            // Additional cleanup tasks specific to agility could go here
            // For example: resetting any agility-specific states, clearing cached data, etc.
            
            log.info("Agility-specific post-schedule cleanup completed successfully");
            return true;
            
        } finally {
            if (lockCondition != null) {
                lockCondition.unlock();
            }
        }
    }
}