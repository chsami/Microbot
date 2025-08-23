package net.runelite.client.plugins.microbot.revKiller;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Gage + "Rev Killer",
        description = "Kills Revs based on stat levels",
        tags = {"rev Killer mm money making", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class revKillerPlugin extends Plugin implements SchedulablePlugin {
    @Inject
    private revKillerConfig config;
    @Provides
    revKillerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(revKillerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private revKillerOverlay revKillerOverlay;

    @Inject
    revKillerScript revKillerScript;
    LogicalCondition stopCondition = new AndCondition();

    @Inject
    private EventBus eventBus;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(revKillerOverlay);
        }
        revKillerScript.run(config);
        revKillerScript.startPkerDetection();
        revKillerScript.startHealthCheck();
        revKillerScript.weDied = false;
        revKillerScript.shouldFlee = false;
        revKillerScript.firstRun = true;
        eventBus.register(this);
        revKillerScript.selectedWP = config.selectedRev().getWorldPoint();
        revKillerScript.selectedArrow = config.selectedArrow().getArrowID();
        revKillerScript.selectedRev = config.selectedRev().getName();
        Rs2Antiban.activateAntiban();
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCombatSetup();
        Rs2Antiban.setActivity(Activity.KILLING_REVENANTS_MAGIC_SHORTBOW);
    }

    protected void shutDown() {
        revKillerScript.weDied = false;
        revKillerScript.shouldFlee = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.deactivateAntiban();
        revKillerScript.stopFutures();
        revKillerScript.shutdown();
        eventBus.unregister(this);
        overlayManager.remove(revKillerOverlay);
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        //Thank you george!
        if (event.getActor().equals(Microbot.getClient().getLocalPlayer())) {
            revKillerScript.weDied = true;
        }
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
