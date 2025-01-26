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
        name = PluginDescriptor.Gage + "Lunar Tablets",
        description = "Creates Lunar teleport tablets",
        tags = {"Lunar Tablets mm Money making", "microbot"},
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
    private LunarTabletsOverlay lunartabletOverlay;

    @Inject
    LunarTabletsScript lunartabletscript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(lunartabletOverlay);
        }
        lunartabletscript.run(config);
    }

    protected void shutDown() {
        lunartabletscript.shutdown();
        overlayManager.remove(lunartabletOverlay);
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
