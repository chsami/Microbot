package net.runelite.client.plugins.microbot.flipper;

import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.config.ConfigManager;
import com.google.inject.Provides;

import java.awt.AWTException;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.plugins.Plugin;

@PluginDescriptor(
        name = PluginDescriptor.Choken + "Flipper",
        description = "Flipping copilot automation",
        tags = {"flip", "ge", "grand", "exchange", "automation"},
        enabledByDefault = false
)
public class FlipperPlugin extends Plugin {
    
    @Inject
    private Client client;
    @Inject
    private FlipperScript flipperScript;
    @Inject
    private FlipperConfig config;

    @Provides
    FlipperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FlipperConfig.class);
    }

    @Override
    protected void startUp() throws AWTException{
        flipperScript.run();
    }

    @Override
    protected void shutDown() {
        flipperScript.state = State.GOING_TO_GE;
        flipperScript.shutdown();
    }
}