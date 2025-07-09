package net.runelite.client.plugins.microbot.nateplugins.moneymaking.natehumidifier;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;


@PluginDescriptor(
        name = PluginDescriptor.Nate +"Humidifier",
        description = "Nate's Humidifier",
        tags = {"magic", "nate", "humidifier","moneymaking"},
        enabledByDefault = false
)
@Slf4j
public class HumidifierPlugin extends Plugin {
    @Inject
    private HumidifierConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HumidifierOverlay humidifierOverlay;

    @Inject
    HumidifierScript humidifierScript;

    @Provides
    HumidifierConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HumidifierConfig.class);
    }


    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(humidifierOverlay);
        }

        if (Microbot.getClient() == null)
            shutDown();

        humidifierScript.run(config);
    }

    protected void shutDown() {
        humidifierScript.shutdown();
        overlayManager.remove(humidifierOverlay);
    }
}
