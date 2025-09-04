package net.runelite.client.plugins.microbot.TaF.GiantSeaweedFarmer;

import net.runelite.client.eventbus.Subscribe;
import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPostScheduleTaskEvent;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Giant Seaweed",
        description = "Farms giant seaweed.",
        tags = {"GiantSeaweedFarmer", "seaweed", "farming", "ironman", "taf", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class GiantSeaweedFarmerPlugin extends Plugin implements SchedulablePlugin {
    private Instant scriptStartTime;
    @Inject
    private GiantSeaweedFarmerConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GiantSeaweedFarmerOverlay giantSeaweedFarmerOverlay;
    @Inject
    private GiantSeaweedFarmerScript giantSeaweedFarmerScript;
    @Inject
    private GiantSeaweedSporeScript giantSeaweedSporeScript;
    private LogicalCondition stopCondition = new AndCondition();
    private LockCondition lockCondition;

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(giantSeaweedFarmerOverlay);
        }
        if(lockCondition == null) {
            getStopCondition();
        }
        lockCondition.lock();
        giantSeaweedFarmerScript.run(config);
        // Start the spore looting script if configured
        if (config.lootSeaweedSpores()) {
            giantSeaweedSporeScript.run(config);
        }
    }

    @Override
    protected void shutDown() {
        if (giantSeaweedFarmerScript  != null && giantSeaweedFarmerScript.isRunning()) {
            giantSeaweedFarmerScript.shutdown();
        }
        if (giantSeaweedSporeScript != null && giantSeaweedSporeScript.isRunning()) {
            giantSeaweedSporeScript.shutdown();
        }
        if (lockCondition != null && lockCondition.isLocked()) {
            lockCondition.unlock();
        }
        overlayManager.remove(giantSeaweedFarmerOverlay);
    }

    @Provides
    GiantSeaweedFarmerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GiantSeaweedFarmerConfig.class);
    }
    @Override
    @Subscribe    
    public void onPluginScheduleEntryPostScheduleTaskEvent(PluginScheduleEntryPostScheduleTaskEvent event) {        
        if (event.getPlugin() == this) {
            if ( giantSeaweedFarmerScript != null 
                    && giantSeaweedFarmerScript.isRunning() 
                    && giantSeaweedFarmerScript.BOT_STATE == GiantSeaweedFarmerStatus.FARMING) {                
                giantSeaweedFarmerScript.BOT_STATE = GiantSeaweedFarmerStatus.RETURN_TO_BANK;                
                Microbot.log("Stopping Giant Seaweed Farmer script due to soft stop event but we need to Returning to bank.");
                //in the future it would be better to use the new pre post schedule task system. 
                // not yet implemented implentented, upcomming in the next release.
                return;
            }            
            if ( lockCondition != null && lockCondition.isLocked()) {
                // If the lock condition is active, we can't stop the plugin yet
                Microbot.log("Stopping Giant Seaweed Farmer script due to soft stop event, but we are currently locked. Waiting to unlock...");
                return;
        }
            
            Microbot.log("Stopping Giant Seaweed Farmer script due to soft stop event.");
            Microbot.stopPlugin(this);
        }
    }    
    @Override
    public LogicalCondition getStopCondition() {
        if (lockCondition == null) {
            // Create a lock condition to prevent the plugin from running while the bank is open
            lockCondition = new LockCondition("Giant Seaweed Farmer lock",false,true);
            this.stopCondition.addCondition(lockCondition);
        }
        // Create a new stop condition
        return this.stopCondition;
    }
    
    public LockCondition getLockCondition(LogicalCondition stopCondition) {
        return lockCondition;
    }    
}
