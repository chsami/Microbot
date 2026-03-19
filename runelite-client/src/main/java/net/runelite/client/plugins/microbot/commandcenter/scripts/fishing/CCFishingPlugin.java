package net.runelite.client.plugins.microbot.commandcenter.scripts.fishing;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.MicrobotPlugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
    name = "CC Fisher",
    description = "Fish at spots, bank or drop fish",
    tags = {"microbot", "fishing", "f2p", "commandcenter"},
    enabledByDefault = false
)
@Slf4j
public class CCFishingPlugin extends MicrobotPlugin {
    @Inject private CCFishingScript script;
    @Inject private CCFishingConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private CCFishingOverlay overlay;

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        script.run(config, "CC Fisher");
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
