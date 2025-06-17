package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "GE Flipper",
        description = "Simple GE flipping bot",
        tags = {"grand exchange", "flip", "ge"},
        enabledByDefault = false
)
@Slf4j
public class GEFlipperPlugin extends Plugin {
    @Inject
    private GEFlipperConfig config;
    @Provides
    GEFlipperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GEFlipperConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GEFlipperOverlay overlay;

    @Inject
    GEFlipperScript script;

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
