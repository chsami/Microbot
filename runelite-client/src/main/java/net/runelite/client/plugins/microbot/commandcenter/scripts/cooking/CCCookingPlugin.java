package net.runelite.client.plugins.microbot.commandcenter.scripts.cooking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
    name = "CC Cooker",
    description = "Cook food on range or fire",
    tags = {"microbot", "cooking", "f2p", "commandcenter"},
    enabledByDefault = false
)
@Slf4j
public class CCCookingPlugin extends Plugin {
    @Inject private CCCookingScript script;
    @Inject private CCCookingConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private CCCookingOverlay overlay;

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        script.run(config, "CC Cooker");
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
