package net.runelite.client.plugins.microbot.TaF.CalcifiedRockMiner;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.DecorativeObject;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Calcified Rock Miner",
        description = "Calcified Rock Miner",
        tags = {"prayer", "rock", "calcified", "taf", "microbot"},
        enabledByDefault = false
)
public class CalcifiedRockMinerPlugin extends Plugin {

    private Instant scriptStartTime;
    @Inject
    private CalcifiedRockMinerConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private CalcifiedRockMinerOverlay calcifiedRockMinerOverlay;
    @Inject
    private CalcifiedRockMinerScript calcifiedRockMinerScript;

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(calcifiedRockMinerOverlay);
        }
        calcifiedRockMinerScript.run(config);
    }

    @Override
    protected void shutDown() {
        calcifiedRockMinerScript.shutdown();
        overlayManager.remove(calcifiedRockMinerOverlay);
    }

    @Provides
    CalcifiedRockMinerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CalcifiedRockMinerConfig.class);
    }
}
