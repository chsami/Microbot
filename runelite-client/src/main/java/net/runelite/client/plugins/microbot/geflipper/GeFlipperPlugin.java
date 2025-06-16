package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "GE Flipper",
        description = "Flips items on the Grand Exchange",
        tags = {"ge", "flip", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class GeFlipperPlugin extends Plugin {
    @Inject
    private GeFlipperConfig config;

    @Provides
    GeFlipperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GeFlipperConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GeFlipperOverlay overlay;
    @Inject
    private GeFlipperScript script;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
