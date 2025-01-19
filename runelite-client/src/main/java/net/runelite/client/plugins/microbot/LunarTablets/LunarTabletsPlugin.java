package net.runelite.client.plugins.microbot.LunarTablets;

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
        name = PluginDescriptor.Default + "Lunar Tablets",
        description = "Start on Lunar-Isle with Lunar spells. Make sure you have your staff of X equipped and enough laws, astrals, and soft clay.",
        tags = {"Lunar Tablets", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class LunarTabletsPlugin extends Plugin {
    @Inject
    private LunarTabletsConfig config;
    @Provides
    LunarTabletsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(LunarTabletsConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private LunarTabletsOverlay exampleOverlay;

    @Inject
    LunarTabletsScript exampleScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(exampleOverlay);
        }
        exampleScript.run(config);
    }

    protected void shutDown() {
        exampleScript.shutdown();
        overlayManager.remove(exampleOverlay);
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
