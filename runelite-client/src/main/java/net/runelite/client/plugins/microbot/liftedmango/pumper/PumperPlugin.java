package net.runelite.client.plugins.microbot.liftedmango.pumper;

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
        name = PluginDescriptor.LiftedMango + "Pumper",
        description = "LiftedMango's Pumper",
        tags = {"liftedmango", "pure", "strength", "str", "pump", "blast furnace"},
        enabledByDefault = false
)
@Slf4j
public class PumperPlugin extends Plugin {
    @Inject
    private PumperConfig config;
    @Provides
    PumperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PumperConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PumperOverlay pumperOverlay;

    @Inject
    PumperScript pumperScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(pumperOverlay);
        }
        pumperScript.run(config);
    }

    protected void shutDown() {
        pumperScript.shutdown();
        overlayManager.remove(pumperOverlay);
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
