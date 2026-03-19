package net.runelite.client.plugins.microbot.commandcenter.scripts.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.MicrobotPlugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
    name = "CC Combat Trainer",
    description = "Train combat on monsters with eating, looting, bone burying",
    tags = {"microbot", "combat", "f2p", "commandcenter"},
    enabledByDefault = false
)
@Slf4j
public class CCCombatPlugin extends MicrobotPlugin {
    @Inject private CCCombatScript script;
    @Inject private CCCombatConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private CCCombatOverlay overlay;

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        script.run(config, "CC Combat Trainer");
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
