package net.runelite.client.plugins.microbot.mining.motherloadmine;

import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ObjectID;
import net.runelite.api.WallObject;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMStatus;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "MotherlodeMine",
        description = "A bot that mines paydirt in the motherlode mine",
        tags = {"paydirt", "mine", "mining", "minigame", "motherlode"},
        enabledByDefault = false
)
@Slf4j
public class MotherloadMinePlugin extends Plugin {
    @Inject
    private MotherloadMineConfig config;
    @Inject
    private MotherloadMineOverlay motherloadMineOverlay;
    @Inject
    private MotherloadMineScript motherloadMineScript;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private Notifier notifier;
    @Inject
    private OverlayManager overlayManager;

    private Instant scriptStartTime;

    @Provides
    MotherloadMineConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MotherloadMineConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts = false;
        Microbot.setClient(client);
        Microbot.setClientThread(clientThread);
        Microbot.setNotifier(notifier);
        Microbot.setMouse(new VirtualMouse());
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        // Everyone makes mistakes
        Rs2AntibanSettings.simulateMistakes = true;

        scriptStartTime = Instant.now();
        overlayManager.add(motherloadMineOverlay);
        motherloadMineScript.run(config);
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event) {
        WallObject wallObject = event.getWallObject();
        try {
            if (wallObject == null || MotherloadMineScript.oreVein == null)
                return;
            if (MotherloadMineScript.status == MLMStatus.MINING && (wallObject.getId() == ObjectID.DEPLETED_VEIN_26665 || wallObject.getId() == ObjectID.DEPLETED_VEIN_26666 || wallObject.getId() == ObjectID.DEPLETED_VEIN_26667 || wallObject.getId() == ObjectID.DEPLETED_VEIN_26668)) {
                if (wallObject.getWorldLocation().equals(MotherloadMineScript.oreVein.getWorldLocation())) {
                    MotherloadMineScript.oreVein = null;
                }
            }
        } catch (Exception e) {
            // e.printStackTrace();
            System.err.println(e.getMessage());
        }

    }

    protected String getTimeRunning() {
        return scriptStartTime != null ?
            TimeUtils.getFormattedDurationBetween(
                scriptStartTime, Instant.now()
            ) :"";
    }

    protected void shutDown() {
        Microbot.pauseAllScripts = true;
        scriptStartTime = null;
        motherloadMineScript.shutdown();
        overlayManager.remove(motherloadMineOverlay);
        Rs2Antiban.resetAntibanSettings();
    }
}
