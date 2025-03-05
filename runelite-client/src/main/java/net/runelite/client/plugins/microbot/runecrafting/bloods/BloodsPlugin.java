package net.runelite.client.plugins.microbot.runecrafting.bloods;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginDescriptor.FrostyX + " True Bloods",
        description = "Craft Blood Runes at the true altar",
        enabledByDefault = false,
        tags = {"bloods", "runecrafting", "rc", "frosty"}
)
public class BloodsPlugin extends Plugin {

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private BloodsOverlay bloodsOverlay;

    @Inject
    private BloodsScript bloodsScript;

    @Inject
    private BloodsConfig bloodsConfig;

    @Provides
    BloodsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BloodsConfig.class);
    }

    @Override
    protected void startUp() {
        Microbot.log("Starting Blood Rune Crafting Plugin...");

        overlayManager.add(bloodsOverlay); // Add overlay

        if (bloodsScript != null) {
            bloodsScript.run(bloodsConfig);
            Microbot.log("BloodsScript started successfully!");
        } else {
            Microbot.log("Error: BloodsScript failed to start.");
        }
    }

    @Override
    protected void shutDown() {
        Microbot.log("Stopping Blood Rune Crafting Plugin...");

        if (bloodsScript != null) {
            bloodsScript.shutdown();
        }

        if (overlayManager != null && bloodsOverlay != null) {
            overlayManager.remove(bloodsOverlay);
        }

        Microbot.log("Bloods Plugin Stopped.");
    }
}
