package net.runelite.client.plugins.microbot.zerozero.moonlightmoth;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.zerozero + "MoonlightMoth",
        description = "Moonlight moth catcher",
        tags = {"moonlight", "moth", "catcher", "microbot", "prayer"},
        enabledByDefault = false
)
public class MoonlightMothPlugin extends Plugin {
    static final String CONFIG = "moonlightmoth";
    public Instant scriptStartTime;
    @Inject
    public MoonlightMothScript script;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private MoonlightMothConfig config;

    @Inject
    private MoonlightMothOverlay moonlightMothOverlay;

    @Override
    protected void startUp() {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(moonlightMothOverlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        scriptStartTime = null;
        overlayManager.remove(moonlightMothOverlay);
        script.stop();
    }


    @Provides
    MoonlightMothConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MoonlightMothConfig.class);
    }

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }
}
