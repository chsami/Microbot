package net.runelite.client.plugins.microbot.winecollector;

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
        name = PluginDescriptor.Logiko + "Wine Collector",
        description = "Collects Eclipse red from Hunter Guild",
        tags = {"eclipse", "wine", "collector", "moneymaking"},
        enabledByDefault = false
)
@Slf4j
public class WineCollectorPlugin extends Plugin {
    @Inject
    private WineCollectorConfig config;

    @Provides
    WineCollectorConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(WineCollectorConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private WineCollectorOverlay overlay;

    @Inject
    private WineCollectorScript script;

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    int ticks = 10;

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (ticks > 0) {
            ticks--;
        } else {
            ticks = 10;
        }
    }
}
