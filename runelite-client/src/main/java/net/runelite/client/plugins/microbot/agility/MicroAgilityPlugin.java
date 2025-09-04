package net.runelite.client.plugins.microbot.agility;

import com.google.inject.Provides;
import java.awt.AWTException;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.agility.AgilityPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.courses.AgilityCourseHandler;
import net.runelite.client.plugins.microbot.agility.requirement.MicroAgilityPrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.agility.tasks.MicroAgilityPrePostScheduleTasks;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.PredicateCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPostScheduleTaskEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPreScheduleTaskEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.AbstractPrePostScheduleTasks;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.events.PluginPauseEvent;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(

	name = PluginDescriptor.Mocrosoft + "Agility",
	description = "Microbot agility plugin",
	tags = {"agility", "microbot"},
	enabledByDefault = false
)
@Slf4j
public class MicroAgilityPlugin extends Plugin implements SchedulablePlugin
{
	@Inject
	private MicroAgilityConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private MicroAgilityOverlay agilityOverlay;
	@Inject
	private AgilityScript agilityScript;

	private LogicalCondition stopCondition;
	
	// Pre/Post Schedule Tasks and Requirements
	private MicroAgilityPrePostScheduleRequirements prePostScheduleRequirements = null;
	private MicroAgilityPrePostScheduleTasks prePostScheduleTasks = null;

