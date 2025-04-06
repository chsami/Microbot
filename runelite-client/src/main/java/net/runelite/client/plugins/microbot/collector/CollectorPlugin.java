package net.runelite.client.plugins.microbot.collector;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;


@PluginDescriptor(
        name = PluginDescriptor.Vopori + "Collector",
        description = "Collector plugin intended for ironmen.",
        tags = {"Collector", "microbot", "ironmen"},
        enabledByDefault = false
)
@Slf4j
public class CollectorPlugin extends Plugin {
    @Inject
    private CollectorConfig config;
    @Provides
    CollectorConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CollectorConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private CollectorOverlay collectorOverlay;

    @Inject
    CollectorScript collectorScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(collectorOverlay);
        }
        collectorScript.run(config);
    }

    protected void shutDown() {
        collectorScript.shutdown();
        overlayManager.remove(collectorOverlay);
    }
   

}
