package net.runelite.client.plugins.microbot.gemMining;

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
        name = PluginDescriptor.Gage + "Gem Miner",
        description = "Mines gems in medium diary spot with or without gem pouch",
        tags = {"Gem Miner Gem bag gem pouch", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class GemPlugin extends Plugin {
    @Inject
    private net.runelite.client.plugins.microbot.gemMining.GemConfig config;
    @Provides
    net.runelite.client.plugins.microbot.gemMining.GemConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GemConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GemOverlay gemOverlay;

    @Inject
    GemScript gemScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(gemOverlay);
        }
        gemScript.run(config);
    }

    protected void shutDown() {
        gemScript.shutdown();
        overlayManager.remove(gemOverlay);
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