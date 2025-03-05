package net.runelite.client.plugins.microbot.runecrafting.bloodx;

import com.google.inject.Provides;
import net.runelite.api.ObjectID;
import net.runelite.api.WallObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.FrostyX + "Bloods",
        description = "Craft Bloods at the true altar",
        enabledByDefault = false,
        tags = {"bloods", "rc", "frosty"}
)


public class BloodxPlugin extends Plugin {
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BloodxOverlay bloodxOverlay;
    @Inject
    private BloodxScript bloodxScript;
    @Inject
    private BloodxConfig bloodxConfig;


    @Provides
    BloodxConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BloodxConfig.class);
    }



    @Override
    protected void startUp() throws AWTException {
        Microbot.log("Starting Bloodx Plugin...");
        overlayManager.add(bloodxOverlay);

        if (bloodxScript != null) {
            bloodxScript.run(bloodxConfig);
            Microbot.log("Bloodx Script Started Successfully!");
        } else {
            Microbot.log("ERROR: BloodxScript is null! Plugin not loaded.");
        }
    }


    @Override
    protected void shutDown() throws Exception {
        bloodxScript.shutdown();
        overlayManager.remove(bloodxOverlay);
    }
}
