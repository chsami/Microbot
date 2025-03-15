package net.runelite.client.plugins.microbot.bee.Baking;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.bee.Baking.scripts.BakingScript;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Burn Baking",
        description = "Give ingredients and optimally burn bakes, bread, stew and cake in Clan Hall",
        tags = {"baking", "microbot", "skilling"},
        enabledByDefault = false
)
@Slf4j
public class BakingPlugin extends Plugin {
    public static double version = 1.1;
    @Inject
    BakingScript bakingScript;
    @Inject
    private BakingConfig config;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BakingOverlay overlay;

    @Provides
    BakingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BakingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts = false;
        Microbot.setClient(client);
        Microbot.setClientThread(clientThread);
        Microbot.setMouse(new VirtualMouse());
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
            bakingScript.run(config);

    }

    protected void shutDown() {
        bakingScript.shutdown();
        overlayManager.remove(overlay);
    }
}
