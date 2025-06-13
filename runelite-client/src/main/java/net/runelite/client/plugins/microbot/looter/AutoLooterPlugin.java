package net.runelite.client.plugins.microbot.looter;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.InventoryID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.looter.enums.LooterActivity;
import net.runelite.client.plugins.microbot.looter.scripts.DefaultScript;
import net.runelite.client.plugins.microbot.looter.scripts.FlaxScript;
import net.runelite.client.plugins.microbot.looter.scripts.NatureRuneChestScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Auto Looter",
        description = "Microbot looter plugin",
        tags = {"looter", "microbot"},
        enabledByDefault = false
)
public class AutoLooterPlugin extends Plugin {
    public static double version = 1.1;

    @Inject
    private DefaultScript defaultScript;

    @Inject
    private FlaxScript flaxScript;

    @Inject
    private NatureRuneChestScript natureRuneChestScript;

    @Inject
    private AutoLooterConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AutoLooterOverlay autoLooterOverlay;

    @Provides
    AutoLooterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoLooterConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(autoLooterOverlay);
        }

        try {
            switch (config.looterActivity()) {
                case DEFAULT:
                    defaultScript.run(config);
                    break;
                case FLAX:
                    flaxScript.run(config);
                    break;
                case NATURE_RUNE_CHEST:
                    natureRuneChestScript.run(config);
                    break;
            }
        } catch (Exception e) {
            System.out.println("Error starting looter: " + e.getMessage());
        }
    }

    @Override
    protected void shutDown() {
        try {
            defaultScript.shutdown();
            flaxScript.shutdown();
            natureRuneChestScript.shutdown();
            
            if (overlayManager != null) {
                overlayManager.remove(autoLooterOverlay);
            }
        } catch (Exception e) {
            System.out.println("Error shutting down looter: " + e.getMessage());
        }
    }
}
