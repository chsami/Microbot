package net.runelite.client.plugins.microbot.maxxin.housethieving;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;


@PluginDescriptor(
        name = PluginDescriptor.Maxxin + "House Thieving",
        description = "House Thieving",
        tags = {"thieving", "house thieving"},
        enabledByDefault = false
)
@Slf4j
public class HouseThievingPlugin extends Plugin {
    @Inject
    private HouseThievingConfig config;

    @Provides
    HouseThievingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HouseThievingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HouseThievingOverlay houseThievingOverlay;

    private HouseThievingScript houseThievingScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(houseThievingOverlay);
        }

        houseThievingScript = new HouseThievingScript(this);
        houseThievingScript.run(config);
    }

    protected void shutDown() {
        houseThievingScript.shutdown();
        overlayManager.remove(houseThievingOverlay);
    }
}
