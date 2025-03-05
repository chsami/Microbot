package net.runelite.client.plugins.microbot.runecrafting.rcx;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.Getter;
import lombok.Setter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = PluginDescriptor.FrostyX + "Mind",
        description = "Mind RC",
        tags = {"runecrafting", "automation", "microbot"},
        enabledByDefault = false
)
public class RcxPlugin extends Plugin {

    @Inject
    private RcxConfig config;

    @Inject
    private RcxScript rcxScript;

    @Inject
    private RcxOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Setter
    @Getter
    private RcxState currentState;

    @Provides
    RcxConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(RcxConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        rcxScript.run(config); // Start the script
        overlayManager.add(overlay); // Add overlay
    }

    @Override
    protected void shutDown() throws Exception {
        rcxScript.shutdown(); // Stop the script
        overlayManager.remove(overlay); // Remove overlay
    }
}