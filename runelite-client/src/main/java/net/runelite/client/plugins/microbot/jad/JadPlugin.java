package net.runelite.client.plugins.microbot.jad;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Jad Helper",
        description = "Jad Prayer Switcher plugin",
        tags = {"Jad", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class JadPlugin extends Plugin {
    @Inject
    private JadConfig config;
    @Provides
    JadConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(JadConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private JadOverlay jadOverlay;

    @Inject
    JadScript jadScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(jadOverlay);
        }
        jadScript.run(config);
    }

    protected void shutDown() {
        jadScript.shutdown();
        overlayManager.remove(jadOverlay);
    }
}
