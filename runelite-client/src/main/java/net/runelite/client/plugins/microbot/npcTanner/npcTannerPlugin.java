package net.runelite.client.plugins.microbot.npcTanner;

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
        name = PluginDescriptor.Gage + "NPC Tanner",
        description = "Tans any hide the bank has",
        tags = {"NPC Tanner", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class npcTannerPlugin extends Plugin {
    @Inject
    private net.runelite.client.plugins.microbot.npcTanner.npcTannerConfig config;
    @Provides
    net.runelite.client.plugins.microbot.npcTanner.npcTannerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(npcTannerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private npcTannerOverlay TanOverlay;

    @Inject
    npcTannerScript TannerScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(TanOverlay);
        }
        TannerScript.run(config);
    }

    protected void shutDown() {
        TannerScript.shutdown();
        overlayManager.remove(TanOverlay);
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
