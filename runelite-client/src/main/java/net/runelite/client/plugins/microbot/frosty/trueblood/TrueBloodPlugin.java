
package net.runelite.client.plugins.microbot.frosty.trueblood;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.FrostyX + "TRUE BLOOD",
        description = "A plugin to automate Blood Rune crafting",
        tags = {"blood", "runecrafting", "automation"},
        enabledByDefault = false
)

@Slf4j
public class TrueBloodPlugin extends Plugin {
    @Inject
    private TrueBloodConfig config;
    @Provides
    TrueBloodConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TrueBloodConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    TrueBloodScript exampleScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
        }
        exampleScript.run(config);
    }

    protected void shutDown() {
        exampleScript.shutdown();

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