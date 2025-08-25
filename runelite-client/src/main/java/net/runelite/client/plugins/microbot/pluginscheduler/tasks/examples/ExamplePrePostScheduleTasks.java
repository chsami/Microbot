package net.runelite.client.plugins.microbot.pluginscheduler.tasks.examples;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.AbstractPrePostScheduleTasks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.concurrent.CompletableFuture;

import javax.net.ssl.KeyManager;

/**
 * Example implementation showing how to extend {@link AbstractPrePostScheduleTasks}
 * for a generic plugin with basic banking and equipment management.
 * <p>
 * This serves as a template for creating pre/post schedule tasks for other plugins.
 * Simply copy this class and customize the preparation and cleanup logic for your specific plugin needs.
 * 
 * TODO: Customize the following methods for your plugin:
 * - {@link #executePreScheduleTask(LockCondition)} - Add your plugin's preparation logic
 * - {@link #executePostScheduleTask(LockCondition)} - Add your plugin's cleanup logic  
 * - {@link #isScheduleMode()} - Check your plugin's configuration for schedule mode
 * - Add any plugin-specific helper methods as needed
 */
@Slf4j
public class ExamplePrePostScheduleTasks extends AbstractPrePostScheduleTasks {
    
    private ExamplePrePostScheduleRequirements exampleRequirements;

    /**
     * Constructor for ExamplePrePostScheduleTasks.
     * 
     * @param plugin The SchedulablePlugin instance to manage
     */
    public ExamplePrePostScheduleTasks(SchedulablePlugin plugin) {
        super(plugin,null);
        this.exampleRequirements = new ExamplePrePostScheduleRequirements();
    }
    
    /**
     * Provides the example requirements for the default implementation to use.
     * 
     * @return The ExamplePrePostScheduleRequirements instance
     */
    @Override
    protected PrePostScheduleRequirements getPrePostScheduleRequirements() {
        return exampleRequirements;
    }
    
    /**
     * Executes pre-schedule preparation for the example plugin.
     * Customize this method with your plugin's specific preparation logic.
     * 
     * @param lockCondition The lock condition to prevent interruption during critical operations
     * @return true if preparation was successful, false otherwise
     */
    @Override
    protected boolean executeCustomPreScheduleTask(CompletableFuture<Boolean> preScheduledFuture, LockCondition lockCondition) {
        if (lockCondition != null) {
            lockCondition.lock();
        }
        
        try {
            log.info("Starting example plugin pre-schedule preparation...");
            
            // Example: Walk to Grand Exchange bank
            if (!walkToBank()) {
                log.error("Failed to reach bank location");
                return false;
            }
            
            // Example: Prepare basic equipment and inventory
            if (!prepareBasicSetup()) {
                log.error("Failed to prepare basic setup");
                return false;
            }
            
            log.info("Example plugin pre-schedule preparation completed successfully");
            return true;
            
        } finally {
            if (lockCondition != null) {
                lockCondition.unlock();
            }
        }
    }
    
    /**
     * Executes post-schedule cleanup for the example plugin.
     * Customize this method with your plugin's specific cleanup logic.
     * 
     * @param lockCondition The lock condition to prevent interruption during critical operations
     * @return true if cleanup was successful, false otherwise
     */
    @Override
    protected boolean executeCustomPostScheduleTask(CompletableFuture<Boolean> postScheduledFuturem, LockCondition lockCondition) {
        log.info("Starting example plugin post-schedule cleanup...");
        
        // Example: Bank all items for safe shutdown
        if (!bankAllItems()) {
            log.warn("Warning: Failed to bank all items during post-schedule cleanup");
        }
        
        log.info("Example plugin post-schedule cleanup completed - stopping plugin");
        Microbot.getClientThread().invokeLater(() -> {
            Microbot.stopPlugin((net.runelite.client.plugins.Plugin) plugin);
            return true;
        });
        
        return true;
    }
    
  
    
    /**
     * Example helper method: Walks to a bank location.
     * Customize this for your plugin's specific bank location needs.
     * 
     * @return true if successfully reached the bank, false otherwise
     */
    private boolean walkToBank() {
        // Example: Walk to Grand Exchange bank
        if (Rs2Bank.isNearBank(BankLocation.GRAND_EXCHANGE, 6)) {
            return true;
        }
        
        log.info("Walking to bank...");
        
        boolean walkResult = Rs2Walker.walkWithBankedTransports(
            BankLocation.GRAND_EXCHANGE.getWorldPoint(), 
            false   // Don't force banking route if direct is faster
        );
        
        if (!walkResult) {
            log.warn("Failed to initiate walking to bank, trying fallback method");
            Rs2Walker.walkTo(BankLocation.GRAND_EXCHANGE.getWorldPoint(), 4);
        }
        
        return sleepUntil(() -> Rs2Bank.isNearBank(BankLocation.GRAND_EXCHANGE, 6), 30000);
    }
    
    /**
     * Example helper method: Prepares basic equipment and inventory setup.
     * Customize this for your plugin's specific equipment and item needs.
     * 
     * @return true if setup was successful, false otherwise
     */
    private boolean prepareBasicSetup() {
        if (!Rs2Bank.openBank()) {
            log.error("Failed to open bank");
            return false;
        }
        
        // Deposit all current items
        Rs2Bank.depositAll();
        sleepUntil(() -> Rs2Inventory.isEmpty(), 5000);
        Rs2Bank.depositEquipment();
        sleepUntil(() -> Rs2Equipment.isNaked(), 5000);
        
        // TODO: Add your plugin's specific equipment and item withdrawal logic here
        // Example:
        // Rs2Bank.withdrawOne(ItemID.BRONZE_PICKAXE);
        // Rs2Bank.withdrawX(ItemID.SALMON, 10);
        
        Rs2Bank.closeBank();
        log.info("Successfully prepared basic setup");
        return true;
    }
    
    /**
     * Example helper method: Banks all equipment and inventory items for safe shutdown.
     * This is a generic implementation that most plugins can use as-is.
     * 
     * @return true if banking was successful, false otherwise
     */
    private boolean bankAllItems() {
        // Walk to bank if not already there
        if (!Rs2Bank.isNearBank(6)) {
            if (!walkToBank()) {
                return false;
            }
        }
        
        if (!Rs2Bank.openBank()) {
            return false;
        }
        
        // Deposit all inventory items
        Rs2Bank.depositAll();
        sleepUntil(() -> Rs2Inventory.isEmpty(), 5000);
        
        // Deposit all equipment
        Rs2Bank.depositEquipment();
        
        Rs2Bank.closeBank();
        log.info("Successfully banked all items");
        return true;
    }
}
