package net.runelite.client.plugins.microbot.herbrun;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Herb runner",
        description = "Herb runner",
        tags = {"herb", "farming", "money making", "skilling"},
        enabledByDefault = false
)
public class HerbrunPlugin extends Plugin implements SchedulablePlugin{
    @Inject
    private HerbrunConfig config;
    @Provides
    HerbrunConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HerbrunConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HerbrunOverlay HerbrunOverlay;

    @Inject
    HerbrunScript herbrunScript;

    static String status;
    private LogicalCondition stopCondition = new AndCondition();
    

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(HerbrunOverlay);
        }
        herbrunScript.run();
    }

    protected void shutDown() {
        herbrunScript.shutdown();
        overlayManager.remove(HerbrunOverlay);
    }

    @Subscribe
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        if (event.getPlugin() == this) {
            Microbot.stopPlugin(this);
        }
    }
     @Override     
    public LogicalCondition getStopCondition() {
        // Create a new stop condition        
        return this.stopCondition;
    }
    @Override
    public ConfigDescriptor getConfigDescriptor() {
        if (Microbot.getConfigManager() == null) {
            return null;
        }
        HerbrunConfig conf = Microbot.getConfigManager().getConfig(HerbrunConfig.class);
        return Microbot.getConfigManager().getConfigDescriptor(conf);
    }

}
