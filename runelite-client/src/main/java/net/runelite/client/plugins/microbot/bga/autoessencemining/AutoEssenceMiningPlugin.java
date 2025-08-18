package net.runelite.client.plugins.microbot.bga.autoessencemining;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "[bga] Auto Essence Mining",
        description = "Mines Rune/Pure Essence...",
        tags = {"mining", "essence", "skilling"},
        enabledByDefault = false
)
@Slf4j
public class AutoEssenceMiningPlugin extends Plugin {
    @Inject
    private AutoEssenceMiningConfig config;
    
    @Provides
    AutoEssenceMiningConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoEssenceMiningConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    
    @Inject
    private AutoEssenceMiningOverlay autoEssenceMiningOverlay;

    @Inject
    AutoAutoEssenceMiningScript autoEssenceMiningScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(autoEssenceMiningOverlay);
        }
        autoEssenceMiningOverlay.resetStartTime();
        autoEssenceMiningScript.run(config);
    }

    protected void shutDown() {
        autoEssenceMiningScript.shutdown();
        overlayManager.remove(autoEssenceMiningOverlay);
    }
}