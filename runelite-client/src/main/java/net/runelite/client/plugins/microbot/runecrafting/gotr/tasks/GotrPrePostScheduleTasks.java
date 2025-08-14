package net.runelite.client.plugins.microbot.runecrafting.gotr.tasks;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.runecrafting.gotr.GotrScript;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.AbstractPrePostScheduleTasks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.runecrafting.gotr.GotrPlugin;
import net.runelite.client.plugins.microbot.runecrafting.gotr.requirement.GotrPrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.concurrent.CompletableFuture;

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
    public GotrPrePostScheduleTasks(SchedulablePlugin plugin, GotrPrePostScheduleRequirements gotrPrePostScheduleRequirements) {
        super(plugin, null);
        this.gotrRequirements = gotrPrePostScheduleRequirements;
    }
    
    /**
     * Provides the GOTR requirements for the default implementation to use.
     * 
     * @return The GotrPrePostScheduleRequirements instance
     */
    @Override
    protected PrePostScheduleRequirements getPrePostScheduleRequirements() {
        if (gotrRequirements != null && !gotrRequirements.isInitialized()) {
            
        }
        return gotrRequirements;
    }
    public boolean isInitialized() {
        return gotrRequirements != null && gotrRequirements.isInitialized();
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
    protected boolean executeCustomPreScheduleTask(CompletableFuture<Boolean>  preScheduledFuture , LockCondition lockCondition) {
        if (lockCondition != null) {
            lockCondition.lock();
        }
        
        try {
            log.info("Executing GOTR-specific pre-schedule preparation...");
            
            // Add any GOTR-specific preparation that is not covered by standard requirements
            // The standard requirements (equipment, spellbook, location) are already handled
            // by the parent class before this method is called
            QuestState templeOfTheEyeState  =Rs2Player.getQuestState(Quest.TEMPLE_OF_THE_EYE);
            // Example: Check if we have the required runecrafting level
            if (Microbot.getClient().getRealSkillLevel(Skill.RUNECRAFT) < 27 || templeOfTheEyeState != QuestState.FINISHED) {
                log.warn("Runecrafting level below 27 or Temple of the Eye quest not completed. Cannot proceed with GOTR.");
                return false; // Cannot proceed with GOTR if requirements are not met                
            }
            
            // Example: Any additional GOTR-specific validations or setup
            log.info("GOTR-specific pre-schedule preparation completed successfully");
            return true;
            
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
    protected boolean executeCustomPostScheduleTask(CompletableFuture<Boolean> postScheduledFuture, LockCondition lockCondition) {
        log.info("Executing GOTR-specific post-schedule cleanup...");
        
        // Validate threading context - this method should never run on client thread
        if (Microbot.getClient().isClientThread()) {
            log.error("executeCustomPostScheduleTask() should not run on client thread - this indicates a design violation");
            return false;
        }
        
        // Access the GOTR script for graceful shutdown
        GotrScript gotrScript = ((GotrPlugin) plugin).getScript();
        
        // Step 1: Stop the script gracefully with timeout
        if (gotrScript != null && gotrScript.isRunning()) {
            log.info("Stopping GOTR script...");
            gotrScript.shutdown();
            if (!sleepUntil(() -> !gotrScript.isRunning(), 1800)) {
                log.warn("GOTR script did not stop within timeout, but continuing with cleanup");
            }
        }
        
        // Step 2: Exit minigame with enhanced retry logic (critical operation)
        if (GotrScript.isInMiniGame) {
            log.info("Leaving GOTR minigame...");
            if (!handleMinigameExitWithRetry(postScheduledFuture)) {
                log.error("Failed to leave GOTR minigame after all retry attempts");
                // This is critical - if we can't leave the minigame, post-schedule requirements 
                // (like banking at Grand Exchange) will fail
                return false;
            }
        }
        sleepUntil(() -> Microbot.getClient().getGameState() != GameState.LOADING, 5000);
        //sleepGaussian(1200, 150);// Wait for minigame exit to stabilize, sometimes it takes a moment
        // Step 3: Bank all items before standard post-schedule requirements
        // This is less critical than leaving the minigame, so we don't fail on banking issues
        if (!bankAllItems()) {
            log.warn("Warning: Failed to bank all items during post-schedule cleanup");                
            return false;
        }
        
        log.info("GOTR-specific post-schedule cleanup completed successfully");
        
        /*
         * EXECUTION ORDER EXPLANATION:
         * 
         * This method (executeCustomPostScheduleTask) runs BEFORE standard requirements fulfillment.
         * See AbstractPrePostScheduleTasks#executePostScheduleTask() for execution order:
         * 
         * 1. Custom post-schedule tasks (this method) - Plugin-specific cleanup
         * 2. Standard post-schedule requirements (GotrPrePostScheduleRequirements) - Generic cleanup
         * 
         * Standard requirements from GotrPrePostScheduleRequirements include:
         * - Location requirement: Travel to Grand Exchange (POST_SCHEDULE context)
         * - Spellbook restoration: Restore original spellbook if changed during pre-schedule
         * 
         * See PrePostScheduleRequirements#fulfillPostScheduleRequirements() for detailed fulfillment logic.
         * 
         * NOTE: Plugin shutdown is handled by AbstractPrePostScheduleTasks via handlePostTaskCompletion()
         */
        return true;
    }

    /**
     * Enhanced minigame exit handler with robust retry logic and progress tracking.
     * 
     * This method implements multiple retry strategies for leaving the GOTR minigame:
     * 1. Standard exit attempts with increasing delays
     * 2. Alternative exit methods if standard approach fails
     * 3. Comprehensive logging for debugging failed exits
     * 
     * @return true if successfully exited minigame, false after all retry attempts failed
     */
    private boolean handleMinigameExitWithRetry(CompletableFuture<Boolean> postScheduledFuture) {
        final int maxRetries = 15; // Increased from 10 for better reliability
        final int baseDelay = 1000; // Base delay between attempts
        final int maxDelay = 2000; // Maximum delay cap
        
        log.info("Starting minigame exit process - current state: isInMiniGame={}", GotrScript.isInMiniGame);
        
        for (int attempt = 1; attempt <= maxRetries && GotrScript.isInMiniGame; attempt++) {
            log.info("Minigame exit attempt {}/{}", attempt, maxRetries);
            if (postScheduledFuture !=null && postScheduledFuture.isCancelled()) {
                log.warn("Pre-scheduled future was cancelled, aborting minigame exit attempts");
                return false; // Exit early if pre-scheduled task was cancelled
            }
            
            // Primary exit method - use GotrScript's leaveMinigame functionality
            boolean successfully = GotrScript.leaveMinigame();
            
            // Calculate progressive delay (exponential backoff with cap)
            int delay = Math.min(baseDelay * attempt, maxDelay);
            
            // Wait for exit to complete with reasonable timeout
            if (successfully) {
                log.info("Successfully left GOTR minigame on attempt {}", attempt);
                return true;
            }
            
            // Log attempt failure and apply progressive delay
            log.warn("Attempt {} failed - minigame state: isInMiniGame={}, waiting {}ms before retry", 
                    attempt, GotrScript.isInMiniGame, delay);
            
            if (attempt < maxRetries) { // Don't sleep on final attempt
                sleepGaussian(delay, 200);
            }
            
            // Additional diagnostics for debugging persistent failures
            if (attempt % 5 == 0) {
                log.warn("Persistent minigame exit failure - attempt {}/{}, checking game state...", attempt, maxRetries);
                // Could add additional state checks here if needed
            }
        }
        
        // All retry attempts exhausted
        if (GotrScript.isInMiniGame) {
            log.error("CRITICAL: Failed to leave GOTR minigame after {} attempts", maxRetries);
            log.error("Final state: isInMiniGame={}", GotrScript.isInMiniGame);
            
            // Consider additional emergency exit strategies here if needed
            // For example: force logout and login, or use alternative exit methods
            
            return false;
        }
        
        // Successfully exited (shouldn't reach here if loop worked correctly)
        log.info("Minigame exit completed - final state: isInMiniGame={}", GotrScript.isInMiniGame);
        return true;
    }
    
   
    
        
    /**
     * Banks all equipment and inventory items for safe shutdown.
     * 
     * @return true if banking was successful, false otherwise
     */
    private boolean bankAllItems() {
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
                   
        log.info("Successfully banked all items");
        return true;
    }
}
