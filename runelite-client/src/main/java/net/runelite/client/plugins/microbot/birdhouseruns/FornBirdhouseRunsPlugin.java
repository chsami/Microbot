package net.runelite.client.plugins.microbot.birdhouseruns;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;


@PluginDescriptor(
        name = PluginDescriptor.Forn + "Birdhouse Runner",
        description = "Does a birdhouse run",
        tags = {"FornBirdhouseRuns", "forn"},
        enabledByDefault = false        
)
@Slf4j
public class FornBirdhouseRunsPlugin extends Plugin implements SchedulablePlugin {
    @Provides
    FornBirdhouseRunsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FornBirdhouseRunsConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FornBirdhouseRunsOverlay fornBirdhouseRunsOverlay;
    @Inject
    FornBirdhouseRunsScript fornBirdhouseRunsScript;
    LogicalCondition stopCondition = new AndCondition();

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(fornBirdhouseRunsOverlay);
        }
        fornBirdhouseRunsScript.run();
    }

    protected void shutDown() {
        fornBirdhouseRunsScript.shutdown();
        overlayManager.remove(fornBirdhouseRunsOverlay);
    }

    @Subscribe
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        try{
            if (event.getPlugin() == this) {
                Microbot.stopPlugin(this);
            }
        } catch (Exception e) {
            log.error("Error stopping plugin: ", e);
        }
    }
    @Override     
    public LogicalCondition getStopCondition() {
        // Create a new stop condition        
        return this.stopCondition;
    }
}
