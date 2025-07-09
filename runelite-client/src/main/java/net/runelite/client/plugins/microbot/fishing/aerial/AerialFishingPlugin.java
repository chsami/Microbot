package net.runelite.client.plugins.microbot.fishing.aerial;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + " Aerial Fisher",
        description = "Aerial Fishing plugin",
        tags = {"Fishing", "microbot" , "Aerial", "skilling"},
        enabledByDefault = false
)
public class AerialFishingPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AerialFishingOverlay fishingOverlay;
    @Inject
    private AerialFishingScript fishingScript;
    @Inject
    private AerialFishingConfig config;

    @Provides
    AerialFishingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AerialFishingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(fishingOverlay);
        }
        fishingScript.run(config);
    }


    protected void shutDown() {
        fishingScript.shutdown();
        overlayManager.remove(fishingOverlay);
    }
}
