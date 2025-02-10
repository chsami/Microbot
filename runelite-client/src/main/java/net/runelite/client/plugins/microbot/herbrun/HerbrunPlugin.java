package net.runelite.client.plugins.microbot.herbrun;

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
        name = PluginDescriptor.LiftedMango + "Herb runner",
        description = "LiftedMango's Herb runner",
        tags = {"herb", "liftedmango", "farming", "money making", "skilling"},
        enabledByDefault = false
)
@Slf4j
public class HerbrunPlugin extends Plugin {
    @Inject
    private HerbrunConfig config;
    @Provides
    HerbrunConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HerbrunConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HerbrunOverlay HerbrunOverlay;

    @Inject
    HerbrunScript herbrunScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(HerbrunOverlay);
        }
        herbrunScript.run(config);
    }

    protected void shutDown() {
        herbrunScript.shutdown();
        overlayManager.remove(HerbrunOverlay);
    }
    int ticks = 10;
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (ticks > 0) {
            ticks--;
        } else {
            ticks = 10;
        }

    }

}
