package net.runelite.client.plugins.microbot.runecrafting.gotr;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPostScheduleTaskEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPreScheduleTaskEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.AbstractPrePostScheduleTasks;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.pouch.PouchOverlay;
import net.runelite.client.plugins.microbot.runecrafting.gotr.requirement.GotrPrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.runecrafting.gotr.tasks.GotrPrePostScheduleTasks;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.ui.overlay.OverlayManager;
import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "GuardiansOfTheRift",
        description = "Guardians of the rift plugin",
        tags = {"runecrafting", "guardians of the rift", "gotr", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class GotrPlugin extends Plugin implements SchedulablePlugin {
    @Inject
    private GotrConfig config;

    @Provides
    GotrConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GotrConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GotrOverlay gotrOverlay;
    @Inject
    private PouchOverlay pouchOverlay;
    @Inject
    GotrScript gotrScript;

    private LockCondition lockCondition;
    private LogicalCondition stopCondition = null;
    private GotrPrePostScheduleTasks gotrActions;
    
    // Pre/Post Schedule Tasks and Requirements
    private GotrPrePostScheduleRequirements prePostScheduleRequirements = null;
    private GotrPrePostScheduleTasks prePostScheduleTasks = null;

    public GotrConfig getConfig() {
        return config;
    }
    public GotrScript getScript() {
        return gotrScript;
    }


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(pouchOverlay);
            overlayManager.add(gotrOverlay);
        }
        
        // Initialize pre/post schedule tasks        
        if (Microbot.isLoggedIn()) {
            // Check if running in schedule mode
            if (isScheduleMode()) {
                log.info("GOTR Plugin started in Scheduler Mode, wait for the event trigger to run pre-schedule tasks");
                // In scheduler mode, pre-tasks will be executed when logged in via onGameStateChanged
                                                
            } else {
                log.info("GOTR Plugin started in Normal Mode");
                // In normal mode, start the script directly                
                gotrScript.run(config);
            }
        }
    }

    protected void shutDown() {
        // Shutdown GotrActions if it exists
        if (gotrActions != null) {
            gotrActions.shutdown();
        }
        BreakHandlerScript.setLockState(false);
        if(lockCondition != null) {
            lockCondition.unlock();
        }
        AbstractPrePostScheduleTasks tasks = getPrePostScheduleTasks();
        if (tasks != null) {
            tasks.close(); // Ensure tasks are closed properly
            prePostScheduleTasks = null; // Clear tasks to allow reinitialization next time
        }        
        if(prePostScheduleRequirements != null){
            prePostScheduleRequirements.reset();
            prePostScheduleRequirements = null; // Clear requirements to allow reinitialization next time
        }
        gotrScript.shutdown();
        overlayManager.remove(gotrOverlay);
        overlayManager.remove(pouchOverlay);
    }
    private boolean isScheduleMode() {
        // Check if the plugin is running in schedule mode
        // @see net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin#getConfigDescriptor() must be implented here so the AbstractPrePostScheduleTasks can be determine the config correct and the SchedulerPlugin can set it
        //getPrePostScheduleTasks();
        //log.debug("GOTR Plugin is in \n\tScheduler Mode: {} \n isInitialized?: {}", prePostScheduleTasks != null && prePostScheduleTasks.isScheduleMode(), 
        //         prePostScheduleRequirements != null && prePostScheduleRequirements.isInitialized());

        return AbstractPrePostScheduleTasks.isScheduleMode(this,getConfigDescriptor().getGroup().value());
    }
    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOADING) {
            GotrScript.resetPlugin();
        } else if (event.getGameState() == GameState.LOGGED_IN) {
            log.info("GameState changed to LOGGED_IN - initializing GOTR tasks");            
            // Initialize Pre/Post Schedule Requirements and Tasks when game information is available
           
            
            // Only run pre-schedule tasks if in scheduler mode
            if (!isScheduleMode()) {                           
                // In normal mode, start script if not already running
                if (gotrScript != null && !gotrScript.isRunning()) {
                    log.info("Game State to  --GOTR Plugin in Normal Mode - starting script");
                    gotrScript.run(config);
                }
            }
        } else if (event.getGameState() == GameState.LOGIN_SCREEN) {
            GotrScript.isInMiniGame = false;
            
            // Reset pre/post schedule tasks on logout for fresh initialization
            log.info("GameState changed to LOGIN_SCREEN - resetting GOTR tasks");
            
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned npcSpawned) {
        NPC npc = npcSpawned.getNpc();
        if (npc.getId() == GotrScript.greatGuardianId) {
            GotrScript.greatGuardian = npc;
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        NPC npc = npcDespawned.getNpc();
        if (npc.getId() == GotrScript.greatGuardianId) {
            GotrScript.greatGuardian = null;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.SPAM && chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String msg = chatMessage.getMessage();

        if (msg.contains("You step through the portal")) {
            Microbot.getClient().clearHintArrow();
            GotrScript.nextGameStart = Optional.empty();
        }

        if (msg.contains("The rift becomes active!")) {
            if (Microbot.isPluginEnabled(BreakHandlerPlugin.class)) {
                BreakHandlerScript.setLockState(true);
            }
            GotrScript.nextGameStart = Optional.empty();
            GotrScript.timeSincePortal = Optional.of(Instant.now());
            GotrScript.isFirstPortal = true;
            GotrScript.state = GotrState.ENTER_GAME;
            if (lockCondition != null) {
                lockCondition.lock();
            }
        } else if (msg.contains("The rift will become active in 30 seconds.")) {
            if (Microbot.isPluginEnabled(BreakHandlerPlugin.class)) {
                BreakHandlerScript.setLockState(true);
            }
            if (lockCondition != null) {
                lockCondition.lock();
            }
            GotrScript.shouldMineGuardianRemains = true;
            GotrScript.nextGameStart = Optional.of(Instant.now().plusSeconds(30));
        } else if (msg.contains("The rift will become active in 10 seconds.")) {
            GotrScript.shouldMineGuardianRemains = true;
            GotrScript.nextGameStart = Optional.of(Instant.now().plusSeconds(10));
        } else if (msg.contains("The rift will become active in 5 seconds.")) {
            GotrScript.shouldMineGuardianRemains = true;
            GotrScript.nextGameStart = Optional.of(Instant.now().plusSeconds(5));
        } else if (msg.contains("The Portal Guardians will keep their rifts open for another 30 seconds.")) {
            GotrScript.shouldMineGuardianRemains = true;
            GotrScript.nextGameStart = Optional.of(Instant.now().plusSeconds(60));
            if (lockCondition != null) {
                lockCondition.unlock();
            }
        }else if (msg.toLowerCase().contains("closed the rift!") || msg.toLowerCase().contains("The great guardian was defeated!")) {
            if (Microbot.isPluginEnabled(BreakHandlerPlugin.class)) {
            Global.sleep(Rs2Random.randomGaussian(2000, 300));
            BreakHandlerScript.setLockState(false);
            }
            if (lockCondition != null) {
                lockCondition.unlock();
            }
            GotrScript.shouldMineGuardianRemains = true;

        }

        Matcher rewardPointMatcher = GotrScript.rewardPointPattern.matcher(msg);
        if (rewardPointMatcher.find()) {
            GotrScript.elementalRewardPoints = Integer.parseInt(rewardPointMatcher.group(1).replaceAll(",", ""));
            GotrScript.catalyticRewardPoints = Integer.parseInt(rewardPointMatcher.group(2).replaceAll(",", ""));
        }
    }
    @Subscribe	
	public void onConfigChanged(ConfigChanged event)
	{
	    final ConfigDescriptor desc = getConfigDescriptor();
		if (desc != null && desc.getGroup() != null && event.getGroup().equals(desc.getGroup().value())) {
			log.info(
				"Config change detected for {}: {}={}, config group {}",
				getName(),
				event.getGroup(),
				event.getKey(),
				desc.getGroup().value()
			);
			if (prePostScheduleTasks != null && !prePostScheduleTasks.isExecuting()) {
				if (prePostScheduleRequirements != null) {
					prePostScheduleRequirements.setConfig(config);
					prePostScheduleRequirements.reset();
				}
				// prePostScheduleTasks.reset(); when we allow reexecution of pre/post-schedule tasks on config change
			}
		}
	}

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();
        if (GotrScript.isGuardianPortal(gameObject)) {
            GotrScript.guardians.add(gameObject);
        }

        if (gameObject.getId() == GotrScript.portalId) {
            Microbot.getClient().setHintArrow(gameObject.getWorldLocation());
            if(GotrScript.isFirstPortal) {
                GotrScript.isFirstPortal = false;
            }
            GotrScript.timeSincePortal = Optional.of(Instant.now());
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        GameObject gameObject = event.getGameObject();

        GotrScript.guardians.remove(gameObject);
        GotrScript.activeGuardianPortals.remove(gameObject);

        if (gameObject.getId() == GotrScript.portalId) {
            Microbot.getClient().clearHintArrow();
            GotrScript.timeSincePortal = Optional.of(Instant.now());
        }
    }

    @Override
    public LogicalCondition getStopCondition() {
        if (this.stopCondition == null) {
            this.stopCondition = createStopCondition();
        }
        return this.stopCondition;
    }
    private LogicalCondition createStopCondition() {
        if (this.lockCondition == null) {
            // default locked is false, and withBreakHandlerLock is true to ensure BreakHandler lock state is managed
            this.lockCondition = new LockCondition("Locked because the Plugin " + getName() + " is in a critical operation", false,true); //ensure unlock on shutdown of the plugin !
        }

        AndCondition andCondition = new AndCondition();
        andCondition.addCondition(lockCondition);
        return andCondition;
    }

    @Override
    public AbstractPrePostScheduleTasks getPrePostScheduleTasks() {      
  
        if (prePostScheduleRequirements == null) {
			if(Microbot.getClient().getGameState() != GameState.LOGGED_IN) {
               log.debug("GOTR - Cannot provide pre/post schedule tasks - not logged in");
                return null; // Return null if not logged in
            }
			log.info("Initializing GOTR Pre/Post Schedule Requirements and Tasks...");
            this.prePostScheduleRequirements = new GotrPrePostScheduleRequirements(config);			
		}	
        if (this.prePostScheduleTasks==null ){
			this.prePostScheduleTasks = new GotrPrePostScheduleTasks(this, prePostScheduleRequirements);				
			log.info("GOTR PrePostScheduleRequirements initialized:\n{}", prePostScheduleRequirements.getDetailedDisplay());
		}               
        // Return the pre/post schedule tasks instance
        return this.prePostScheduleTasks;
    }

    /**
     * Runs pre-schedule tasks to prepare the GOTR plugin for automated execution.
     * This includes equipment setup, location verification, and inventory preparation.
     * Only executes if in scheduler mode.
     */
    public void runPreScheduleTasks() {
        log.info("Starting GOTR pre-schedule tasks...");
        
        AbstractPrePostScheduleTasks tasks = getPrePostScheduleTasks();
        if (prePostScheduleTasks != null && !prePostScheduleTasks.isPreTaskRunning() && !prePostScheduleTasks.isPreTaskComplete()) {
            if (tasks != null) {
                // Only execute pre-schedule tasks if in scheduler mode
                if (tasks.isScheduleMode() && !tasks.isPreTaskComplete()) {
                    tasks.executePreScheduleTasks(() -> {
                        log.info("GOTR pre-schedule tasks completed successfully starting the script");
                        // Start the main GOTR script after pre-tasks are complete
                        if (gotrScript != null) {
                            gotrScript.run(config);
                        }
                    });
                } else {
                    log.warn("Attempted to run pre-schedule tasks but not in scheduler mode");
                }
            } else {
                log.error("Cannot run pre-schedule tasks - task system not initialized");
            }
        }else {
            log.warn("GOTR pre-schedule tasks already completed or running");
        }

    }

    /**
     * Runs post-schedule tasks to clean up after GOTR plugin execution.
     * This includes leaving the minigame area and banking items.
     * Only executes if in scheduler mode.
     * Note: AbstractPrePostScheduleTasks handles plugin stopping via reportFinished().
     */
    public void runPostScheduleTasks() {
        if (prePostScheduleTasks != null && !prePostScheduleTasks.isPostScheduleRunning() && !prePostScheduleTasks.isPostTaskComplete()) {        
            log.info("Starting GOTR post-schedule tasks...");
            
            AbstractPrePostScheduleTasks tasks = getPrePostScheduleTasks();
            if (tasks != null) {
                // Only execute post-schedule tasks if in scheduler mode
                if (tasks.isScheduleMode()) {
                    tasks.executePostScheduleTasks(() -> {
                        log.info("GOTR post-schedule tasks completed successfully");                   
                        // Report the plugin as finished will be handled by AbstractPrePostScheduleTasks                                                            
                    });
                } else {
                    log.warn("Attempted to run post-schedule tasks but not in scheduler mode");
                }
            } else {
                log.error("Cannot run post-schedule tasks - task system not initialized");
            }
        }else {
            log.warn("Post-schedule tasks already completed or running for GOTR");
        }
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
        
        log.info("{} Plugin - Received PluginScheduleEntryPreScheduleTaskEvent", getName());
        
        if (prePostScheduleTasks != null && event.isSchedulerControlled() && !prePostScheduleTasks.isPreTaskComplete() && !prePostScheduleTasks.isPreScheduleRunning()) {
            // Plugin has pre/post tasks and is under scheduler control
            log.info("{} starting with pre-schedule tasks", getName());                                
            try {
                // Execute pre-schedule tasks with callback to start the script
                runPreScheduleTasks();
            } catch (Exception e) {
                log.error("Error during Pre-Schedule Tasks {} of the plugin:\n\t{}", getName(),e );                
            }
        }
    }
    /**
     * Handles the post-schedule task event from the scheduler system.
     * This event is triggered when the scheduler wants to gracefully stop this plugin
     * and execute any necessary cleanup tasks. The method determines whether to run
     * post-schedule tasks or perform a direct shutdown based on the plugin's current mode.*
     * @param event The post-schedule task event containing plugin reference and execution context
     * @see PluginScheduleEntryPostScheduleTaskEvent
     * @see AbstractPrePostScheduleTasks#executePostScheduleTasks(Runnable)
     * @see #getConfigDescriptor()
     * @see #getPrePostScheduleTasks()
     * @see net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin
     */
    @Subscribe
    public void onPluginScheduleEntryPostScheduleTaskEvent(PluginScheduleEntryPostScheduleTaskEvent event) {
        if (event.getPlugin() == this) {
            log.info("Scheduler requesting GOTR Plugin soft stop");
            
            // Only run post-schedule tasks if in scheduler mode
            AbstractPrePostScheduleTasks tasks = getPrePostScheduleTasks();
            if (tasks != null && !tasks.isPostScheduleRunning() && !tasks.isPostTaskComplete()) {
                log.info("Running post-schedule tasks for scheduler stop");
                if(gotrScript != null && gotrScript.isRunning()) {
                    gotrScript.shutdown();
                }
                runPostScheduleTasks();
            } else {
                log.info("Not in scheduler mode, performing direct shutdown");
                // Direct shutdown for non-scheduler mode
                if (gotrScript != null) {
                    gotrScript.shutdown();
                }                                
            }
        }
    }

    /**
     * Provides the configuration descriptor for scheduler integration and per-entry configuration management.     
     * @return The configuration descriptor for this plugin, or null if configuration is unavailable
     * @see net.runelite.client.config.ConfigDescriptor
     * @see net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin
     * @see net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry
     * @see AbstractPrePostScheduleTasks#isScheduleMode()
     * @see net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin#getConfigDescriptor()
     */
    @Override
    public ConfigDescriptor getConfigDescriptor() {
        if (Microbot.getConfigManager() == null) {
            return null;
        }
        GotrConfig conf = Microbot.getConfigManager().getConfig(GotrConfig.class);
        return Microbot.getConfigManager().getConfigDescriptor(conf);
    }   
    
}
