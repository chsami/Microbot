package net.runelite.client.plugins.microbot.GeoffPlugins.construction2;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.GeoffPlugins.construction2.enums.Construction2State;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Geoff + "Construction 2",
        description = "Geoff's Microbot construction plugin with added new bits.",
        tags = {"skilling", "microbot", "construction"},
        enabledByDefault = false
)
@Slf4j
public class Construction2Plugin extends Plugin {

    @Inject
    private Construction2Config config;

    @Provides
    Construction2Config provideConfig(ConfigManager configManager) {
        return configManager.getConfig(Construction2Config.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private Construction2Overlay construction2Overlay;

    private final Construction2Script construction2Script = new Construction2Script();

    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(construction2Overlay);
        }
        construction2Script.run(config);
    }

    @Override
    protected void shutDown() {
        construction2Script.shutdown();
        overlayManager.remove(construction2Overlay);
    }

    public Construction2State getState() {
        return construction2Script.getState();
    }
}
