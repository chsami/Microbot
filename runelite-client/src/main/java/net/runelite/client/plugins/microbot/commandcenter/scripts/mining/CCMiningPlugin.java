package net.runelite.client.plugins.microbot.commandcenter.scripts.mining;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
    name = "CC Miner",
    description = "Mine rocks, bank or drop ore",
    tags = {"microbot", "mining", "f2p", "commandcenter"},
    enabledByDefault = false
)
@Slf4j
public class CCMiningPlugin extends Plugin {
    @Inject private CCMiningScript script;
    @Inject private CCMiningConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private CCMiningOverlay overlay;

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        script.run(config, "CC Miner");
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
