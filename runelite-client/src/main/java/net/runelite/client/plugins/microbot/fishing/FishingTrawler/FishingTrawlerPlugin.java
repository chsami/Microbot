package net.runelite.client.plugins.microbot.fishing.FishingTrawler;

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

@PluginDescriptor(
        name = "<html>[<font color=#93652a>K</font>] " + "Fishing Trawler",
        description = "Chops Tentacles in fishing trawler minigame. start the script either on the dock outside the minigame or on the upstairs ship level where the tentacles spawn. make sure you have an axe on you",
        tags = {"example", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class FishingTrawlerPlugin extends Plugin {
    @Inject
    private FishingTrawlerConfig config;
    @Provides
    FishingTrawlerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FishingTrawlerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FishingTrawlerOverlay fishingTrawlerOverlay;

    @Inject
    FishingTrawlerScript fishingTrawlerScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(fishingTrawlerOverlay);
        }
        fishingTrawlerScript.run(config);
    }

    protected void shutDown() {
        fishingTrawlerScript.shutdown();
        overlayManager.remove(fishingTrawlerOverlay);
    }
  

}
