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
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.example.ExampleConfig;
import net.runelite.client.plugins.microbot.example.ExampleOverlay;
import net.runelite.client.plugins.microbot.example.ExampleScript;
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
public class revKillerPlugin extends Plugin {
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
        eventBus.register(this);
        revKillerScript.selectedWP = config.selectedRev().getWorldPoint();
        revKillerScript.selectedArrow = config.selectedArrow().getArrowID();
        revKillerScript.selectedRev = config.selectedRev().getName();
    }

    protected void shutDown() {
        revKillerScript.weDied = false;
        revKillerScript.shouldFlee = false;
        revKillerScript.ourEquipmentForDeathWalking.clear();
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
