package net.runelite.client.plugins.microbot.TaF.GiantSeaweedFarmer;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
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
    private LogicalCondition stopCondition = new AndCondition();

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(giantSeaweedFarmerOverlay);
        }
        giantSeaweedFarmerScript.run(config);
    }

    @Override
    protected void shutDown() {
        giantSeaweedFarmerScript.shutdown();
        overlayManager.remove(giantSeaweedFarmerOverlay);
    }

    @Provides
    GiantSeaweedFarmerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GiantSeaweedFarmerConfig.class);
    }

    @Override
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        if (event.getPlugin() == this) {
            if (giantSeaweedFarmerScript != null) {
                Rs2Bank.walkToBank();
            }
            Microbot.stopPlugin(this);
        }
    }

    @Override
    public LogicalCondition getStopCondition() {
        // Create a new stop condition
        return this.stopCondition;
    }
}
