package net.runelite.client.plugins.microbot.bee.monkscript;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Bee + "Monk Killer Plugin",
        description = "Kills monks for training defense pure",
        tags = {"monks", "combat", "training"},
        enabledByDefault = false
)

@Slf4j
public class MonkKillerPlugin extends Plugin {

    @Inject
    private MonkKillerConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MonkKillerOverlay monkKillerOverlay;

    @Inject
    private MonkKillerScript monkKillerScript;  // Inject MonkKillerScript

    @Inject
    private Client client; // Inject the client instance

    public MonkKillerPlugin() {
        // No-argument constructor for the framework.
    }

    public MonkKillerPlugin(MonkKillerScript monkKillerScript) {
        this.monkKillerScript = monkKillerScript;
    }

    @Provides
    MonkKillerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MonkKillerConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {

        Microbot.pauseAllScripts = false;

        if (overlayManager != null) {
            overlayManager.add(monkKillerOverlay);
        }

        // Start the MonkKillerScript
        monkKillerScript.run(config);

    }

    @Override
    protected void shutDown() {
        monkKillerScript.shutdown();  // Shutdown the script
        overlayManager.remove(monkKillerOverlay);
    }
}
