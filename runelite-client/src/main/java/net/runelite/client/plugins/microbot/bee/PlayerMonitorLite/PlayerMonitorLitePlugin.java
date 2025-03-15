package net.runelite.client.plugins.microbot.bee.PlayerMonitorLite;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;

import javax.inject.Inject;
import java.util.List;

@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.Bee + "Player Monitor Lite",
        tags = {"Bee", "Player Monitor", "Pvp", "Wilderness"},
        enabledByDefault = false
)

public class PlayerMonitorLitePlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private PlayerMonitorLiteConfig config;

    @Inject
    private PlayerMonitorLiteScript playerMonitorLiteScript;

    @Getter
    private boolean playerDetected = false;
    private boolean wasPlayerDetected = false;

    @Override
    protected void startUp() throws Exception {
        //log.info("PlayerMonitorLite started");
        playerMonitorLiteScript.run(config);
    }

    @Override
    protected void shutDown() throws Exception {
        //log.info("PlayerMonitorLite shutdown");
        playerMonitorLiteScript.shutdown();
    }

    @Subscribe
    public void onClientTick(ClientTick clientTick) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            if (playerDetected) {
                playerDetected = false;
                //log.debug("Not logged in, player detection disabled");
            }
            return;
        }

        // Check if there are dangerous players nearby
        playerDetected = isDangerousPlayerNearby();

        // Log state changes to avoid spamming logs
        if (playerDetected != wasPlayerDetected) {
            if (playerDetected) {
                log.info("Dangerous player detected!");
                Microbot.log("PlayerMonitorLite: Dangerous player detected!");
            } else {
                log.debug("No dangerous players detected");
            }
            wasPlayerDetected = playerDetected;
        }
    }

    /**
     * Determines if there's a dangerous player nearby
     * @return true if a player who can attack us is within the alarm radius
     */
    private boolean isDangerousPlayerNearby() {
        // Get local player's position
        LocalPoint currentPosition = client.getLocalPlayer().getLocalLocation();

        // Get players in combat level range from the Rs2Player utility class
        List<Rs2PlayerModel> threatPlayers = Rs2Player.getPlayersInCombatLevelRange();

        if (!threatPlayers.isEmpty()) {
            //log.debug("Found {} players in combat level range", threatPlayers.size());
        }

        // Check if any of them are within the configured alarm radius
        for (Rs2PlayerModel playerModel : threatPlayers) {
            LocalPoint playerLocation = playerModel.getPlayer().getLocalLocation();
            float distanceInTiles = playerLocation.distanceTo(currentPosition) / 128f;

            if (distanceInTiles <= config.alarmRadius()) {
                log.debug("Player {} is within alarm radius: {} tiles",
                        playerModel.getPlayer().getName(), distanceInTiles);
                return true;
            }
        }

        return false;
    }

    /**
     * Used by the script to check if a player is detected
     * @return true if a dangerous player is detected
     */
    public boolean isPlayerDetected() {
        return playerDetected;
    }

    @Provides
    PlayerMonitorLiteConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PlayerMonitorLiteConfig.class);
    }
}