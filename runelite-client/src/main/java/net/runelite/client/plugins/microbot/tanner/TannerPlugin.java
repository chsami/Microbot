package net.runelite.client.plugins.microbot.tanner;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
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
        name = PluginDescriptor.Mocrosoft + "Tanner",
        description = "Microbot tanner plugin",
        tags = {"tanner", "microbot", "moneymaking"},
        enabledByDefault = false,
        hidden = true
)
@Slf4j
public class TannerPlugin extends Plugin {
    @Inject
    private TannerConfig config;
    @Provides
    TannerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TannerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private TannerOverlay tannerOverlay;

    @Inject
    TannerScript tannerScript;


    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);

        if (overlayManager != null) {
            overlayManager.add(tannerOverlay);
        }
        tannerScript.run(config);
    }

    protected void shutDown() {
        tannerScript.shutdown();
        overlayManager.remove(tannerOverlay);
    }
}
