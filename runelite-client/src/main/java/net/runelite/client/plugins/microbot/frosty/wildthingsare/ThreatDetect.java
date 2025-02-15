package net.runelite.client.plugins.microbot.frosty.wildthingsare;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@PluginDescriptor(
        name = PluginDescriptor.FrostyX + "Threat Detect",
        description = "Automatically teleports if a player is detected in the Wilderness.",
        tags = {"wilderness", "pvp", "escape"}
)
public class ThreatDetect extends Plugin {

    private static final int ESCAPE_RADIUS = 20;
    private boolean seedPodUsed = false;

    @Inject
    private Client client;
    @Override
    protected void startUp() {
        seedPodUsed = false;
    }
    @Override
    protected void shutDown() {
        seedPodUsed = false;
    }
    @Subscribe
    public void onGameTick(GameTick event) {
        if (!isInWilderness()) {
            seedPodUsed = false;
        }
        if (isInWilderness() && isPlayerNearby()) {
            triggerEscape();
        }
    }

    private boolean isPlayerNearby() {
        LocalPoint currentPosition = client.getLocalPlayer().getLocalLocation();
        List<Player> playersInRange = client.getPlayers().stream()
                .filter(player -> player != client.getLocalPlayer())
                .filter(player -> player.getLocalLocation().distanceTo(currentPosition) / 128 <= ESCAPE_RADIUS)
                .collect(Collectors.toList());
        return !playersInRange.isEmpty();
    }

    private void triggerEscape() {
        if (!seedPodUsed) {
            if (Rs2Inventory.interact(ItemID.ROYAL_SEED_POD, "Commune")) {
                seedPodUsed = true;
                return;
            } else {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                        "Failed to activate seed pod! Retrying...", null);
            }
        }
    }

    private boolean isInWilderness() {
        return Microbot.getVarbitValue(Varbits.IN_WILDERNESS) == 1
                && (Rs2Player.getWorldLocation().getPlane() == 1
                || Rs2Player.getWorldLocation().getPlane() == 2
                || Rs2Player.getWorldLocation().getPlane() == 0);
    }
}