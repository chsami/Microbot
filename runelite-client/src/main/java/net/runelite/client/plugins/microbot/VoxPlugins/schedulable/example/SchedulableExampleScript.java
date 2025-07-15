package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import net.runelite.api.Constants;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
// import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SchedulableExampleScript extends Script {
    private SchedulableExampleConfig config;
    private WorldPoint returnPoint;
    private int logsCollected = 0;
    
    // Antiban testing state
    private boolean antibanOriginalTakeMicroBreaks = false;
    private double antibanOriginalMicroBreakChance = 0.0;
    private int antibanOriginalMicroBreakDurationLow = 3;
    private int antibanOriginalMicroBreakDurationHigh = 15;
    private boolean antibanOriginalActionCooldownActive = false;
    private boolean antibanOriginalMoveMouseOffScreen = false;
    private long lastStatusReport = 0;
    private long lastBreakStatusCheck = 0;
    private boolean wasOnBreak = false;
    private long breakStartTime = 0;
    private long totalBreakTime = 0;
    private int microBreakCount = 0;
    private boolean antibanInitialized = false;
    
    enum State {
        IDELE,
        RESETTING,
        BREAK_PAUSED
    }
    
    private State state = State.IDELE;

    
    
    public boolean main(SchedulableExampleConfig config) {
        if (!Microbot.isLoggedIn()) return false;
        if (!super.run()) return false;
        
        // Initialize antiban settings if enabled
        if (config.enableAntibanTesting() && !antibanInitialized) {
            setupAntibanTesting();
        }
        
        // Check break status and handle state changes
        handleBreakStatusChecks();
        
        // Set initial location if none was saved
        if (initialPlayerLocation == null) {
            initialPlayerLocation = Rs2Player.getWorldLocation();
        }
        
        if (this.returnPoint == null) {
            this.returnPoint = initialPlayerLocation;
        }
        
        // Check if we have an axe
        if (!hasAxe()) {
            //            Microbot.status = "No axe found! Stopping...";
            //          return false;
        }
        
        // Handle break pause state - don't do anything while on break
        if (state == State.BREAK_PAUSED) {
            return true;
        }
        
        // Skip if player is moving or animating, unless resetting
        if (state != State.RESETTING && (Rs2Player.isMoving() || Rs2Player.isAnimating())) {
            return true;
        }
        
        // Trigger antiban behaviors if enabled
        if (config.enableAntibanTesting()) {
            handleAntibanBehaviors();
        }
        
        switch (state) {
            case IDELE:
                if (Rs2Inventory.isFull()) {
                    state = State.RESETTING;
                    return true;
                }
                break;
                
            case RESETTING:
                resetInventory();
                return true;
                
            case BREAK_PAUSED:
                // Already handled above
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
                if (!super.run()) return;
                
                // Call the main method with antiban testing
                main(config);                
            } catch (Exception ex) {
                Microbot.log("SchedulableExampleScript error: " + ex.getMessage());
            }
        }, 0, Constants.GAME_TICK_LENGTH*2, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    private boolean hasAxe() {
        return Rs2Inventory.hasItem("axe") || Rs2Equipment.isWearing("axe");
    }
    
    private void resetInventory() {                
        // Update count before moving to next state
        updateItemCount();
        state = State.IDELE;
    }
    
    /*
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
    */
    private List<Pattern> getLootItemPatterns(){
        String lootItemsString = config.lootItems();
        List<String> lootItemsList = new ArrayList<>();
        List<Pattern> lootItemsListPattern = new ArrayList<>();
        if (lootItemsString != null && !lootItemsString.isEmpty()) {
            String[] lootItemsArray = lootItemsString.split(",");
            for (String item : lootItemsArray) {
                String trimmedItem = item.trim();
                try {
                    // Validate regex pattern
                    lootItemsListPattern.add(java.util.regex.Pattern.compile(trimmedItem));
                    lootItemsList.add(trimmedItem);
                } catch (java.util.regex.PatternSyntaxException e) {
                    //log.warn("Invalid regex pattern: '{}' - {}", trimmedItem, e.getMessage());
                }
            }
        }
        return lootItemsListPattern;
    }
    /*
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
    */
    
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
        // Teardown antiban testing if it was initialized
        if (config != null && config.enableAntibanTesting()) {
            teardownAntibanTesting();
        }
        
        super.shutdown();
        returnPoint = null;
        
        // Log final antiban stats if testing was enabled
        if (config != null && config.enableAntibanTesting()) {
            Microbot.log("Final " + getAntibanStats());
        }
    }
    
    /**
     * Sets up antiban testing configuration based on plugin config
     */
    private void setupAntibanTesting() {
        if (antibanInitialized) {
            return;
        }
        
        Microbot.log("Setting up antiban testing...");
        
        // Store original antiban settings
        antibanOriginalTakeMicroBreaks = Rs2AntibanSettings.takeMicroBreaks;
        antibanOriginalMicroBreakChance = Rs2AntibanSettings.microBreakChance;
        antibanOriginalMicroBreakDurationLow = Rs2AntibanSettings.microBreakDurationLow;
        antibanOriginalMicroBreakDurationHigh = Rs2AntibanSettings.microBreakDurationHigh;
        antibanOriginalActionCooldownActive = Rs2AntibanSettings.actionCooldownActive;
        antibanOriginalMoveMouseOffScreen = Rs2AntibanSettings.moveMouseOffScreen;
        
        // Apply test configuration
        if (config.enableMicroBreaks()) {
            Rs2AntibanSettings.takeMicroBreaks = true;
            Rs2AntibanSettings.microBreakChance = config.microBreakChancePercent() / 100.0;
            Rs2AntibanSettings.microBreakDurationLow = config.microBreakDurationMin();
            Rs2AntibanSettings.microBreakDurationHigh = config.microBreakDurationMax();
            
            Microbot.log("Micro breaks enabled - Chance: " + (config.microBreakChancePercent()) + 
                        "%, Duration: " + config.microBreakDurationMin() + "-" + config.microBreakDurationMax() + " minutes");
        }
        
        if (config.enableActionCooldowns()) {
            Rs2AntibanSettings.actionCooldownActive = true;
            Microbot.log("Action cooldowns enabled");
        }
        
        if (config.moveMouseOffScreen()) {
            Rs2AntibanSettings.moveMouseOffScreen = true;
            Microbot.log("Mouse off-screen movement enabled");
        }
        
        // Set antiban activity
        Rs2Antiban.setActivity(Activity.GENERAL_WOODCUTTING);
        Rs2Antiban.setActivityIntensity(ActivityIntensity.MODERATE);
        
        antibanInitialized = true;
        Microbot.log("Antiban testing setup complete");
    }
    
    /**
     * Restores original antiban settings
     */
    private void teardownAntibanTesting() {
        if (!antibanInitialized) {
            return;
        }
        
        Microbot.log("Restoring original antiban settings...");
        
        // Restore original settings
        Rs2AntibanSettings.takeMicroBreaks = antibanOriginalTakeMicroBreaks;
        Rs2AntibanSettings.microBreakChance = antibanOriginalMicroBreakChance;
        Rs2AntibanSettings.microBreakDurationLow = antibanOriginalMicroBreakDurationLow;
        Rs2AntibanSettings.microBreakDurationHigh = antibanOriginalMicroBreakDurationHigh;
        Rs2AntibanSettings.actionCooldownActive = antibanOriginalActionCooldownActive;
        Rs2AntibanSettings.moveMouseOffScreen = antibanOriginalMoveMouseOffScreen;
        
        // Reset antiban activity
        Rs2Antiban.resetAntibanSettings();
        
        antibanInitialized = false;
        Microbot.log("Antiban settings restored");
    }
    
    /**
     * Handles break status monitoring and state transitions
     */
    private void handleBreakStatusChecks() {
        long currentTime = System.currentTimeMillis();
        
        // Check break status every second
        if (currentTime - lastBreakStatusCheck < 1000) {
            return;
        }
        lastBreakStatusCheck = currentTime;
        
        boolean isCurrentlyOnBreak = BreakHandlerScript.isBreakActive() || 
                                   Rs2AntibanSettings.microBreakActive || 
                                   Rs2AntibanSettings.actionCooldownActive;
        
        // Detect break start
        if (isCurrentlyOnBreak && !wasOnBreak) {
            handleBreakStart();
        }
        // Detect break end
        else if (!isCurrentlyOnBreak && wasOnBreak) {
            handleBreakEnd();
        }
        
        // Report status periodically if on break
        if (isCurrentlyOnBreak && config.statusReportInterval() > 0) {
            if (currentTime - lastStatusReport >= config.statusReportInterval() * 1000) {
                reportBreakStatus();
                lastStatusReport = currentTime;
            }
        }
        
        wasOnBreak = isCurrentlyOnBreak;
    }
    
    /**
     * Handles the start of a break
     */
    private void handleBreakStart() {
        breakStartTime = System.currentTimeMillis();
        state = State.BREAK_PAUSED;
        
        String breakType = getBreakType();
        Microbot.log("Break started - Type: " + breakType + ", Script state: PAUSED");
        
        if (Rs2AntibanSettings.microBreakActive) {
            microBreakCount++;
        }
    }
    
    /**
     * Handles the end of a break
     */
    private void handleBreakEnd() {
        if (breakStartTime > 0) {
            long breakDuration = System.currentTimeMillis() - breakStartTime;
            totalBreakTime += breakDuration;
            
            String breakType = getBreakType();
            Microbot.log("Break ended - Type: " + breakType + 
                        ", Duration: " + formatDuration(breakDuration) + 
                        ", Script state: RESUMED");
            
            breakStartTime = 0;
        }
        
        // Resume normal operation
        if (state == State.BREAK_PAUSED) {
            state = State.IDELE;
        }
    }
    
    /**
     * Reports current break status
     */
    private void reportBreakStatus() {
        String breakType = getBreakType();
        long currentBreakDuration = breakStartTime > 0 ? 
            System.currentTimeMillis() - breakStartTime : 0;
        
        Microbot.log("Break Status - Type: " + breakType + 
                    ", Current Duration: " + formatDuration(currentBreakDuration) + 
                    ", Total Break Time: " + formatDuration(totalBreakTime) + 
                    ", Micro Breaks: " + microBreakCount);
    }
    
    /**
     * Determines the current break type
     */
    private String getBreakType() {
        if (BreakHandlerScript.isBreakActive()) {
            return "Regular Break";
        } else if (Rs2AntibanSettings.microBreakActive) {
            return "Micro Break";
        } else if (Rs2AntibanSettings.actionCooldownActive) {
            return "Action Cooldown";
        } else {
            return "Unknown";
        }
    }
    
    /**
     * Handles antiban behaviors during normal operation
     */
    private void handleAntibanBehaviors() {
        // Trigger action cooldown occasionally
        if (config.enableActionCooldowns() && Math.random() < 0.01) { // 1% chance per tick
            Rs2Antiban.actionCooldown();
        }
        
        // Take micro breaks by chance
        if (config.enableMicroBreaks() && Math.random() < (config.microBreakChancePercent() / 100.0 )) {
            Rs2Antiban.takeMicroBreakByChance();
            if (Rs2AntibanSettings.microBreakActive) {                
                Microbot.log("Taking a new micro break - Count: " + microBreakCount);
            }
        }
        
        // Move mouse randomly
        if (config.moveMouseOffScreen() && Math.random() < 0.005) { // 0.5% chance per tick
            Rs2Antiban.moveMouseRandomly();
        }
    }
    
    /**
     * Formats a duration in milliseconds to a readable string
     */
    private String formatDuration(long durationMillis) {
        long seconds = durationMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Gets comprehensive antiban testing statistics
     */
    public String getAntibanStats() {
        if (!config.enableAntibanTesting()) {
            return "Antiban testing disabled";
        }
        
        return String.format("Antiban Stats - Total Break Time: %s, Micro Breaks: %d, " +
                           "Current State: %s, Break Active: %s",
                           formatDuration(totalBreakTime),
                           microBreakCount,
                           state.name(),
                           wasOnBreak ? getBreakType() : "None");
    }
}