package net.runelite.client.plugins.microbot.commandcenter.scripts.woodcutting;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.MicrobotPlugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
    name = "CC Woodcutter",
    description = "Chop trees, bank or drop logs",
    tags = {"microbot", "woodcutting", "f2p", "commandcenter"},
    enabledByDefault = false
)
@Slf4j
public class CCWoodcuttingPlugin extends MicrobotPlugin {
    @Inject private CCWoodcuttingScript script;
    @Inject private CCWoodcuttingConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private CCWoodcuttingOverlay overlay;

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        script.run(config, "CC Woodcutter");
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
