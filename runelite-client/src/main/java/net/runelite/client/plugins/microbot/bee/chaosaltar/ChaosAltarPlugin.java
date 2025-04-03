package net.runelite.client.plugins.microbot.bee.chaosaltar;

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
        name = PluginDescriptor.Bee + "Chaos Altar",
        description = "Automates bone offering at the Chaos Altar",
        tags = {"prayer", "bones", "altar"},
        enabledByDefault = false
)
@Slf4j
public class ChaosAltarPlugin extends Plugin {
    @Inject
    private ChaosAltarScript chaosAltarScript;
    @Inject
    private ChaosAltarConfig config;
    @Provides
    ChaosAltarConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChaosAltarConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    ChaosAltarOverlay chaosAltarOverlay;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(chaosAltarOverlay);
        }
        chaosAltarScript.run(config);
    }

    protected void shutDown() {
        chaosAltarScript.shutdown();
        overlayManager.remove(chaosAltarOverlay);
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
