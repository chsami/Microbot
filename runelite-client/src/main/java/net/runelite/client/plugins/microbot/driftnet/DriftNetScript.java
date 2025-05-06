package net.runelite.client.plugins.microbot.driftnet;

import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.Microbot.log;

public class DriftNetScript extends Script {

    // Script version
    public static final double VERSION = 1.1;

    private static final int MAX_FETCH_ATTEMPTS = 5;
    private static final Logger log = LoggerFactory.getLogger(DriftNetScript.class);

    // Current number of attempts to fetch nets
    private int netFetchAttempts = 0;


    public boolean run(DriftNetConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                // 1 validations before continuing
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (!super.run()) {
                    return;
                }

                // 2) Stop script if too many attempts were made to fetch nets
                if (netFetchAttempts > MAX_FETCH_ATTEMPTS) {
                    log("Script shutdown, no nets found");
                    shutdown();
                    return;
                }

                // 3) Ensure we have drift nets in inventory; if not, try to fetch
                if (!Rs2Inventory.hasItem(ItemID.DRIFT_NET)) {
                    Microbot.log("No nets in inventory");
                    fetchNetsFromAnnette();
                    return;
                }

                // Reset attempt counter if we successfully have nets
                if (netFetchAttempts > 0) {
                    netFetchAttempts = 0;
                }

                // 4) Handle any nets that are either FULL or UNSET
                if (DriftNetPlugin.getNETS().stream().anyMatch(x ->
                        x.getStatus() == DriftNetStatus.FULL ||
                                x.getStatus() == DriftNetStatus.UNSET)) {
                    processNets(config);
                    return;
                }

                // 5) If no nets require attention, chase fish
                chaseNearbyFish(DriftNetPlugin.getFish());

            } catch (Exception ex) {
                // You might want more robust logging here
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    /**
     * Attempts to fetch drift nets from Annette.
     * Increments netFetchAttempts if no nets were found
     */
    private void fetchNetsFromAnnette() {
        final int maxWeight = 25; // https://oldschool.runescape.wiki/w/Drift_net_fishing
        var maxDriftnets = maxWeight - Microbot.getClient().getWeight() - 1; // Driftnets are 1kg each; doing - 1 to be safe
        Rs2GameObject.interact(ObjectID.ANNETTE, "Nets");
        sleepUntil(() -> Rs2Widget.getWidget(20250629) != null);
        Rs2Widget.clickWidgetFast(Rs2Widget.getWidget(20250629), 0, 3);
        sleepGaussian(1500, 300);
        Rs2Keyboard.typeString(String.valueOf(maxDriftnets));
        sleepGaussian(1500, 300);
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        sleepGaussian(1500, 300);
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);

        netFetchAttempts++;
    }

    /**
     * Processes all known drift nets. If a net is FULL, we either bank fish
     * or just loot them depending on the config. If a net is UNSET, we set it.
     */
    private void processNets(DriftNetConfig config) {
        for (DriftNet net : DriftNetPlugin.getNETS()) {

            final Shape netShape = Microbot.getClientThread().runOnClientThreadOptional(net.getNet()::getConvexHull)
                    .orElse(null);

            if (netShape == null) {
                continue;
            }

            switch (net.getStatus()) {
                case FULL:
                    handleFullNet(net, config);
                    return;
                case UNSET:
                    handleUnsetNet(net);
                default:

            }
        }

    }

    /**
     * Handles a FULL net. If bankFish() is true in config, bank the fish.
     * Otherwise, simply loot the net.
     */
    private void handleFullNet(DriftNet net, DriftNetConfig config) {

        // 1) Interact with the net
        Rs2GameObject.interact(net.getNet());

        if (config.bankFish()) {

            boolean initialWidgetLoaded = sleepUntil(
                    () -> Rs2Widget.getWidget(39780359) != null,
                    10000
            );
            Rs2Widget.clickWidget(39780359);
            sleepUntil(() -> Rs2Widget.isWidgetVisible(39780365));
            Rs2Widget.clickWidget(39780365);


        }
    }

    /**
     * Handles an UNSET net by interacting with it once to set it.
     */
    private void handleUnsetNet(DriftNet net) {
        Rs2GameObject.interact(net.getNet());
        sleepUntil(() -> Rs2Player.isAnimating());
    }

    /**
     * Iterates over nearby fish (sorted by distance to player) and chases the first
     * fish that hasn’t been tagged yet.
     */
    private void chaseNearbyFish(Set<Integer> fishSet) {
        // Sort the NPC indexes by distance to the player
        List<Integer> sortedFish = fishSet.stream()
                .sorted(Comparator.comparingInt(fishIndex -> {
                            Rs2NpcModel npc = Rs2Npc.getNpcByIndex(fishIndex);
                            if (npc == null) {
                                return Integer.MAX_VALUE;
                            }
                            // Return distance from local player
                            return npc.getLocalLocation().distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation());
                        }
                ))
                .collect(Collectors.toList());

        for (int fishIndex : sortedFish) {
            if (DriftNetPlugin.getTaggedFish().containsKey(fishIndex)) continue;

            Rs2NpcModel npc = Rs2Npc.getNpcByIndex(fishIndex);
            if (npc == null) continue;

            // Interact with the fish to "Chase" it
            Rs2Npc.interact(npc, "Chase");
            sleepGaussian(1500, 300);
            break;
        }
    }
}