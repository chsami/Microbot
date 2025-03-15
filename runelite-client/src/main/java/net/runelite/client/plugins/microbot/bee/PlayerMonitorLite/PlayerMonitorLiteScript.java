package net.runelite.client.plugins.microbot.bee.PlayerMonitorLite;


import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.ClientUI;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PlayerMonitorLiteScript extends Script {
    public static String version = "1.0.0";

    @Inject
    private PlayerMonitorLiteConfig config;

    @Inject
    private PlayerMonitorLitePlugin plugin;

    private boolean logoutInitiated = false;

    public boolean run(PlayerMonitorLiteConfig config) {
        this.config = config;
        log.info("PlayerMonitorLiteScript started with alarm radius: {}", config.alarmRadius());
        //Microbot.log("PlayerMonitorLite: Script started");

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) {
                log.debug("Script not running");
                return;
            }
            if (!Microbot.isLoggedIn()) {
                log.debug("Not logged in");
                return;
            }

            try {
                // If the plugin has detected a player
                if (plugin.isPlayerDetected() && !logoutInitiated) {
                    log.info("Player detected - initiating logout");
                    Microbot.log("PlayerMonitorLite: Player detected - logging out");
                    logoutInitiated = true;
                    // Perform logout
                    logoutPlayer();
                } else if (!plugin.isPlayerDetected() && logoutInitiated) {
                    logoutInitiated = false;
                }
            } catch (Exception ex) {
                log.error("Error in script: {}", ex.getMessage());
                Microbot.log("PlayerMonitorLite error: " + ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS); // Check every 100ms

        return true;
    }

    private void logoutPlayer() {
        //log.info("Starting logout sequence");
        //Microbot.log("PlayerMonitorLite: Starting logout sequence");

        ClientUI.getClient().setEnabled(false);
        log.debug("Client disabled");

        if (this.isRunning()) {
            sleep(61, 93);
            //log.debug("Sleeping before logout");
        }

        if (this.isRunning()) {
            log.info("Calling Rs2Player.logout()");
            Rs2Player.logout();
        }

        if (this.isRunning()) {
            sleep(61, 93);
            //log.debug("Sleeping after logout");
        }

        ClientUI.getClient().setEnabled(true);
        log.debug("Client re-enabled");
        //Microbot.log("PlayerMonitorLite: Logout sequence completed");
    }

    @Override
    public void shutdown() {
        if (mainScheduledFuture != null) {
            mainScheduledFuture.cancel(true);
            //log.info("Scheduled task cancelled");
        }
        //Microbot.log("PlayerMonitorLite: Script shut down");
        super.shutdown();
    }
}