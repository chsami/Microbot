package net.runelite.client.plugins.microbot.runecrafting.gotr;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.AbstractPrePostScheduleTasks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.runecrafting.gotr.requirement.GotrPrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Handles pre and post schedule tasks for the GOTR plugin when operating under scheduler control.
 * Provides setup/preparation logic before scheduled runs and cleanup logic after completion.
 * <p>
 * This implementation extends {@link AbstractPrePostScheduleTasks} to provide GOTR-specific
 * preparation and cleanup logic while leveraging the common task management infrastructure.
 */
@Slf4j
public class GotrPrePostScheduleTasks extends AbstractPrePostScheduleTasks {
    
    private GotrPrePostScheduleRequirements gotrRequirements;

    /**
     * Constructor for GotrPrePostScheduleTasks.
     * Initializes the task manager with the provided plugin instance and sets up GOTR requirements.
     * 
     * @param plugin The SchedulablePlugin instance to manage
     */
    public GotrPrePostScheduleTasks(SchedulablePlugin plugin) {
        super(plugin);
        this.gotrRequirements = new GotrPrePostScheduleRequirements();
    }
    
    /**
     * Provides the GOTR requirements for the default implementation to use.
     * 
     * @return The GotrPrePostScheduleRequirements instance
     */
    @Override
    protected PrePostScheduleRequirements getPrePostScheduleRequirements() {
        return gotrRequirements;
    }
    
    /**
     * Executes custom pre-schedule actions specific to GOTR preparation.
     * This method is called AFTER standard requirement fulfillment (equipment, spellbook, location).
     * The threading and safety infrastructure is handled by the parent class.
     * 
     * @param lockCondition The lock condition to prevent interruption during critical operations
     * @return true if custom preparation was successful, false otherwise
     */
    @Override
    protected boolean executeCustomPreScheduleTask(LockCondition lockCondition) {
        try {
            if (lockCondition != null) {
                lockCondition.lock();
            }
            
            log.info("Executing GOTR-specific pre-schedule preparation...");
            
            // Add any GOTR-specific preparation that is not covered by standard requirements
            // The standard requirements (equipment, spellbook, location) are already handled
            // by the parent class before this method is called
            
            // Example: Check if we have the required runecrafting level
            if (Microbot.getClient().getRealSkillLevel(Skill.RUNECRAFT) < 27) {
                log.warn("Runecrafting level below 27 - GOTR may not be efficient");
                // Could return false here if level is critical, or continue with warning
            }
            
            // Example: Any additional GOTR-specific validations or setup
            log.info("GOTR-specific pre-schedule preparation completed successfully");
            return true;
            
        } catch (Exception e) {
            log.error("Error during GOTR custom pre-schedule preparation: {}", e.getMessage(), e);
            return false;
        } finally {
            if (lockCondition != null) {
                lockCondition.unlock();
            }
        }
    }
    
    /**
     * Executes custom post-schedule cleanup actions specific to GOTR.
     * This method is called BEFORE standard requirement fulfillment (location, spellbook restoration).
     * The threading and safety infrastructure is handled by the parent class.
     * 
     * @param lockCondition The lock condition to prevent interruption during critical operations  
     * @return true if custom cleanup was successful, false otherwise
     */
    @Override
    protected boolean executeCustomPostScheduleTask(LockCondition lockCondition) {
        try {
            log.info("Executing GOTR-specific post-schedule cleanup...");
            if (Microbot.getClient().isClientThread()) {
                 log.error("executeCustomPostScheduleTask() should not run on client thread - this indicates a design violation");
                return false;
            }
            
            GotrScript gotrScript = ((GotrPlugin) plugin).gotrScript;
            
            // Stop the script gracefully
            if (gotrScript != null) {
                log.info("Stopping GOTR script...");
                gotrScript.shutdown();
                sleepUntil(() -> !gotrScript.isRunning(), 1800);
            }
            
            // Leave the minigame if still inside
            if (GotrScript.isInMiniGame) {
                log.info("Leaving GOTR minigame...");
                if (!handleMinigameExit()) {
                    log.warn("Failed to leave GOTR minigame, but continuing with cleanup");
                    // Don't return false here as we want to continue with other cleanup
                }
            }
            
            // Bank all items before standard post-schedule requirements
            if (!bankAllItems()) {
                log.warn("Warning: Failed to bank all items during post-schedule cleanup");
                // Don't return false here as banking issues shouldn't prevent other cleanup
            }
            
            log.info("GOTR-specific post-schedule cleanup completed");
            
            // Stop the plugin - this is GOTR-specific behavior
            log.info("Stopping GOTR plugin...");
            Microbot.getClientThread().invokeLater(() -> {
                Microbot.stopPlugin((GotrPlugin) plugin);
                return true;
            });
            
            return true;
            
        } catch (Exception ex) {
            log.error("Error during GOTR custom post-schedule cleanup: {}", ex.getMessage(), ex);
            // Even if custom cleanup fails, we still want to try standard cleanup
            // So we'll stop the plugin but return false to indicate issues
            Microbot.getClientThread().invokeLater(() -> {
                Microbot.stopPlugin((GotrPlugin) plugin);
                return true;
            });
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
    protected String getConfigGroupName() {
        return "gotr";
    }
    
   
    
    /**
     * Handles exiting from the GOTR minigame with retry logic.
     * 
     * @return true if successfully exited, false otherwise
     */
    private boolean handleMinigameExit() {
        final int maxRetries = 10;
        int retryCount = 0;
        final int sleepTime = 3000; // 3 seconds between retries
        
        while (GotrScript.isInMiniGame && retryCount < maxRetries) {
            GotrScript.leaveMinigame();
            if (sleepUntil(() -> !GotrScript.isInMiniGame, 1800)) {
                log.info("Successfully left GOTR minigame");
                return true;
            } else {
                retryCount++;
                log.warn("Attempt {} to leave minigame failed, sleeping and retrying...", retryCount);
                sleep(sleepTime);
            }
        }
        
        if (GotrScript.isInMiniGame) {
            log.error("Failed to leave GOTR minigame after {} attempts", maxRetries);
            return false;
        }
        
        return true;
    }
    
    /**
     * Banks all equipment and inventory items for safe shutdown.
     * 
     * @return true if banking was successful, false otherwise
     */
    private boolean bankAllItems() {
        try {
            // Walk to bank if not already there
            if (!Rs2Bank.isNearBank(6)) {
                if (Rs2Bank.walkToBank()) {
                    return false;
                }
            }
            
            if (!Rs2Bank.openBank()) {
                return false;
            }
            
            // Deposit all inventory items
            Rs2Bank.depositAll();
            sleepUntil(() -> Rs2Inventory.isEmpty(), 5000);
            
            // Deposit all equipment except basic items
            Rs2Bank.depositEquipment();
            
            Rs2Bank.closeBank();
            log.info("Successfully banked all items");
            return true;
            
        } catch (Exception e) {
            log.error("Error banking items: {}", e.getMessage(), e);
            return false;
        }
    }
}
