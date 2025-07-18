package net.runelite.client.plugins.microbot.f2pAccountBuilder;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.example.ExampleConfig;
import net.runelite.client.plugins.microbot.example.ExampleOverlay;
import net.runelite.client.plugins.microbot.example.ExampleScript;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Gage + "F2P Acc Builder",
        description = "F2P Account Builder",
        tags = {"F2P Account Builder", "F2P", "Account", "Builder"},
        enabledByDefault = false
)
@Slf4j
public class f2pAccountBuilderPlugin extends Plugin implements SchedulablePlugin {
    @Inject
    private f2pAccountBuilderConfig config;
    @Provides
    f2pAccountBuilderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(f2pAccountBuilderConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private f2pAccountBuilderOverlay f2paccountbuilderOverlay;

    @Inject
    f2pAccountBuilderScript f2paccountbuilderScript;
    LogicalCondition stopCondition = new AndCondition();


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(f2paccountbuilderOverlay);
            f2paccountbuilderOverlay.myButton.hookMouseListener();
        }
        f2paccountbuilderScript.run(config);
        f2paccountbuilderScript.shouldThink = true;
        f2paccountbuilderScript.scriptStartTime = System.currentTimeMillis();
        Rs2Antiban.activateAntiban(); // Enable Anti Ban
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_WOODCUTTING);
    }

    protected void shutDown() {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.deactivateAntiban();
        f2paccountbuilderScript.shutdown();
        overlayManager.remove(f2paccountbuilderOverlay);
        f2paccountbuilderOverlay.myButton.unhookMouseListener();
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
