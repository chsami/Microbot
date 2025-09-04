package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;


import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
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
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPostScheduleTaskEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPreScheduleTaskEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.AbstractPrePostScheduleTasks;
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
    
    @Inject
    private SchedulableExampleOverlay overlay;
   
    @Provides
    SchedulableExampleConfig provideConfig(ConfigManager configManager) {
        if (configManager == null) {
            log.warn("ConfigManager is null, cannot provide SchedulableExampleConfig");
            return null;
        }
        return configManager.getConfig(SchedulableExampleConfig.class);
    }
    
    /**
     * Gets the plugin configuration.
     * 
     * @return The SchedulableExampleConfig instance
     */
    public SchedulableExampleConfig getConfig() {
        return config;
    }
    
    private SchedulableExampleScript script;
    private WorldPoint lastLocation = null;
    private int itemsCollected = 0;
    
    private LockCondition lockCondition;
    private LogicalCondition startCondition = null;
    private LogicalCondition stopCondition = null;
    
    
    // Pre/Post Schedule Tasks and Requirements
    private SchedulableExamplePrePostScheduleRequirements prePostScheduleRequirements = null;
    private SchedulableExamplePrePostScheduleTasks prePostScheduleTasks = null;
    
    // HotkeyListener for the area marking
    private final HotkeyListener areaHotkeyListener = new HotkeyListener(() -> config.areaMarkHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleCustomArea();
        }
    };
    
    // HotkeyListener for testing PluginScheduleEntryMainTaskFinishedEvent
    private final HotkeyListener finishPluginSuccessHotkeyListener = new HotkeyListener(() -> config.finishPluginSuccessfulHotkey()) {
        @Override
        public void hotkeyPressed() {
            String reason = config.finishReason() + " (success)";
            boolean success = true;
            log.info("\nManually triggering plugin finish: reason='{}', success={}", reason, success);
            Microbot.getClientThread().invokeLater( () ->  {reportFinished(reason, success); return true;});
        }
    };
     // HotkeyListener for testing PluginScheduleEntryMainTaskFinishedEvent
    private final HotkeyListener finishPluginNotSuccessHotkeyListener = new HotkeyListener(() -> config.finishPluginNotSuccessfulHotkey()) {
        @Override
        public void hotkeyPressed() {
            String reason = config.finishReason()+ " (not success)";
            boolean success = false;
            log.info("\nManually triggering plugin finish: reason='{}', success={}", reason, success);
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
            log.info("\n\tLock condition toggled: {}", newState ? "LOCKED - " + config.lockDescription() : "UNLOCKED");
        }
    };
    
    // HotkeyListener for testing pre-schedule tasks
    private final HotkeyListener testPreScheduleTasksHotkeyListener = new HotkeyListener(() -> config.testPreScheduleTasksHotkey()) {
        @Override
        public void hotkeyPressed() {
            // Initialize Pre/Post Schedule Requirements and Tasks if needed
            if (config.enablePrePostRequirements()) {
                if (getPrePostScheduleTasks() == null) {
                    log.info("Initializing Pre/Post failed ");
                    return;
                }
                // Test only pre-schedule tasks
                SchedulableExamplePlugin.this.runPreScheduleTasks();


            } else {
                log.info("Pre/Post Schedule Requirements are disabled in configuration");
            }
        }
    };
    
    // HotkeyListener for testing post-schedule tasks
    private final HotkeyListener testPostScheduleTasksHotkeyListener = new HotkeyListener(() -> config.testPostScheduleTasksHotkey()) {
        @Override
        public void hotkeyPressed() {
            // Initialize Pre/Post Schedule Requirements and Tasks if needed
            if (config.enablePrePostRequirements()) {
                if (getPrePostScheduleTasks() == null) {
                    log.info("Initializing Pre/Post failed ");
                    return;
                }
                
                // Test only post-schedule tasks
                runPostScheduleTasks();
            } else {
                log.info("Pre/Post Schedule Requirements are disabled in configuration");
            }
        }
    };
    
    // HotkeyListener for cancelling tasks
    private final HotkeyListener cancelTasksHotkeyListener = new HotkeyListener(() -> config.cancelTasksHotkey()) {
        @Override
        public void hotkeyPressed() {
            log.info("Cancel tasks hotkey pressed for plugin: {}", getName());
            
            if (prePostScheduleTasks != null) {
                if (prePostScheduleTasks.isPreScheduleRunning()) {
                    prePostScheduleTasks.cancelPreScheduleTasks();
                    log.info("Cancelled pre-schedule tasks");
                } else if (prePostScheduleTasks.isPostScheduleRunning()) {
                    prePostScheduleTasks.cancelPostScheduleTasks();
                    log.info("Cancelled post-schedule tasks");
                } else {
                    log.info("No pre/post schedule tasks are currently running");
                }
                
                // Reset the execution state to allow fresh start
                prePostScheduleTasks.reset();
                log.info("Reset pre/post schedule tasks execution state");
            } else {
                log.info("No pre/post schedule tasks manager initialized");
            }
        }
    };
    
    @Override
    protected void startUp() {
        loadLastLocation();
        this.script = new SchedulableExampleScript();
        
                
        
        keyManager.registerKeyListener(this);
        
        // Register the hotkey listeners
        keyManager.registerKeyListener(areaHotkeyListener);
        keyManager.registerKeyListener(finishPluginSuccessHotkeyListener);
        keyManager.registerKeyListener(finishPluginNotSuccessHotkeyListener);
        keyManager.registerKeyListener(lockConditionHotkeyListener);
        keyManager.registerKeyListener(testPreScheduleTasksHotkeyListener);
        keyManager.registerKeyListener(testPostScheduleTasksHotkeyListener);
        keyManager.registerKeyListener(cancelTasksHotkeyListener);
        
        // Add the overlay
        overlayManager.add(overlay);
        boolean scheduleMode = Microbot.getConfigManager().getConfiguration(
                                    "SchedulableExample", 
                                    "scheduleMode", 
                                Boolean.class
                                );        
        log.info("\n\tSchedulable Example plugin started\n\t -In SchedulerMode:{}\n\t -Press {} to test the PluginScheduleEntryMainTaskFinishedEvent successfully\n\t -Press {} to test the PluginScheduleEntryMainTaskFinishedEvent unsuccessfully\n\t -Use {} to toggle the lock condition (prevents the plugin from being stopped)\n\t -Use {} to test Pre-Schedule Tasks functionality\n\t -Use {} to test Post-Schedule Tasks functionality\n\t -Use {} to cancel running pre/post schedule tasks",
                scheduleMode,
                config.finishPluginSuccessfulHotkey(),
                config.finishPluginNotSuccessfulHotkey(),
                config.lockConditionHotkey(),
                config.testPreScheduleTasksHotkey(),
                config.testPostScheduleTasksHotkey(),
                config.cancelTasksHotkey());
      
    }
    
    /**
     * Override the default event handler to start the script properly after pre-schedule tasks.
     * This follows the same pattern as runPreScheduleTasks() but integrates with the scheduler.
     */
    @Subscribe
    public void onPluginScheduleEntryPreScheduleTaskEvent(PluginScheduleEntryPreScheduleTaskEvent event) {

        if (event.getPlugin() != this) {
            return; // Not for this plugin
        }
        
        log.info("Received PluginScheduleEntryPreScheduleTaskEvent for SchedulableExample plugin");
        
        if (prePostScheduleTasks != null && event.isSchedulerControlled() && !prePostScheduleTasks.isPreTaskComplete()) {
            // Plugin has pre/post tasks and is under scheduler control
            log.info("SchedulableExample starting with pre-schedule tasks from scheduler");                                
            try {
                // Execute pre-schedule tasks with callback to start the script
                runPreScheduleTasks();
            } catch (Exception e) {
                log.error("Error during Pre-Schedule Tasks for SchedulableExample", e);                
            }
        }
    }
   
    
    @Override
    protected void shutDown() {      
        // Clean up PrePostScheduleTasks if initialized
        if (prePostScheduleTasks != null) {
            try {
                prePostScheduleTasks.close();
                log.info("PrePostScheduleTasks cleaned up successfully");
            } catch (Exception e) {
                log.error("Error cleaning up PrePostScheduleTasks", e);
            } finally {
                prePostScheduleTasks = null;
                prePostScheduleRequirements = null;
            }
        }

        if (script != null && script.isRunning()) {
            saveCurrentLocation();
            script.shutdown();            
        }
        unlock((Condition)(stopCondition));
        keyManager.unregisterKeyListener(this);
        keyManager.unregisterKeyListener(areaHotkeyListener);
        keyManager.unregisterKeyListener(finishPluginSuccessHotkeyListener);
        keyManager.unregisterKeyListener(finishPluginNotSuccessHotkeyListener);
        keyManager.unregisterKeyListener(lockConditionHotkeyListener);
        keyManager.unregisterKeyListener(testPreScheduleTasksHotkeyListener);
        keyManager.unregisterKeyListener(testPostScheduleTasksHotkeyListener);
        keyManager.unregisterKeyListener(cancelTasksHotkeyListener);
        
        // Remove the overlay
        overlayManager.remove(overlay);
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
             this.lockCondition = new LockCondition("Locked because the Plugin "+getName()+" is in a critical operation", false,true); //ensure unlock on shutdown of the plugin !
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
  
    
   
    /**
     * Tests only the pre-schedule tasks functionality.
     * This method demonstrates how pre-schedule tasks work and logs the results.
     */
    private void runPreScheduleTasks() {
         if (prePostScheduleTasks != null && !prePostScheduleTasks.isPreTaskRunning() && !prePostScheduleTasks.isPreTaskComplete()) {
            executePreScheduleTasks(() -> {
                            log.info("Pre-Schedule Tasks completed successfully for SchedulableExample");
                            // Ensure script is initialized
                            if (this.script == null) {
                                this.script = new SchedulableExampleScript();
                            }
                            if (this.script.isRunning()) {
                                this.script.shutdown();
                            }
                            // Start the actual script after pre-schedule tasks are done
                            this.script.run(config, lastLocation);
                        });
        }

    }
    private void runPostScheduleTasks( ){
        if (prePostScheduleTasks != null && !prePostScheduleTasks.isPostScheduleRunning() && !prePostScheduleTasks.isPostTaskComplete()) {        
            executePostScheduleTasks(()->{
                    if( this.script != null && this.script.isRunning()) {
                        this.script.shutdown();
                    }              
            });
        }else {
            log.info("Post-Schedule Tasks already completed or running for SchedulableExample");
        }


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
    @Override
    public LogicalCondition getStopCondition() {
        // Create a new stop condition
        if (this.stopCondition == null) {
            this.stopCondition = createStopCondition();
        }
        return this.stopCondition;
    }
    @Override
    public AbstractPrePostScheduleTasks getPrePostScheduleTasks() {
        SchedulableExampleConfig config = provideConfig(Microbot.getConfigManager());
        if (prePostScheduleRequirements == null || prePostScheduleTasks == null) {
            if(Microbot.getClient().getGameState() != GameState.LOGGED_IN) {
               log.debug("Schedulable Example - Cannot provide pre/post schedule tasks - not logged in");
                return null; // Return null if not logged in
            }
            log.info("Initializing Pre/Post Schedule Requirements and Tasks...");
            this.prePostScheduleRequirements = new SchedulableExamplePrePostScheduleRequirements(config);
            this.prePostScheduleTasks = new SchedulableExamplePrePostScheduleTasks(this, keyManager,prePostScheduleRequirements);            
            // Log the requirements status
            if (prePostScheduleRequirements.isInitialized()) log.info("\nPrePostScheduleRequirements initialized:\n{}", prePostScheduleRequirements.getDetailedDisplay());
        }        
        // Return the pre/post schedule tasks instance       
        return this.prePostScheduleTasks;
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
        
        for (int i = 0; i < lootItemsList.size(); i++) {
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
        
        for (int i = 0; i < resourcesList.size(); i++) {
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
            
            for (int i = 0; i < npcNamesList.size(); i++) {
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
		final ConfigDescriptor desc = getConfigDescriptor();
		if (desc != null && desc.getGroup() != null && event.getGroup().equals(desc.getGroup().value())) {
			
            this.startCondition = null;
            this.stopCondition = null;        
            log.info(
				"Config change detected for {}: {}={}, config group {}",
				getName(),
				event.getGroup(),
				event.getKey(),
				desc.getGroup().value()
			);
            if (config.enablePrePostRequirements()) {
                if (prePostScheduleTasks != null && !prePostScheduleTasks.isExecuting()) {                    
                    if (prePostScheduleRequirements != null) {
                        prePostScheduleRequirements.setConfig(config);    
                        prePostScheduleRequirements.reset();
                    }
                    // prePostScheduleTasks.reset(); when we allow reexecution of pre/post-schedule tasks on config change
                    log.info("PrePostScheduleRequirements initialized:\n{}", prePostScheduleRequirements.getDetailedDisplay());
                }
            } else {
                log.info("Pre/Post Schedule Requirements are disabled in configuration");
            }
		}
	}

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {           
            log.info("GameState changed to LOGGED_IN"); 
            getPrePostScheduleTasks();           
            if( prePostScheduleTasks != null 
                && prePostScheduleTasks.isScheduleMode() && 
                !prePostScheduleTasks.isPreTaskComplete() && 
                !prePostScheduleTasks.isPreScheduleRunning()) {
                log.info("Plugin is running in Scheduler Mode - waiting for scheduler to start pre-schedule tasks");
            } else {
                log.info("Plugin is running in normal mode");
            }
        }else if (event.getGameState() == GameState.LOGIN_SCREEN) {
            
        }
    }
    @Override
    @Subscribe
    public void onPluginScheduleEntryPostScheduleTaskEvent(PluginScheduleEntryPostScheduleTaskEvent event) {
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
            log.info("Scheduling stop for plugin: {}", event.getPlugin().getClass().getSimpleName());

            runPostScheduleTasks(); 
            /*try {                    
                Microbot.log("Successfully exited SchedulerExamplePlugin - stopping plugin");
                Microbot.getClientThread().invokeLater(() -> {
                    Microbot.stopPlugin(this);
                    return true;
                });
            } catch (Exception ex) {
                Microbot.log("Error during safe exit: " + ex.getMessage());
                Microbot.getClientThread().invokeLater(() -> {
                    Microbot.stopPlugin(this);
                    return true;
                });
            }*/
                
            
        
            
            // Schedule the stop operation on the client thread
            //Microbot.getClientThread().invokeLater(() -> {
            //    try {                    
            //        Microbot.getPluginManager().setPluginEnabled(this, false);
            //        Microbot.getPluginManager().stopPlugin(this);
            //    } catch (Exception e) {
             //       log.error("Error stopping plugin", e);
              //  }
           // });
        }
    }
    





    @Override
    public void keyTyped(KeyEvent e) {
        // Not used
    }

    @Override
    public void keyPressed(KeyEvent e) {
        // Movement handling has been moved to VoxQoL plugin
        // This plugin now only handles its core scheduling functionality
    }

    
   
    







    @Override
    public void keyReleased(KeyEvent e) {
        // Not used but required by the KeyListener interface
    }


}