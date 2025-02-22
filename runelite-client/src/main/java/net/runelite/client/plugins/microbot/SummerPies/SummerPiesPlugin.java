package net.runelite.client.plugins.microbot.SummerPies;

import com.google.inject.Provides;
import java.awt.AWTException;
import javax.inject.Inject;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@PluginDescriptor(
        name = PluginDescriptor.LT + "Summer Pies",
        description = "Summer Pies Plugin",
        tags = {"Summer Pies", "microbot"},
        enabledByDefault = false
)
public class SummerPiesPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger(SummerPiesPlugin.class);
    @Inject
    private SummerPiesConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private SummerPiesOverlay exampleOverlay;
    @Inject
    SummerPiesScript SummerPiesScript;
    int ticks = 10;

    public SummerPiesPlugin() {
    }

    @Provides
    SummerPiesConfig provideConfig(ConfigManager configManager) {
        return (SummerPiesConfig) configManager.getConfig(SummerPiesConfig.class);
    }

    protected void startUp() throws AWTException {
        if (this.overlayManager != null) {
            this.overlayManager.add(this.exampleOverlay);
        }

        this.SummerPiesScript.run();
    }

    protected void shutDown() {
        this.SummerPiesScript.shutdown();
        this.overlayManager.remove(this.exampleOverlay);
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (this.ticks > 0) {
            --this.ticks;
        } else {
            this.ticks = 10;
        }

    }
}

