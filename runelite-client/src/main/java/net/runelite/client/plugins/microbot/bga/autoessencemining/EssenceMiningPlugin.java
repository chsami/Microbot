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
public class EssenceMiningPlugin extends Plugin {
    @Inject
    private EssenceMiningConfig config;
    
    @Provides
    EssenceMiningConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EssenceMiningConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    
    @Inject
    private EssenceMiningOverlay essenceMiningOverlay;

    @Inject
    EssenceMiningScript essenceMiningScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(essenceMiningOverlay);
        }
        essenceMiningOverlay.resetStartTime();
        essenceMiningScript.run(config);
    }

    protected void shutDown() {
        essenceMiningScript.shutdown();
        overlayManager.remove(essenceMiningOverlay);
    }
}