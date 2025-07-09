package net.runelite.client.plugins.microbot.fishing.minnows;

import com.google.inject.Provides;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + "Auto Minnows",
        description = "Microbot minnows plugin",
        tags = {"minnows", "microbot"},
        enabledByDefault = false
)
public class MinnowsPlugin extends Plugin {
    @Inject
    MinnowsScript minnowsScript;
    @Inject
    private MinnowsConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private MinnowsOverlay minnowsOverlay;

    @Provides
    MinnowsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MinnowsConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(minnowsOverlay);
        }
        minnowsScript.run();
    }

    protected void shutDown() {
        overlayManager.remove(minnowsOverlay);
        minnowsScript.shutdown();
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        minnowsScript.onGameTick();

    }

}