package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.AreaCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.LocationCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.npc.NpcKillCountCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.GatheredResourceCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.LootItemCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.ProcessItemCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

@PluginDescriptor(
        name = "Schedulable Example",
        description = "Designed for use with the scheduler and testing its features",
        tags = {"microbot", "woodcutting", "combat", "scheduler", "condition"},
        enabledByDefault = false
)
@Slf4j
public class SchedulableExamplePlugin extends Plugin implements SchedulablePlugin, KeyListener {
    
    @Inject
    private SchedulableExampleConfig config;
    
    @Inject
    private Client client;
    
    @Inject
    private KeyManager keyManager;
    
    @Inject
    private OverlayManager overlayManager;
   
    @Provides
    SchedulableExampleConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SchedulableExampleConfig.class);
    }
    
    private SchedulableExampleScript script;
    private WorldPoint lastLocation = null;
    private int itemsCollected = 0;
    private int npcKilled = 0;
    private LocationStartNotificationOverlay locationOverlay;    
    private LockCondition lockCondition;
    private LogicalCondition startCondition = null;
    private LogicalCondition stopCondition = null;
    
    // HotkeyListener for the area marking
    private final HotkeyListener areaHotkeyListener = new HotkeyListener(() -> config.areaMarkHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleCustomArea();
        }
    };
    
    // HotkeyListener for testing PluginScheduleEntryFinishedEvent
    private final HotkeyListener finishPluginSuccessHotkeyListener = new HotkeyListener(() -> config.finishPluginSuccessfulHotkey()) {
        @Override
        public void hotkeyPressed() {
            String reason = config.finishReason() + " (success)";
            boolean success = true;
            log.info("Manually triggering plugin finish: reason='{}', success={}", reason, success);
            Microbot.getClientThread().invokeLater( () ->  {reportFinished(reason, success); return true;});
        }
    };
     // HotkeyListener for testing PluginScheduleEntryFinishedEvent
    private final HotkeyListener finishPluginNotSuccessHotkeyListener = new HotkeyListener(() -> config.finishPluginNotSuccessfulHotkey()) {
        @Override
        public void hotkeyPressed() {
            String reason = config.finishReason()+ " (not success)";
            boolean success = false;
            log.info("Manually triggering plugin finish: reason='{}', success={}", reason, success);
            Microbot.getClientThread().invokeLater( () ->  {reportFinished(reason, success); return true;});
        }
    };
    
    // HotkeyListener for toggling the lock condition
    private final HotkeyListener lockConditionHotkeyListener = new HotkeyListener(() -> config.lockConditionHotkey()) {
        @Override
        public void hotkeyPressed() {
            log.info("Toggling lock condition for plugin: {}", getName());
            if (stopCondition == null || stopCondition.getConditions().isEmpty()) {
                log.warn("Stop condition is not initialized. Cannot toggle lock condition.");
                return;
            }
            boolean newState = toggleLock((Condition)(stopCondition));
            log.info("Lock condition toggled: {}", newState ? "LOCKED - " + config.lockDescription() : "UNLOCKED");
        }
    };
    
    @Override
    protected void startUp() {
        loadLastLocation();
        script = new SchedulableExampleScript();
        script.run(config, lastLocation);
        
        keyManager.registerKeyListener(this);
        
        // Register the hotkey listeners
        keyManager.registerKeyListener(areaHotkeyListener);
        keyManager.registerKeyListener(finishPluginSuccessHotkeyListener);
        keyManager.registerKeyListener(finishPluginNotSuccessHotkeyListener);
        keyManager.registerKeyListener(lockConditionHotkeyListener);
        
        // Create and add the overlay
        locationOverlay = new LocationStartNotificationOverlay(this, config);
        overlayManager.add(locationOverlay);
        
        log.info("Schedulable Example plugin started - Press {} to test the PluginScheduleEntryFinishedEvent successfully\nand\n{} to test the PluginScheduleEntryFinishedEvent unsuccessfully",
                config.finishPluginSuccessfulHotkey(),
                config.finishPluginNotSuccessfulHotkey());                 
        log.info("Use {} to toggle the lock condition (prevents the plugin from being stopped)", 
                config.lockConditionHotkey());
            
    }
    
    @Override
    protected void shutDown() {
        if (script != null) {
            saveCurrentLocation();
            script.shutdown();            
        }
        unlock((Condition)(stopCondition));
        keyManager.unregisterKeyListener(this);
        keyManager.unregisterKeyListener(areaHotkeyListener);
        keyManager.unregisterKeyListener(finishPluginSuccessHotkeyListener);
        keyManager.unregisterKeyListener(finishPluginNotSuccessHotkeyListener);
        keyManager.unregisterKeyListener(lockConditionHotkeyListener);
        
        // Remove the overlay
        overlayManager.remove(locationOverlay);
    }
    
    /**
     * Toggles the custom area state and updates configuration
     */
    private void toggleCustomArea() {
        if (!Microbot.isLoggedIn()) {
            log.info("Cannot toggle custom area: Not logged in");
            return;
        }
        
        boolean isActive = config.customAreaActive();
        
        if (isActive) {
            // Clear the custom area
            config.setCustomAreaActive(false);
            config.setCustomAreaCenter(null);
            log.info("Custom area removed");
        } else {
            // Create new custom area at current position
            WorldPoint currentPos = null;
            if (Microbot.isLoggedIn()){
                currentPos = Rs2Player.getWorldLocation();
            }
            if (currentPos != null) {
                config.setCustomAreaCenter(currentPos);
                config.setCustomAreaActive(true);
                log.info("Custom area created at: " + currentPos.toString() + " with radius: " + config.customAreaRadius());
            }
        }
    }
    
    /**
     * Checks if the player is in the custom area
     */
    public boolean isPlayerInCustomArea() {
        if (!config.customAreaActive() || config.customAreaCenter() == null) {
            return false;
        }
        if (!Microbot.isLoggedIn()) {
            return false;
        }
        WorldPoint currentPos = Rs2Player.getWorldLocation();
        if (currentPos == null) {
            return false;
        }
        
        WorldPoint center = config.customAreaCenter();
        int radius = config.customAreaRadius();
        
        // Check if player is within radius of the center point and on the same plane
        return (currentPos.getPlane() == center.getPlane() && 
                currentPos.distanceTo(center) <= radius);
    }
    
    private void loadLastLocation() {
        WorldPoint savedLocation = config.lastLocation();
        if (savedLocation == null) {
            log.warn("No saved location found in config.");
            if (Microbot.isLoggedIn()){
                this.lastLocation = Rs2Player.getWorldLocation();
            }
            return;
        }
        this.lastLocation = savedLocation;
    }
    
    private void saveCurrentLocation() {        
        if (client.getLocalPlayer() != null) {
            WorldPoint currentLoc = client.getLocalPlayer().getWorldLocation();
            config.setLastLocation(currentLoc);
        }
    }
    
    private LogicalCondition createStopCondition() {
         // Create an OR condition - we'll stop when ANY of the enabled conditions are met
         OrCondition orCondition = new OrCondition();
         if (this.lockCondition == null) {
             this.lockCondition = new LockCondition("Locked because the Plugin "+getName()+" is in a critical operation");
         }
         
         // Add enabled conditions based on configuration
         if (config.enableTimeCondition()) {
             orCondition.addCondition(createTimeCondition());
         }
         
         if (config.enableLootItemCondition()) {
             orCondition.addCondition(createLootItemCondition());
         }
         
         if (config.enableGatheredResourceCondition()) {
             orCondition.addCondition(createGatheredResourceCondition());
         }
         
         if (config.enableProcessItemCondition()) {
             orCondition.addCondition(createProcessItemCondition());
         }
         
         if (config.enableNpcKillCountCondition()) {
             orCondition.addCondition(createNpcKillCountCondition());
         }
         
         // If no conditions were added, add a fallback time condition
         if (orCondition.getConditions().isEmpty()) {
             log.warn("No stop conditions were enabled. Adding default time condition of 5 minutes.");
             orCondition.addCondition(IntervalCondition.createRandomized(Duration.ofMinutes(5), Duration.ofMinutes(5)));
         }
         
         // Add a lock condition that can be toggled manually
         // NOTE: This condition uses AND logic with the other conditions since it's in an AND condition
         AndCondition andCondition = new AndCondition();
         //andCondition.addCondition(orCondition);
         andCondition.addCondition(lockCondition);         
         
         List<LockCondition> all = andCondition.findAllLockConditions();
         log.info("\nCreated stop condition: \n{}"+"\nFound {} lock conditions in stop condition: {}", andCondition.getDescription(), all.size(), all);
    
         return andCondition;
       
    }
    @Override
    public LogicalCondition getStopCondition() {
        // Create a new stop condition
        if (this.stopCondition == null) {
            this.stopCondition = createStopCondition();
        }
        return this.stopCondition;
    }
    private LogicalCondition createStartCondition() {
        try {
            // Default to no start conditions (always allowed to start)
            if (!config.enableLocationStartCondition()) {
                return null;
            }
            
            // Create a logical condition for start conditions
            LogicalCondition startCondition = null;
            
            // Create location-based condition based on selected type
            if (config.locationStartType() == SchedulableExampleConfig.LocationStartType.BANK) {
                // Bank-based start condition
                BankLocation selectedBank = config.bankStartLocation();
                int distance = config.bankDistance();
                
                // Create condition using bank location
                startCondition = new OrCondition(); // Use OR to allow multiple possible conditions
                Condition bankCondition = LocationCondition.atBank(selectedBank, distance);
                ((OrCondition) startCondition).addCondition(bankCondition);
                
                log.debug("Created bank start condition: " + selectedBank.name() + " within " + distance + " tiles");
            } else if (config.locationStartType() == SchedulableExampleConfig.LocationStartType.CUSTOM_AREA) {
                // Custom area start condition
                if (config.customAreaActive() && config.customAreaCenter() != null) {
                    WorldPoint center = config.customAreaCenter();
                    int radius = config.customAreaRadius();
                    
                    // Create area condition centered on the saved point
                    startCondition = new OrCondition();
                    AreaCondition areaCondition = LocationCondition.createArea(
                        "Custom Start Area", 
                        center, 
                        radius * 2, // Width (diameter)
                        radius * 2  // Height (diameter)
                    );
                    ((OrCondition) startCondition).addCondition(areaCondition);
                    
                    log.debug("Created custom area start condition at: " + center + " with radius: " + radius);
                } else {
                    log.warn("Custom area start condition selected but no area is defined");
                    // Return null to indicate no start condition (always allowed to start)
                    return null;
                }
            }
            
            return startCondition;
        } catch (Exception e) {
            log.error("Error creating start condition", e);
            e.printStackTrace();            
            return new OrCondition(); // Fallback to no conditions
        }
    }
    /**
     * Returns a logical condition that determines when the plugin is allowed to start
     */
    @Override
    public LogicalCondition getStartCondition() {
       if (this.startCondition == null) {
            this.startCondition = createStartCondition();
        }
        return this.startCondition;
        
    }
    
    /**
     * Creates a time-based condition based on config settings
     */
    private Condition createTimeCondition() {
        // Existing implementation
        int minMinutes = config.minRuntime();
        int maxMinutes = config.maxRuntime();
                
        return IntervalCondition.createRandomized(
            Duration.ofMinutes(minMinutes),
            Duration.ofMinutes(maxMinutes)
        );
       
    }
    
    /**
     * Creates a loot item condition based on config settings
     */
    private LogicalCondition createLootItemCondition() {
        // Parse the comma-separated list of items
        List<String> lootItemsList = parseItemList(config.lootItems());
        if (lootItemsList.isEmpty()) {
            log.warn("No valid loot items specified, defaulting to 'Logs'");
            lootItemsList.add("Logs");
        }
        
        boolean andLogical = config.itemsToLootLogical();
        int minLootItems = config.minItems();
        int maxLootItems = config.maxItems();
        
        // Create randomized targets for each item
        List<Integer> minLootItemPerPattern = new ArrayList<>();
        List<Integer> maxLootItemPerPattern = new ArrayList<>();
        
        for (String item : lootItemsList) {
            int minLoot = Rs2Random.between(minLootItems, maxLootItems);
            int maxLoot = Rs2Random.between(minLoot, maxLootItems);
            
            // Ensure max is not less than min
            if (maxLoot < minLoot) {
                maxLoot = maxLootItems;
            }
            
            minLootItemPerPattern.add(minLoot);
            maxLootItemPerPattern.add(maxLoot);
        }
        
        boolean includeNoted = config.includeNoted();
        boolean allowNoneOwner = config.allowNoneOwner();
        
        // Create the appropriate logical condition based on config
        if (andLogical) {
            return LootItemCondition.createAndCondition(
                lootItemsList,
                minLootItemPerPattern,
                maxLootItemPerPattern,
                includeNoted,
                allowNoneOwner
            );
        } else {
            return LootItemCondition.createOrCondition(
                lootItemsList,
                minLootItemPerPattern,
                maxLootItemPerPattern,
                includeNoted,
                allowNoneOwner
            );
        }
    }
    
    /**
     * Creates a gathered resource condition based on config settings
     */
    private LogicalCondition createGatheredResourceCondition() {
        // Parse the comma-separated list of resources
        List<String> resourcesList = parseItemList(config.gatheredResources());
        if (resourcesList.isEmpty()) {
            log.warn("No valid resources specified, defaulting to 'logs'");
            resourcesList.add("logs");
        }
        
        boolean andLogical = config.resourcesLogical();
        int minResources = config.minResources();
        int maxResources = config.maxResources();
        boolean includeNoted = config.includeResourceNoted();
        
        // Create target lists
        List<Integer> minResourcesPerItem = new ArrayList<>();
        List<Integer> maxResourcesPerItem = new ArrayList<>();
        
        for (String resource : resourcesList) {
            int minCount = Rs2Random.between(minResources, maxResources);
            int maxCount = Rs2Random.between(minCount, maxResources);
            
            // Ensure max is not less than min
            if (maxCount < minCount) {
                maxCount = maxResources;
            }
            
            minResourcesPerItem.add(minCount);
            maxResourcesPerItem.add(maxCount);
        }
        
        // Create the appropriate logical condition
        if (andLogical) {
            return GatheredResourceCondition.createAndCondition(
                resourcesList,
                minResourcesPerItem,
                maxResourcesPerItem,
                includeNoted
            );
        } else {
            return GatheredResourceCondition.createOrCondition(
                resourcesList,
                minResourcesPerItem,
                maxResourcesPerItem,
                includeNoted
            );
        }
    }
    
    /**
     * Creates a process item condition based on config settings
     */
    private Condition createProcessItemCondition() {
        ProcessItemCondition.TrackingMode trackingMode;
        
        // Map config enum to condition enum
        switch (config.trackingMode()) {
            case SOURCE_CONSUMPTION:
                trackingMode = ProcessItemCondition.TrackingMode.SOURCE_CONSUMPTION;
                break;
            case TARGET_PRODUCTION:
                trackingMode = ProcessItemCondition.TrackingMode.TARGET_PRODUCTION;
                break;
            case EITHER:
                trackingMode = ProcessItemCondition.TrackingMode.EITHER;
                break;
            case BOTH:
                trackingMode = ProcessItemCondition.TrackingMode.BOTH;
                break;
            default:
                trackingMode = ProcessItemCondition.TrackingMode.SOURCE_CONSUMPTION;
        }
        
        List<String> sourceItemsList = parseItemList(config.sourceItems());
        List<String> targetItemsList = parseItemList(config.targetItems());
        
        int minProcessed = config.minProcessedItems();
        int maxProcessed = config.maxProcessedItems();
        
        // Create the appropriate process item condition based on tracking mode
        if (trackingMode == ProcessItemCondition.TrackingMode.SOURCE_CONSUMPTION) {
            // If tracking source consumption
            if (!sourceItemsList.isEmpty()) {
                return ProcessItemCondition.forConsumption(sourceItemsList.get(0), 
                    Rs2Random.between(minProcessed, maxProcessed));
            }
        } else if (trackingMode == ProcessItemCondition.TrackingMode.TARGET_PRODUCTION) {
            // If tracking target production
            if (!targetItemsList.isEmpty()) {
                return ProcessItemCondition.forProduction(targetItemsList.get(0), 
                    Rs2Random.between(minProcessed, maxProcessed));
            }
        } else if (trackingMode == ProcessItemCondition.TrackingMode.BOTH) {
            // If tracking both source and target
            if (!sourceItemsList.isEmpty() && !targetItemsList.isEmpty()) {
                return ProcessItemCondition.forRecipe(
                    sourceItemsList.get(0), 1,
                    targetItemsList.get(0), 1,
                    Rs2Random.between(minProcessed, maxProcessed)
                );
            }
        }
        
        // Default fallback
        log.warn("Invalid process item configuration, using default");
        return ProcessItemCondition.forConsumption("logs", 10);
    }
     /**
     * Creates an NPC kill count condition based on config settings
     */
    private LogicalCondition createNpcKillCountCondition() {
        // Parse the comma-separated list of NPC names
        List<String> npcNamesList = parseItemList(config.npcNames());
        if (npcNamesList.isEmpty()) {
            log.warn("No valid NPC names specified, defaulting to 'goblin'");
            npcNamesList.add("goblin");
        }
        
        boolean andLogical = config.npcLogical();
        int minKills = config.minKills();
        int maxKills = config.maxKills();
        boolean killsPerType = config.killsPerType();
        
        // If we're counting per NPC type, create target lists for each NPC
        if (killsPerType) {
            List<Integer> minKillsPerNpc = new ArrayList<>();
            List<Integer> maxKillsPerNpc = new ArrayList<>();
            
            for (String npc : npcNamesList) {
                int minKillCount = Rs2Random.between(minKills, maxKills);
                int maxKillCount = Rs2Random.between(minKillCount, maxKills);
                
                // Ensure max is not less than min
                if (maxKillCount < minKillCount) {
                    maxKillCount = maxKills;
                }
                
                minKillsPerNpc.add(minKillCount);
                maxKillsPerNpc.add(maxKillCount);
            }
            
            // Create the appropriate logical condition based on config
            if (andLogical) {
                return NpcKillCountCondition.createAndCondition(
                    npcNamesList,
                    minKillsPerNpc,
                    maxKillsPerNpc
                );
            } else {
                return NpcKillCountCondition.createOrCondition(
                    npcNamesList,
                    minKillsPerNpc,
                    maxKillsPerNpc
                );
            }
        } 
        // If we're counting total kills across all NPC types
        else {
            // Generate a single randomized kill count target
            int targetMin = minKills;
            int targetMax = maxKills;
            
            // Create multiple individual conditions with same ranges
            if (andLogical) {
                return NpcKillCountCondition.createAndCondition(
                    npcNamesList,
                    targetMin,
                    targetMax
                );
            } else {
                return NpcKillCountCondition.createOrCondition(
                    npcNamesList,
                    targetMin,
                    targetMax
                );
            }
        }
    }
    
    /**
     * Helper method to parse a comma-separated list of items
     */
    private List<String> parseItemList(String itemsString) {
        List<String> itemsList = new ArrayList<>();
        if (itemsString != null && !itemsString.isEmpty()) {
            String[] itemsArray = itemsString.split(",");
            for (String item : itemsArray) {
                String trimmedItem = item.trim();
                try {
                    // Validate regex pattern
                    java.util.regex.Pattern.compile(trimmedItem);
                    itemsList.add(trimmedItem);
                    log.debug("Valid item pattern found: {}", trimmedItem);
                } catch (java.util.regex.PatternSyntaxException e) {
                    log.warn("Invalid regex pattern: '{}' - {}", trimmedItem, e.getMessage());
                }
            }
        }
        return itemsList;
    }
    @Override
    public ConfigDescriptor getConfigDescriptor() {
        if (Microbot.getConfigManager() == null) {
            return null;
        }
        SchedulableExampleConfig conf = Microbot.getConfigManager().getConfig(SchedulableExampleConfig.class);
        return Microbot.getConfigManager().getConfigDescriptor(conf);
    }
    @Override
    public void onStopConditionCheck() {
        // Update item count when condition is checked
        if (script != null) {
            itemsCollected = script.getLogsCollected();
        }
    }
    
    // Method for the scheduler to check progress
    public int getItemsCollected() {
        return itemsCollected;
    }
    @Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("SchedulableExample"))
		{
			this.startCondition = null;
            this.stopCondition = null;
		}
	}
    @Override
    @Subscribe
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        // Save location before stopping
        if (event.getPlugin() == this) {
            WorldPoint currentLocation = null;
            if (Microbot.isLoggedIn()) {
                currentLocation = Rs2Player.getWorldLocation();
            }
            if ( Microbot.getConfigManager() == null) {
                log.warn("Cannot save last location - ConfigManager or current location is null");
                return;
            }
            Microbot.getConfigManager().setConfiguration("SchedulableExample", "lastLocation", currentLocation);
            log.info("onPluginScheduleEntrySoftStopEvent - Scheduling stop for plugin: {}", event.getPlugin().getClass().getSimpleName());
            Microbot.stopPlugin(this);
            // Schedule the stop operation on the client thread
            // Microbot.getClientThread().invokeLater(() -> {
            //     try {                    
            //         Microbot.getPluginManager().setPluginEnabled(this, false);
            //         Microbot.getPluginManager().stopPlugin(this);
            //     } catch (Exception e) {
            //         log.error("Error stopping plugin", e);
            //     }
            // });
        }
    }
    
    @Override
    public void keyTyped(KeyEvent e) {
        // Not used
    }

    @Override
    public void keyPressed(KeyEvent e) {
        // Can add additional key handlers here if needed
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // Not used
    }
}