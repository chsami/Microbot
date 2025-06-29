package net.runelite.client.plugins.microbot.GirdyScripts.cannonballsmelter;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Girdy + "Cannonball Smelter",
        description = "Makes cannonballs",
        tags = {"smithing", "girdy", "skilling"},
        enabledByDefault = false
)
public class CannonballSmelterPlugin extends Plugin {

        @Inject
        private CannonballSmelterConfig config;

        @Provides
        CannonballSmelterConfig provideConfig(ConfigManager configManager) {
            return configManager.getConfig(CannonballSmelterConfig.class);
        }

        @Inject
        private OverlayManager overlayManager;
        @Inject
        private CannonballSmelterOverlay cannonballSmelterOverlay;

        @Inject
        CannonballSmelterScript cannonballSmelterScript;

        @Override
        protected void startUp() throws AWTException {
            Microbot.pauseAllScripts = false;
            if (overlayManager != null) {
                overlayManager.add(cannonballSmelterOverlay);
            }
            cannonballSmelterScript.run(config);
        }

        protected void shutDown() {
            cannonballSmelterScript.shutdown();
            overlayManager.remove(cannonballSmelterOverlay);
        }
}

