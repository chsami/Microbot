package net.runelite.client.plugins.microbot.cardewsPlugins.AIOCamdozaal;

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
        name = PluginDescriptor.Cardew + "AIO Camdozaal",
        description = "Cardews AIO Camdozaal plugin",
        tags = {"aio", "microbot", "camdozaal", "cd", "cardew"},
        enabledByDefault = false
)
@Slf4j
public class AIOCamdozPlugin extends Plugin {
    @Inject
    private AIOCamdozConfig config;
    @Provides
    AIOCamdozConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AIOCamdozConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AIOCamdozOverlay aioCamdozOverlay;

    @Inject
    AIOCamdozScript aioCamdozScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(aioCamdozOverlay);
        }
        aioCamdozScript.run(config);
    }

    protected void shutDown() {
        aioCamdozScript.shutdown();
        overlayManager.remove(aioCamdozOverlay);
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
