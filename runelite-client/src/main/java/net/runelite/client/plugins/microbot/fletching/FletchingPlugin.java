package net.runelite.client.plugins.microbot.fletching;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Fletcher",
        description = "Microbot fletching plugin",
        tags = {"fletching", "microbot", "skills"},
        enabledByDefault = false
)
@Slf4j
public class FletchingPlugin extends Plugin {
    @Inject
    private FletchingConfig config;

    @Provides
    FletchingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FletchingConfig.class);
    }
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FletchingOverlay fletchingOverlay;

    FletchingScript fletchingScript;


    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(fletchingOverlay);
        }
        fletchingScript = new FletchingScript();
        fletchingScript.run(config);
    }

    protected void shutDown() {
        fletchingScript.shutdown();
        overlayManager.remove(fletchingOverlay);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        fletchingScript.onWidgetLoaded(event);
    }
}
