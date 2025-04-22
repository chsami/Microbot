package net.runelite.client.plugins.microbot.antibanpreloader;

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
        name = PluginDescriptor.Logiko + "Antiban Preloader",
        description = "Forces Antiban settings",
        tags = {"Logiko's", "Antiban", "Preloader"},
        enabledByDefault = false
)
@Slf4j
public class AntibanPresetPlugin extends Plugin {
    @Inject
    private AntibanPresetConfig config;
    @Provides
    AntibanPresetConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AntibanPresetConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AntibanPresetOverlay antibanPresetOverlay;

    @Inject
    AntibanPresetScript antibanPresetScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(antibanPresetOverlay);
        }
        antibanPresetScript.run(config);
    }

    protected void shutDown() {
        antibanPresetScript.shutdown();
        overlayManager.remove(antibanPresetOverlay);
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