	@Provides
	MicroAgilityConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MicroAgilityConfig.class);
	}
	public MicroAgilityConfig getConfig()
	{
		return config;
	}

	@Override
	protected void startUp() throws AWTException
	{
		if (overlayManager != null)
		{
			overlayManager.add(agilityOverlay);
		}

		// Initialize pre/post schedule tasks
		if (Microbot.isLoggedIn()) {
			getPrePostScheduleTasks(); // Ensure tasks are initialized if logged in at startup
			// Check if running in schedule mode
			if (isScheduleMode()) {
				log.info("Agility Plugin started in Scheduler Mode, wait for the event trigger to run pre-schedule tasks");
				// In scheduler mode, pre-tasks will be executed when logged in via onGameStateChanged
			} else {
				log.info("Agility Plugin started in Normal Mode");
				// In normal mode, start the script directly
				agilityScript.run();
			}
		}
	}

	protected void shutDown()
	{
		// Shutdown pre/post tasks if they exist
		if (prePostScheduleTasks != null) {
			prePostScheduleTasks.close();
		}
		prePostScheduleTasks = null;
		if (prePostScheduleRequirements != null) {
			prePostScheduleRequirements.reset();
		}
		prePostScheduleRequirements = null;
		unlockAllStopConditions();
		agilityScript.shutdown();
		overlayManager.remove(agilityOverlay);
		
	}

	@Subscribe
	public void onPluginScheduleEntryPostScheduleTaskEvent(PluginScheduleEntryPostScheduleTaskEvent event) {
		try{
			 if (event.getPlugin() == this) {
				if (isLocked(getStopCondition())) {
	              	log.warn("Agility: PostScheduleTaskEvent ignored while locked.");
	                return;
	            }
				log.info("Scheduler requesting Agility Plugin soft stop");
				PluginPauseEvent.setPaused(true);
				if(agilityScript != null && agilityScript.isRunning()) {
					agilityScript.shutdown();// we could also consider pausing the script here PluginPauseEvent.setPaused(True); but than we would need reset it on plugin shutdown!
				}
				PluginPauseEvent.setPaused(false);
				Rs2Walker.setTarget(null);
				Microbot.getClientThread().runOnSeperateThread(() -> {
					if (config.agilityCourse().getHandler().getCurrentObstacleIndex() > 0 || Rs2Player.isInteracting()) {
						Global.sleepUntil(() -> config.agilityCourse().getHandler().getCurrentObstacleIndex() == 0, 10_000);
					}				
					// Only run post-schedule tasks if in scheduler mode
					AbstractPrePostScheduleTasks tasks = getPrePostScheduleTasks();
					if (tasks != null && !tasks.isPostScheduleRunning() && !tasks.isPostTaskComplete() && tasks.canStartPostScheduleTasks()) {
						log.info("Running post-schedule tasks for scheduler stop");						
						runPostScheduleTasks();
						return null; // Indicate that the plugin has been stopped
					} else{					
						Microbot.stopPlugin(this);
						return null;						
					}
				});
			}
		} catch (Exception e) {
			log.error("Error stopping plugin: ", e);
		}
	}

	@Override
	public LogicalCondition getStopCondition() {
		if (stopCondition == null) {
			LogicalCondition _stopCondition = new AndCondition();

			Supplier<Integer> currentIndexSupplier = () -> config.agilityCourse().getHandler().getCurrentObstacleIndex();
			Predicate<Integer> isAtStartPredicate = index -> ((Microbot.getClient().getLocalPlayer()!=null && !Rs2Player.isInteracting()) && index == 0 && (AgilityPlugin.getMarksOfGrace() == null || AgilityPlugin.getMarksOfGrace().isEmpty()));
			PredicateCondition<Integer> atStartCondition = new PredicateCondition<>(
				"Locked because agility plugin is running through an obstacle course",
				true, // break handler should respect this lock -> so we cant break  during the course run
				isAtStartPredicate,
				currentIndexSupplier,
				"Player is at the start of the agility course (index 0)"
			);

			_stopCondition.addCondition(atStartCondition);
			stopCondition = _stopCondition;
		}
		return stopCondition;
	}
	
	private boolean isScheduleMode() {		
		return AbstractPrePostScheduleTasks.isScheduleMode(this,getConfigDescriptor().getGroup().value());
	}
	
	@Override
	public AbstractPrePostScheduleTasks getPrePostScheduleTasks() {
		if (prePostScheduleRequirements == null) {
			if(Microbot.getClient().getGameState() != GameState.LOGGED_IN) {
               log.debug("MicroAgility Plugin - Cannot provide pre/post schedule tasks - not logged in");
                return null; // Return null if not logged in
            }
			log.info("Initializing Agility Pre/Post Schedule Requirements and Tasks...");
			this.prePostScheduleRequirements = new MicroAgilityPrePostScheduleRequirements(config);
			log.info("finalized Agility Pre/Post Schedule Requirements:\n{}", prePostScheduleRequirements.getDetailedDisplay());
		}			
		if (this.prePostScheduleTasks==null){
			log.info("Creating Agility Pre/Post Schedule Tasks...");
			this.prePostScheduleTasks = new MicroAgilityPrePostScheduleTasks(this, prePostScheduleRequirements);	
			log.info("Agility Pre/Post Schedule Tasks created successfully");						
		}
				
		// Return the pre/post schedule tasks instance
		return this.prePostScheduleTasks;
	}

	public AgilityCourseHandler getCourseHandler()
	{
		return config.agilityCourse().getHandler();
	}

	public List<Rs2ItemModel> getInventoryFood()
	{
		return Rs2Inventory.getInventoryFood().stream().filter(i -> !(i.getName().toLowerCase().contains("summer pie"))).collect(Collectors.toList());
	}

	public List<Rs2ItemModel> getSummerPies()
	{
		return Rs2Inventory.getInventoryFood().stream().filter(i -> i.getName().toLowerCase().contains("summer pie")).collect(Collectors.toList());
	}

	public boolean hasRequiredLevel()
	{
		if (getSummerPies().isEmpty() || !getCourseHandler().canBeBoosted())
		{
			return Rs2Player.getRealSkillLevel(Skill.AGILITY) >= getCourseHandler().getRequiredLevel();
		}

		return Rs2Player.getBoostedSkillLevel(Skill.AGILITY) >= getCourseHandler().getRequiredLevel();
	}
	
	public AgilityScript getAgilityScript() {
		return agilityScript;
	}
	
	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			log.debug("GameState changed to LOGGED_IN - initializing Agility tasks");
			
			// Only run pre-schedule tasks if in scheduler mode
			if (!agilityScript.isRunning() && !isScheduleMode()) {                           
				// In normal mode, start script if not already running
				if (agilityScript != null && !agilityScript.isRunning()) {
					log.debug("Game State - Agility Plugin in Normal Mode - starting script");
					agilityScript.run();
				}
			}
		} else if (event.getGameState() == GameState.LOGIN_SCREEN) {
			// Reset pre/post schedule tasks on logout for fresh initialization
			log.debug("GameState changed to LOGIN_SCREEN - resetting Agility tasks");
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
	/**
	 * Runs pre-schedule tasks to prepare the agility plugin for automated execution.
	 * This includes equipment setup, location verification, and inventory preparation.
	 * Only executes if in scheduler mode.
	 */
	public void runPreScheduleTasks() {
		log.info("Starting Agility pre-schedule tasks...");
		
		AbstractPrePostScheduleTasks tasks = getPrePostScheduleTasks();
		if (prePostScheduleTasks != null && !prePostScheduleTasks.isPreTaskRunning() && !prePostScheduleTasks.isPreTaskComplete()) {
			if (tasks != null) {
				// Only execute pre-schedule tasks if in scheduler mode
				if (tasks.isScheduleMode() && !tasks.isPreTaskComplete()) {
					tasks.executePreScheduleTasks(() -> {
						log.info("Agility pre-schedule tasks completed successfully starting the script");
						// Start the main agility script after pre-tasks are complete
						if (agilityScript != null) {
							agilityScript.run();
						}
					});
				} else {
					log.warn("Attempted to run pre-schedule tasks but not in scheduler mode");
				}
			} else {
				log.error("Cannot run pre-schedule tasks - task system not initialized");
			}
		} else {
			log.warn("Agility pre-schedule tasks already completed or running");
		}
	}

	/**
	 * Runs post-schedule tasks to clean up after agility plugin execution.
	 * This includes moving to Grand Exchange and banking items.
	 * Only executes if in scheduler mode.
	 */
	public void runPostScheduleTasks() {
		if (prePostScheduleTasks != null && !prePostScheduleTasks.isPostScheduleRunning() && !prePostScheduleTasks.isPostTaskComplete()) {        
			log.info("\n\t........Starting Agility post-schedule tasks.......");
			
			AbstractPrePostScheduleTasks tasks = getPrePostScheduleTasks();
			if (tasks != null) {
				// Only execute post-schedule tasks if in scheduler mode
				if (tasks.isScheduleMode()) {
					tasks.executePostScheduleTasks(() -> {
						log.info("Agility post-schedule tasks completed successfully");                   
						// Report the plugin as finished will be handled by AbstractPrePostScheduleTasks                                                            
					});
				} else {
					log.warn("Attempted to run post-schedule tasks but not in scheduler mode");
				}
			} else {
				log.error("Cannot run post-schedule tasks - task system not initialized");
			}
		} else {
			log.warn("Post-schedule tasks already completed or running for Agility");
		}
	}

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
				log.error("Error during Pre-Schedule Tasks {} of the plugin:\n\t{}", getName(), e);                
			}
		}
	}
	
	@Override
	public ConfigDescriptor getConfigDescriptor() {
		if (Microbot.getConfigManager() == null) {
			return null;
		}
		MicroAgilityConfig conf = Microbot.getConfigManager().getConfig(MicroAgilityConfig.class);
		return Microbot.getConfigManager().getConfigDescriptor(conf);
	}
}
