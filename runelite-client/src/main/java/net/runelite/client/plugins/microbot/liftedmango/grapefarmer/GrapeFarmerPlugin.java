package net.runelite.client.plugins.microbot.liftedmango.grapefarmer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.LiftedMango + "Grape farmer",
        description = "Grape farmer",
        tags = {"liftedmango", "skilling", "farming", "money making"},
        enabledByDefault = false
)
@Slf4j
public class GrapeFarmerPlugin extends Plugin {
    @Inject
    private GrapeFarmerConfig config;
    @Provides
    GrapeFarmerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GrapeFarmerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GrapeFarmerOverlay grapeFarmerOverlay;

    @Inject
    GrapeFarmerScript grapeFarmerScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(grapeFarmerOverlay);
        }
        grapeFarmerScript.run(config);
    }

    protected void shutDown() {
        grapeFarmerScript.shutdown();
        overlayManager.remove(grapeFarmerOverlay);
    }
    int ticks = 10;
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        //System.out.println(getName().chars().mapToObj(i -> (char)(i + 3)).map(String::valueOf).collect(Collectors.joining()));

        if (ticks > 0) {
            ticks--;
        } else {
            ticks = 10;
        }

    }

}
