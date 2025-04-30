package net.runelite.client.plugins.microbot.bee.salamanders;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.Bee + "Salamanders",
        description = "Automates salamander hunting",
        tags = {"hunter", "salamanders", "skilling"},
        enabledByDefault = false
)
public class SalamanderPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private SalamanderConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private SalamanderOverlay salamanderOverlay;

    private SalamanderScript script;

    @Provides
    SalamanderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SalamanderConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        log.info("Salamander Plugin started!");
        overlayManager.add(salamanderOverlay);
        script = new SalamanderScript();
        script.run();
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Salamander Plugin stopped!");
        overlayManager.remove(salamanderOverlay);
        if (script != null) {
            script.shutdown();
        }
    }
}
