package net.runelite.client.plugins.microbot.jens19963.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Example Plugin2",
        description = "Performance test for GameObject composition retrieval",
        tags = {"performance", "microbot", "test", "gameobject"},
        enabledByDefault = false
)
@Slf4j
public class ExamplePlugin extends Plugin {
    @Inject
    ExampleScript exampleScript;
    @Inject
    ExampleScriptOverlay exampleScriptOverlay;
    @Inject
    OverlayManager overlayManager;


    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(exampleScriptOverlay);
        exampleScript.run();
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(exampleScriptOverlay);
        exampleScript.shutdown();
    }

    // on settings change
    @Subscribe
    public void onConfigChanged(final ConfigChanged event) {
    }
}