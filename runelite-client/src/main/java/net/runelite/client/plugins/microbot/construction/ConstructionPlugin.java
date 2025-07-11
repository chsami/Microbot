package net.runelite.client.plugins.microbot.construction;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Construction",
        description = "Microbot construction plugin",
        tags = {"skilling", "microbot", "construction"},
        enabledByDefault = false
)
@Slf4j
public class ConstructionPlugin extends Plugin {

    @Inject
    private ConstructionConfig config;

    @Provides
    ConstructionConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ConstructionConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ConstructionOverlay constructionOverlay;

    public ConstructionScript constructionScript = new ConstructionScript();

    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(constructionOverlay);
        }
        constructionScript.run(config);
    }

    protected void shutDown() throws Exception {
        constructionScript.shutdown();
        overlayManager.remove(constructionOverlay);
        super.shutDown();
    }
}
