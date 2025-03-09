package net.runelite.client.plugins.microbot.breakhandler;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "BreakHandler",
        description = "Microbot breakhandler",
        tags = {"break", "microbot", "breakhandler"},
        enabledByDefault = false
)
@Slf4j
public class BreakHandlerPlugin extends Plugin {
    @Inject
    BreakHandlerScript breakHandlerScript;
    @Inject
    private BreakHandlerConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BreakHandlerOverlay breakHandlerOverlay;

    @Provides
    BreakHandlerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BreakHandlerConfig.class);
    }

    private boolean hideOverlay;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(breakHandlerOverlay);
        }
        hideOverlay = config.isHideOverlay();
        toggleOverlay(hideOverlay);
        breakHandlerScript.run(config);
    }

    private void toggleOverlay(boolean hideOverlay) {
        if (overlayManager != null) {
            boolean hasOverlay = overlayManager.anyMatch(ov -> ov.getName().equalsIgnoreCase(BreakHandlerOverlay.class.getSimpleName()));

            if (hideOverlay) {
                if(!hasOverlay) return;

                overlayManager.remove(breakHandlerOverlay);
            } else {
                if (hasOverlay) return;

                overlayManager.add(breakHandlerOverlay);
            }
        }
    }

    protected void shutDown() {
        breakHandlerScript.shutdown();
        overlayManager.remove(breakHandlerOverlay);
    }

    // on settings change
    @Subscribe
    public void onConfigChanged(final ConfigChanged event) {
        if (event.getGroup().equals(BreakHandlerConfig.configGroup)) {
            if (event.getKey().equals("UsePlaySchedule")) {
                breakHandlerScript.reset();
            }
            
            if (event.getKey().equals("breakNow")) {
                boolean breakNowValue = config.breakNow();
                log.debug("Break Now toggled: {}", breakNowValue);
            }

            if (event.getKey().equals(BreakHandlerConfig.hideOverlay)) {
                hideOverlay = config.isHideOverlay();
                toggleOverlay(hideOverlay);
            }
        }
    }
}