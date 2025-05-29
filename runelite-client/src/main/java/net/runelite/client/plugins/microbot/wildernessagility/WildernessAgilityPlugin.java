package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigManager;
import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

@PluginDescriptor(
    name = "Wilderness Agility",
    enabledByDefault = false
)
public class WildernessAgilityPlugin extends Plugin {
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private WildernessAgilityOverlay overlay;
    @Inject
    private WildernessAgilityConfig config;
    @Inject
    private WildernessAgilityScript script;

    @Provides
    WildernessAgilityConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(WildernessAgilityConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        Microbot.log("WildernessAgilityPlugin: startUp called");
        Microbot.log("Injected overlayManager: " + (overlayManager != null));
        Microbot.log("Injected overlay: " + (overlay != null));
        Microbot.log("Injected script: " + (script != null));
        Microbot.log("Injected config: " + (config != null));
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        overlay.setScript(script);
        overlay.setActive(true);
        script.run(config);
    }

    @Override
    protected void shutDown() throws Exception {
        Microbot.log("WildernessAgilityPlugin: shutDown called");
        if (script != null) {
            script.shutdown();
        }
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
        overlay.setActive(false);
    }
} 