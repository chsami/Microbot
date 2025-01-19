package net.runelite.client.plugins.microbot.autoCannoner;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.autoCannoner.AutoCannonerScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.ChillX + "Auto Cannoner",
        description = "Automatically keeps your cannon going",
        tags = {"combat", "cannon", "ChillX"},
        enabledByDefault = false
)
@Slf4j
public class AutoCannonerPlugin extends Plugin {
    @Inject
    private AutoCannonerConfig config;
    @Provides
    AutoCannonerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AutoCannonerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoCannonerOverlay autoCannonerOverlay;

    @Inject
    AutoCannonerScript cannonerScript;

    @Override
    protected void startUp() throws AWTException
    {

        if (overlayManager != null)
        {
            overlayManager.add(autoCannonerOverlay);
        }
        cannonerScript.run(config);
    }

    protected void shutDown()
    {
        cannonerScript.shutdown();
        overlayManager.remove(autoCannonerOverlay);
    }
    int ticks = 10;
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (ticks > 0)
        {
            ticks--;
        }
        else
        {
            ticks = 10;
        }
    }
}
