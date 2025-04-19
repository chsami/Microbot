package net.runelite.client.plugins.microbot.TaF.TzhaarVenatorBow;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.TaF.DemonicGorillaKiller.DemonicGorillaConfig;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Tzhaar Venator",
        description = "Automatically kills Tzhaars with the Venator Bow",
        tags = {"Tzhaar", "Venator", "bow", "range", "xp", "microbot"},
        enabledByDefault = false
)
public class TzhaarVenatorBowPlugin extends Plugin {

    private Instant scriptStartTime;
    @Inject
    private TzHaarVenatorBowConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private TzhaarVenatorBowOverlay tzhaarVenatorBowOverlay;
    @Inject
    private TzhaarVenatorBowScript tzHaarVenator;

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(tzhaarVenatorBowOverlay);
        }
        tzHaarVenator.run(config);
    }

    @Override
    protected void shutDown() {
        tzHaarVenator.shutdown();
        overlayManager.remove(tzhaarVenatorBowOverlay);
    }

    @Provides
    TzHaarVenatorBowConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TzHaarVenatorBowConfig.class);
    }
}
