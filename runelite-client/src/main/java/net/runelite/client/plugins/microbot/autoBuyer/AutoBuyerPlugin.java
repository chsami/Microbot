package net.runelite.client.plugins.microbot.autoBuyer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

/**
 * Made by Acun
 */
@PluginDescriptor(
        name = PluginDescriptor.Default + "GE Buyer",
        description = "Acun's GE buyer. Give a list of items to buy",
        tags = {"buy", "buyer", "grand exchange", "ge"},
        enabledByDefault = false
)
@Slf4j
public class AutoBuyerPlugin extends Plugin {
    @Inject
    private AutoBuyerConfig config;
    @Provides
    AutoBuyerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoBuyerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoBuyerOverlay exampleOverlay;

    @Inject
    AutoBuyerScript exampleScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(exampleOverlay);
        }
        exampleScript.run(config);
    }

    protected void shutDown() {
        exampleScript.shutdown();
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
