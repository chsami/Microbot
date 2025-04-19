package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;

import java.util.ArrayList;
import java.util.List;


import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


public class SchedulableExampleScript extends Script {
    private SchedulableExampleConfig config;
    private WorldPoint returnPoint;
    private int logsCollected = 0;
    private boolean needsReset = false;
    
    enum State {
        IDELE,
        RESETTING
    }
    
    private State state = State.IDELE;

    
    
    public boolean main(SchedulableExampleConfig config) {
        if (!Microbot.isLoggedIn()) return false;
        if (!super.run()) return false;
        
        // Set initial location if none was saved
        if (initialPlayerLocation == null) {
            initialPlayerLocation = Rs2Player.getWorldLocation();
        }
        
        if (this.returnPoint == null) {
            this.returnPoint = initialPlayerLocation;
        }
        
        // Check if we have an axe
        if (!hasAxe()) {
            Microbot.status = "No axe found! Stopping...";
//            shutdown();
            return false;
        }
        
        // Skip if player is moving or animating, unless resetting
        if (state != State.RESETTING && (Rs2Player.isMoving() || Rs2Player.isAnimating())) {
            return true;
        }
        
        switch (state) {
            case IDELE:
                if (Rs2Inventory.isFull()) {
                    state = State.RESETTING;
                    return true;
                }
                

                
            case RESETTING:
                resetInventory();
                return true;
        }
        
        return true;
    }
    
    public boolean run(SchedulableExampleConfig config, WorldPoint savedLocation) {
        this.returnPoint = savedLocation;
        this.config = config;
        this.mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return ;
                return; //manuel play testing the Scheduler plugin.. doing nothing for now
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
        // Update count before moving to next state
        updateItemCount();
        state = State.IDELE;
    }
    
    private void bankItems() {
        Microbot.status = "Banking ";
        
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
    private List<Pattern> getLootItemPatterns(){
        String lootItemsString = config.lootItems();
        List<String> lootItemsList = new ArrayList<>();
        List<Pattern> lootItemsListPattern = new ArrayList<>();
        if (lootItemsString != null && !lootItemsString.isEmpty()) {
            String[] lootItemsArray = lootItemsString.split(",");
            int validPatternsCount = 0;
            for (String item : lootItemsArray) {
                String trimmedItem = item.trim();
                try {
                    // Validate regex pattern
                    lootItemsListPattern.add(java.util.regex.Pattern.compile(trimmedItem));
                    lootItemsList.add(trimmedItem);
                    //log.info("Valid loot item pattern found: {}", trimmedItem);
                    validPatternsCount++;
                } catch (java.util.regex.PatternSyntaxException e) {
                    //log.warn("Invalid regex pattern: '{}' - {}", trimmedItem, e.getMessage());
                }
            }
            //log.info("Total valid loot item patterns: {}", validPatternsCount);
        }
        return lootItemsListPattern;
    }
    private void dropItems() {
        Microbot.status = "Dropping Items";
        
        // Drop all logs
        List<Pattern> lootItemsListPattern = getLootItemPatterns();
        //List<Rs2ItemModel> foundItems =  
        Rs2Inventory.all().forEach(item -> {
            if (lootItemsListPattern.stream().anyMatch(pattern -> pattern.matcher(item.getName()).find())) {                
                
            }else{
                // Drop all logs
                Rs2Inventory.dropAll(item.getName());
            }
            
        });
        // Drop all logs
        
        }
    

    
    private void walkToReturnPoint() {
        if (Rs2Player.getWorldLocation().distanceTo(returnPoint) > 3) {
            Rs2Walker.walkTo(returnPoint);
        }
    }
    
    public void updateItemCount() {
        List<Pattern> lootItemsListPattern = getLootItemPatterns();
        int currentItems =Rs2Inventory.all().stream().filter(item -> lootItemsListPattern.stream().anyMatch(pattern -> pattern.matcher(item.getName()).find())).mapToInt(Rs2ItemModel::getQuantity).sum();
        
        
        if (currentItems > 0) {
            logsCollected += currentItems;
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