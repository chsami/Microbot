package net.runelite.client.plugins.microbot.wildyrunite;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Runite Miner",
        description = "Mines Runite ore in the wilderness, banks, and avoids other players.",
        tags = {"mining", "wilderness", "runite", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class WildernessRuniteMiningPlugin extends Plugin {
    @Inject
    private WildernessRuniteMiningConfig config;

    @Provides
    WildernessRuniteMiningConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(WildernessRuniteMiningConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private WildernessRuniteMiningOverlay overlay;

    @Inject
    private WildernessRuniteMiningScript script;

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

    public WildernessRuniteMiningScript getScript() {
        return script;
    }
}
