package net.runelite.client.plugins.microbot.magic.orbcharger.scripts;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.magic.orbcharger.OrbChargerPlugin;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PlayerDetectionScript extends Script {

    private final OrbChargerPlugin plugin;
    private final int[] airStaves = {
            ItemID.STAFF_OF_AIR,
            ItemID.AIR_BATTLESTAFF,
            ItemID.MYSTIC_AIR_STAFF,
            ItemID.DUST_BATTLESTAFF,
    };

    private final List<Rs2PlayerModel> detectedDangerousPlayers = new CopyOnWriteArrayList<>();

    @Inject
    public PlayerDetectionScript(OrbChargerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;

                if (Rs2Pvp.isInWilderness()) {
                    List<Rs2PlayerModel> newDangerousPlayers = Rs2Player.getPlayersInCombatLevelRange().stream()
                            .filter(player -> !Rs2Player.hasPlayerEquippedItem(player, airStaves))
                            .filter(player -> !detectedDangerousPlayers.contains(player))
                            .collect(Collectors.toList());

                    if (!newDangerousPlayers.isEmpty()) {
                        Microbot.log("Detected " + newDangerousPlayers.size() + " dangerous players.");
                        System.out.println("Detected " + newDangerousPlayers.size() + " dangerous players.");
                        detectedDangerousPlayers.addAll(newDangerousPlayers);
                        plugin.setDangerousPlayers(List.copyOf(detectedDangerousPlayers));
                    }
                } else if (!detectedDangerousPlayers.isEmpty()) {
                    detectedDangerousPlayers.clear();
                    plugin.setDangerousPlayers(List.of());
                }
            } catch (Exception ex) {
                System.out.println("Error in PlayerDetectionScript: " + ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        detectedDangerousPlayers.clear();
        plugin.setDangerousPlayers(List.of());
        super.shutdown();
    }
}