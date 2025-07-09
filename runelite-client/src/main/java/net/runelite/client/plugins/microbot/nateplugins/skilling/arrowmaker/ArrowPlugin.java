package net.runelite.client.plugins.microbot.nateplugins.skilling.arrowmaker;

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
        name = PluginDescriptor.Nate +"Arrow Maker",
        description = "Nate's Arrow Maker",
        tags = {"MoneyMaking", "nate", "Arrow"},
        enabledByDefault = false
)
@Slf4j
public class ArrowPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ArrowConfig config;
    @Inject
    private ClientThread clientThread;
    @Inject
    Notifier notifier;

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ArrowOverlay arrowOverlay;

    @Inject
    ArrowScript arrowScript;

    @Provides
    ArrowConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ArrowConfig.class);
    }


    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(arrowOverlay);
        }
        arrowScript.run(config);
    }

    protected void shutDown() {
        arrowScript.shutdown();
        overlayManager.remove(arrowOverlay);
    }
}
