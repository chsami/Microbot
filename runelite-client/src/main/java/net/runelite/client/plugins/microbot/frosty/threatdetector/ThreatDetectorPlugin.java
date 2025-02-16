package net.runelite.client.plugins.microbot.frosty.threatdetector;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.microbot.frosty.threatdetector.flashLayer;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.FrostyX + "Threat Detector",
        description = "Automatically alerts and communes away if a player is detected in the Wilderness.",
        tags = {"wilderness", "pvp", "escape"}
)
public class ThreatDetectorPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private ThreatDetectorConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ThreatDetectorOverlay overlay;

    @Inject
    private Notifier notifier;

    private boolean overlayOn = false;
    private boolean seedPodUsed = false;
    private HashSet<String> customIgnores = new HashSet<>();
    private final HashMap<String, Integer> playerNameToTimeInRange = new HashMap<>();
    private final SafeZoneHelper zoneHelper = new SafeZoneHelper();

    @Override
    protected void startUp() {
        overlay.setLayer(config.flashLayer().getLayer());
        resetCustomIgnores();
    }

    @Override
    protected void shutDown() {
        removeOverlay();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!isInWilderness()) {
            seedPodUsed = false;
            removeOverlay();
            return;
        }

        List<Player> dangerousPlayers = getPlayersInRange()
                .stream()
                .filter(this::shouldPlayerTriggerAlarm)
                .collect(Collectors.toList());

        updatePlayersInRange();

        if (!dangerousPlayers.isEmpty() && !overlayOn) {
            notifier.notify("Threat detected!" + "Player nearby in the Wilderness!");
            triggerEscape();
            addOverlay();
        } else if (dangerousPlayers.isEmpty()) {
            removeOverlay();
        }
    }

    private List<Player> getPlayersInRange() {
        LocalPoint currentPosition = client.getLocalPlayer().getLocalLocation();
        return client.getPlayers().stream()
                .filter(player -> player.getLocalLocation().distanceTo(currentPosition) / 128 <= config.alarmRadius())
                .collect(Collectors.toList());
    }

    private boolean shouldPlayerTriggerAlarm(Player player) {
        if (player == client.getLocalPlayer() || customIgnores.contains(player.getName().toLowerCase())) {
            return false;
        }

        if (config.ignoreClan() && player.isClanMember()) return false;
        if (config.ignoreFriends() && player.isFriend()) return false;
        if (config.ignoreFriendsChat() && player.isFriendsChatMember()) return false;
        if (config.ignoreIgnored() && client.getIgnoreContainer().findByName(player.getName()) != null) return false;
        if (zoneHelper.isInsideFerox(player.getWorldLocation())) return false;

        return true;
    }

    private void triggerEscape() {
        if (!seedPodUsed && Rs2Inventory.interact(ItemID.ROYAL_SEED_POD, "Commune")) {
            seedPodUsed = true;
        }
    }

    private void updatePlayersInRange() {
        List<Player> playersInRange = getPlayersInRange();

        for (Player player : playersInRange) {
            String playerName = player.getName();
            int timeInRange = playerNameToTimeInRange.getOrDefault(playerName, 0) + Constants.GAME_TICK_LENGTH;
            playerNameToTimeInRange.put(playerName, timeInRange);
        }

        List<String> playersToRemove = playerNameToTimeInRange.keySet()
                .stream()
                .filter(name -> playersInRange.stream().noneMatch(p -> p.getName().equals(name)))
                .collect(Collectors.toList());

        for (String name : playersToRemove) {
            playerNameToTimeInRange.remove(name);
        }
    }


    private boolean isInWilderness() {
        return client.getVarbitValue(Varbits.IN_WILDERNESS) == 1;
    }

    private void addOverlay() {
        overlayOn = true;
        overlayManager.add(overlay);
    }

    private void removeOverlay() {
        overlayOn = false;
        overlayManager.remove(overlay);
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals("ThreatDetector")) {
            overlay.setLayer(config.flashLayer().getLayer());
            resetCustomIgnores();
        }
    }

    private void resetCustomIgnores() {
        customIgnores.clear();
        customIgnores.addAll(List.of(config.customIgnoresList().toLowerCase().split(",")));
    }

    @Provides
    ThreatDetectorConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ThreatDetectorConfig.class);
    }
}
