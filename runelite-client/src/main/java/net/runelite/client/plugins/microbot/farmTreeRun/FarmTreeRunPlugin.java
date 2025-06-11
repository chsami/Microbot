package net.runelite.client.plugins.microbot.farmTreeRun;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.blastoisefurnace.BlastoiseFurnaceScript;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

/**
 * Made by Acun
 */
@PluginDescriptor(
        name = PluginDescriptor.Default + "Farm tree runner",
        description = "Acun's farm tree runner. Supports regular and fruit trees",
        tags = {"Farming", "Tree run"},
        enabledByDefault = false
)
@Slf4j
public class FarmTreeRunPlugin extends Plugin implements SchedulablePlugin {
    @Inject
    private FarmTreeRunConfig config;
    @Provides
    FarmTreeRunConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FarmTreeRunConfig.class);
    }
    private LogicalCondition stopCondition = new AndCondition();

    @Override
    public LogicalCondition getStartCondition() {
        // Create conditions that determine when your plugin can start
        // Return null if the plugin can start anytime
        return null;
    }

    @Override
    public LogicalCondition getStopCondition() {
        // Create a new stop condition

        return this.stopCondition;
    }

    @Subscribe
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        if (event.getPlugin() == this) {
            if (FarmTreeRunScript != null && FarmTreeRunScript.isRunning()) {
                FarmTreeRunScript.shutdown();
            }
            Microbot.getClientThread().invokeLater( ()->  {Microbot.stopPlugin(this); return true;});
        }
    }

    @Inject
    FarmTreeRunScript FarmTreeRunScript;

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FarmTreeRunOverlay exampleOverlay;

    @Inject
    FarmTreeRunScript farmTreeRunScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(exampleOverlay);
        }
        farmTreeRunScript.run(config);
    }

    protected void shutDown() {
        farmTreeRunScript.shutdown();
        overlayManager.remove(exampleOverlay);
    }
    int ticks = 10;
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        //System.out.println(getName().chars().mapToObj(i -> (char)(i + 3)).map(String::valueOf).collect(Collectors.joining()));

        if (ticks > 0) {
            ticks--;
        } else {
            ticks = 10;
        }

    }

}
