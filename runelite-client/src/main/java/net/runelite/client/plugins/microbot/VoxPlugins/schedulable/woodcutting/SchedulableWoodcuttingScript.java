package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.woodcutting;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;

import java.util.concurrent.TimeUnit;

public class SchedulableWoodcuttingScript extends Script {
    private SchedulableWoodcuttingConfig config;
    private WorldPoint returnPoint;
    private int logsCollected = 0;
    private boolean needsReset = false;
    
    enum State {
        WOODCUTTING,
        RESETTING
    }
    
    private State state = State.WOODCUTTING;

    
    
    public boolean main(SchedulableWoodcuttingConfig config) {
        if (!Microbot.isLoggedIn()) return false;
        if (!super.run()) return false;
        
        // Set initial location if none was saved
        if (initialPlayerLocation == null) {
            initialPlayerLocation = Rs2Player.getWorldLocation();
        }
        
        if (returnPoint == null) {
            returnPoint = initialPlayerLocation;
        }
        
        // Check if we have an axe
        if (!hasAxe()) {
            Microbot.status = "No axe found! Stopping...";
            shutdown();
            return false;
        }
        
        // Skip if player is moving or animating, unless resetting
        if (state != State.RESETTING && (Rs2Player.isMoving() || Rs2Player.isAnimating())) {
            return true;
        }
        
        switch (state) {
            case WOODCUTTING:
                if (Rs2Inventory.isFull()) {
                    state = State.RESETTING;
                    return true;
                }
                
                return cutTree();
                
            case RESETTING:
                resetInventory();
                return true;
        }
        
        return true;
    }
    
    public boolean run(SchedulableWoodcuttingConfig config, WorldPoint savedLocation) {
        this.returnPoint = savedLocation;
        this.config = config;
        this.mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                run();
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    private boolean hasAxe() {
        return Rs2Inventory.hasItem("axe") || Rs2Equipment.hasEquippedContains("axe");
    }
    
    private void resetInventory() {
        if (config.bankLogs()) {
            bankLogs();
        } else {
            dropLogs();
        }
        
        // Update count before moving to next state
        updateLogsCount();
        state = State.WOODCUTTING;
    }
    
    private void bankLogs() {
        Microbot.status = "Banking logs";
        
        // Find and use nearest bank
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.useBank()) {
                return;
            }
        }
        
        // Deposit logs but keep axe
        Rs2Bank.depositAllExcept("axe");
        Rs2Bank.closeBank();
        
        // Return to woodcutting spot
        walkToReturnPoint();
    }
    
    private void dropLogs() {
        Microbot.status = "Dropping logs";
        
        // Drop all logs
        WoodcuttingTree tree = config.tree();
        Rs2Inventory.dropAll(tree.getLog());
    }
    
    private boolean cutTree() {
        Microbot.status = "Looking for " + config.tree().getName();
        
        // Find a tree within range
        GameObject tree = Rs2GameObject.findReachableObject(
            config.tree().getName(), 
            true, 
            config.maxDistance(), 
            returnPoint
        );
        
        if (tree != null) {
            Microbot.status = "Cutting " + config.tree().getName();
            if (Rs2GameObject.interact(tree, config.tree().getAction())) {
                // Update return point to current location
                returnPoint = Rs2Player.getWorldLocation();
                Rs2Player.waitForAnimation();
                return true;
            }
        } else {
            // Move around to find trees
            Microbot.status = "Searching for trees...";
            walkToReturnPoint();
        }
        
        return false;
    }
    
    private void walkToReturnPoint() {
        if (Rs2Player.getWorldLocation().distanceTo(returnPoint) > 3) {
            Rs2Walker.walkTo(returnPoint);
        }
    }
    
    public void updateLogsCount() {
        WoodcuttingTree tree = config.tree();
        int currentLogs = Rs2Inventory.count(tree.getLog());
        
        if (currentLogs > 0) {
            logsCollected += currentLogs;
            Microbot.log("Total logs collected: " + logsCollected);
        }
    }
    
    public int getLogsCollected() {
        return logsCollected;
    }
    
    @Override
    public void shutdown() {
        super.shutdown();
        returnPoint = null;
    }
}