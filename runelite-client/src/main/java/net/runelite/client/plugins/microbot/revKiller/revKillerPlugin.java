package net.runelite.client.plugins.microbot.revKiller;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
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
    private net.runelite.client.plugins.microbot.revKiller.revKillerConfig config;
    @Provides
    net.runelite.client.plugins.microbot.revKiller.revKillerConfig provideConfig(ConfigManager configManager) {
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
        eventBus.register(revKillerScript);
    }

    protected void shutDown() {
        revKillerScript.stopFutures();
        revKillerScript.shutdown();
        eventBus.unregister(revKillerScript);
        overlayManager.remove(revKillerOverlay);
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
