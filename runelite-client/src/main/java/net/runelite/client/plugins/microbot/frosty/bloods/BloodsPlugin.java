package net.runelite.client.plugins.microbot.frosty.bloods;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;


@PluginDescriptor(
        name = PluginDescriptor.FrostyX + "Bloods",
        description = "A plugin to automate Blood Rune crafting",
        tags = {"blood", "ruc", "rune", "Frosty"},
        enabledByDefault = false
)
public class BloodsPlugin extends Plugin {
    @Inject
    private BloodsConfig config;
    @Provides
    BloodsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BloodsConfig.class);
    }
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BloodsOverlay bloodsOverlay;
    @Inject
    private BloodsScript bloodsScript;
    public static String version = "1.0.0";
    @Getter
    public Instant startTime;
    @Getter
    private boolean toggleRunesCrafted;

    private int bloodRunesCrafted = 0;


    @Override
    protected void startUp() throws AWTException {
        startTime = Instant.now();
        if(overlayManager != null) {
            overlayManager.add(bloodsOverlay);
        }
        bloodsScript.run();
    }
    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(bloodsOverlay);
        bloodsScript.shutdown();
    }

}
