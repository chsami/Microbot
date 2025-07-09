package net.runelite.client.plugins.microbot.hal.blessedwine;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
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
        name = PluginDescriptor.Hal + "Blessed Wine",
        description = "Automating Prayer Training",
        tags = {"blessed", "wine", "ralos", "prayer", "libation"},
        enabledByDefault = false
)
public class BlessedWinePlugin extends Plugin implements SchedulablePlugin {
    @Inject
    private BlessedWineConfig config;
    @Provides
    BlessedWineConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BlessedWineConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BlessedWineOverlay blessedWineOverlay;

    @Inject
    BlessedWineScript blessedWineScript;
    static String status = "Initializing...";
    static int loopCount = 0;
    static int expectedXp = 0;
    static int startingXp = 0;
    static int totalWinesToBless = 0;
    static int totalLoops = 0;
    static int endingXp = 0;
    private LogicalCondition stopCondition = new AndCondition();

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(blessedWineOverlay);
        }
        blessedWineScript.run();
    }

    @Override
    protected void shutDown() {
        blessedWineScript.shutdown();
        overlayManager.remove(blessedWineOverlay);
    }

    @Subscribe
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        if (event.getPlugin() == this) {
            Microbot.stopPlugin(this);
        }
    }
    @Override
    public LogicalCondition getStopCondition() {
        return this.stopCondition;
    }
}