package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;

@PluginDescriptor(
        name = PluginDescriptor.Default + "GE Flipper",
        description = "Microbot GE flipping plugin",
        tags = {"ge", "flipping", "microbot"},
        enabledByDefault = false
)
public class GeFlipperPlugin extends Plugin {
    @Inject
    private GeFlipperConfig config;
    @Provides
    GeFlipperConfig provideConfig(ConfigManager configManager) { return configManager.getConfig(GeFlipperConfig.class); }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GeFlipperOverlay overlay;
    @Inject
    private GeFlipperScript script;

    @Getter
    private int profit;
    @Getter
    private Instant startTime;

    @Override
    protected void startUp() throws AWTException {
        Microbot.status = "Starting";
        startTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(this, config);
    }

    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
        startTime = null;
        profit = 0;
        Microbot.status = "IDLE";
    }

    void addProfit(int gp) { profit += gp; }

    int getProfitPerHour() {
        if (startTime == null) return 0;
        long seconds = TimeUtils.getDurationInSeconds(startTime, Instant.now());
        if (seconds == 0) return 0;
        return (int) (profit * 3600L / seconds);
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        script.onGameTick();
    }
}
