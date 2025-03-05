package net.runelite.client.plugins.microbot.mining.fxminer;

import com.google.inject.Provides;
import net.runelite.api.ObjectID;
import net.runelite.api.WallObject;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.FrostyX + "Yanille Miner",
        description = "Mine rocks in Yanille",
        enabledByDefault = false,
        tags = {"mining", "frosty", "yan"}
)

public class FxminerPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger(FxminerPlugin.class);
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FxminerOverlay fxminerOverlay;
    @Inject
    private FxminerScript fxminerScript;
    @Inject
    private FxminerConfig fxminerConfig;

    @Provides
    FxminerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FxminerConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(fxminerOverlay);
        fxminerScript.run(fxminerConfig);
    }

    @Override
    protected void shutDown() throws Exception {
        fxminerScript.shutdown();
        overlayManager.remove(fxminerOverlay);
    }
}
