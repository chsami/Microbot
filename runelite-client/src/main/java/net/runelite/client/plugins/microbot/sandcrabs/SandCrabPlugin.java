package net.runelite.client.plugins.microbot.sandcrabs;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "SandCrabs",
        description = "Kills SandCrab & resets",
        tags = {"Combat", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class SandCrabPlugin extends Plugin implements SchedulablePlugin {
    @Inject
    private SandCrabConfig config;
    @Provides
    SandCrabConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SandCrabConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private SandCrabOverlay sandCrabOverlay;

    @Inject
    public SandCrabScript sandCrabScript;

    private LockCondition lockCondition;
    private LogicalCondition stopCondition = null;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(sandCrabOverlay);
        }
        sandCrabScript.run(config, this);
    }

    protected void shutDown() {
        sandCrabScript.shutdown();
        overlayManager.remove(sandCrabOverlay);
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
        if (this.stopCondition == null) {
            this.lockCondition = new LockCondition("We're in combat");
            AndCondition andCondition = new AndCondition();
            andCondition.addCondition(lockCondition);
            this.stopCondition = andCondition;
        }
        return this.stopCondition;
    }
}
