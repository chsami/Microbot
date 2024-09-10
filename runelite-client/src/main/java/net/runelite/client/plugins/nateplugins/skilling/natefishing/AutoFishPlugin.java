package net.runelite.client.plugins.nateplugins.skilling.natefishing;

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
        name = PluginDescriptor.Nate + "Auto Fishing",
        description = "Nate's Power Fisher plugin",
        tags = {"Fishing", "nate", "skilling"},
        enabledByDefault = false
)
@Slf4j
public class AutoFishPlugin extends Plugin {
    @Inject
    private AutoFishConfig config;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    Notifier notifier;

    @Provides
    AutoFishConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoFishConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoFishOverlay fishingOverlay;

    @Inject
    AutoFishingScript fishingScript;


    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts = false;
        Microbot.setClient(client);
        Microbot.setClientThread(clientThread);
        Microbot.setNotifier(notifier);
        Microbot.setMouse(new VirtualMouse());
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
