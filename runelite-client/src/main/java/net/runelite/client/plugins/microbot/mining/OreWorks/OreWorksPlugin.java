package net.runelite.client.plugins.microbot.OreWorks;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Logiko + "OreWorks",
        description = "Logiko's Miner plugin. Mines, smelts and banks your ores!",
        tags = {"skilling", "mining", "smelting", "banking"},
        enabledByDefault = false
)
@Slf4j
public class OreWorksPlugin extends Plugin {

    @Inject
    private OreWorksConfig config;

    @Provides
    OreWorksConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(OreWorksConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private OreWorksOverlay oreWorksOverlay;

    @Inject
    private OreWorksScript oreWorksScript;

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(oreWorksOverlay);
        oreWorksScript.run(config);
    }

    @Override
    protected void shutDown() {
        if (oreWorksScript != null) {
            oreWorksScript.shutdown();
        }
        if (overlayManager != null && oreWorksOverlay != null) {
            overlayManager.remove(oreWorksOverlay);
        }
    }
}