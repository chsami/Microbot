package net.runelite.client.plugins.microbot.TaF.DeadFallTrapHunter;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.hunter.HunterTrap;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Deadfall Trap Hunter",
        description = "Automates hunting all creatures with Deadfall traps",
        tags = {"hunter", "deadfall", "skilling", "xp", "loot", "TaF"},
        enabledByDefault = false
)
public class DeadFallTrapHunterPlugin extends Plugin implements SchedulablePlugin {

    @Getter
    private final Map<WorldPoint, HunterTrap> traps = new HashMap<>();
    @Inject
    private Client client;
    @Inject
    private DeadFallTrapHunterConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private DeadFallTrapHunterOverlay deadFallTrapHunterOverlay;
    private DeadFallTrapHunterScript script;
    private DeadFallTrapInventoryHandlerScript looter;
    private WorldPoint lastTickLocalPlayerLocation;
    private Instant scriptStartTime;
    private LogicalCondition stopCondition = new AndCondition();

    @Provides
    DeadFallTrapHunterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DeadFallTrapHunterConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        log.info("Deadfall hunter plugin started!");
        scriptStartTime = Instant.now();
        overlayManager.add(deadFallTrapHunterOverlay);
        script = new DeadFallTrapHunterScript();
        script.run(config, this);
        looter = new DeadFallTrapInventoryHandlerScript();
        looter.run(config, script);
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Deadfall hunter plugin stopped!");
        scriptStartTime = null;
        overlayManager.remove(deadFallTrapHunterOverlay);
        if (script != null) {
            script.shutdown();
            looter.shutdown();
        }
    }

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        final GameObject gameObject = event.getGameObject();
        final WorldPoint trapLocation = gameObject.getWorldLocation();
        final HunterTrap myTrap = traps.get(trapLocation);
        final Player localPlayer = client.getLocalPlayer();
        switch (gameObject.getId()) {
            /*
             * ------------------------------------------------------------------------------
             * Placing traps
             * ------------------------------------------------------------------------------
             */
            case ObjectID.HUNTING_DEADFALL_TRAP: // Deadfall trap placed
            case ObjectID.HUNTING_MONKEYTRAP_SET: // Maniacal monkey trap placed
                // If player is right next to "object" trap assume that player placed the trap
                if (localPlayer.getWorldLocation().distanceTo(trapLocation) <= 2) {
                    log.debug("Trap placed by \"{}\" on {}", localPlayer.getName(), trapLocation);
                    traps.put(trapLocation, new HunterTrap(gameObject));
                }
                break;
            case ObjectID.HUNTING_DEADFALL_FULL_SPIKE: // Prickly kebbit caught
            case ObjectID.HUNTING_DEADFALL_FULL_SABRE: // Sabre-tooth kebbit caught
            case ObjectID.HUNTING_DEADFALL_FULL_BARBED: // Barb-tailed kebbit caught
            case ObjectID.HUNTING_DEADFALL_FULL_CLAW: // Wild kebbit caught
            case ObjectID.HUNTING_DEADFALL_FULL_FENNEC: // Pyre fox caught
                if (myTrap != null) {
                    myTrap.setState(HunterTrap.State.FULL);
                    myTrap.resetTimer();
                }

                break;
            case ObjectID.HUNTING_DEADFALL_BOULDER: //Empty deadfall trap
                if (myTrap != null) {
                    myTrap.setState(HunterTrap.State.EMPTY);
                    myTrap.resetTimer();
                }

                break;
            // Deadfall trap
            case ObjectID.HUNTING_DEADFALL_TRAPPING_SPIKE:
            case ObjectID.HUNTING_DEADFALL_TRAPPING_SABRE:
            case ObjectID.HUNTING_DEADFALL_TRAPPING_SABRE_M:
            case ObjectID.HUNTING_DEADFALL_TRAPPING_BARBED:
            case ObjectID.HUNTING_DEADFALL_TRAPPING_BARBED_M:
            case ObjectID.HUNTING_DEADFALL_TRAPPING_CLAW:
            case ObjectID.HUNTING_DEADFALL_TRAPPING_FENNEC:
            case ObjectID.HUNTING_DEADFALL_TRAPPING_FENNEC_M:
                if (myTrap != null) {
                    myTrap.setState(HunterTrap.State.TRANSITION);
                }
                break;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().equalsIgnoreCase("oh dear, you are dead!")) {
            script.hasDied = true;
        }
        if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().contains("You don't have enough inventory space. You need")) {
            script.forceBank = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // Check if all traps are still there, and remove the ones that are not.
        Iterator<Map.Entry<WorldPoint, HunterTrap>> it = traps.entrySet().iterator();
        Tile[][][] tiles = client.getScene().getTiles();

        Instant expire = Instant.now().minus(HunterTrap.TRAP_TIME.multipliedBy(2));

        while (it.hasNext()) {
            Map.Entry<WorldPoint, HunterTrap> entry = it.next();
            HunterTrap trap = entry.getValue();
            WorldPoint world = entry.getKey();
            LocalPoint local = LocalPoint.fromWorld(client, world);

            // Not within the client's viewport
            if (local == null) {
                // Cull very old traps
                if (trap.getPlacedOn().isBefore(expire)) {
                    log.debug("Trap removed from personal trap collection due to timeout, {} left", traps.size());
                    it.remove();
                    continue;
                }
                continue;
            }

            Tile tile = tiles[world.getPlane()][local.getSceneX()][local.getSceneY()];
            GameObject[] objects = tile.getGameObjects();

            boolean containsBoulder = false;
            boolean containsAnything = false;
            boolean containsYoungTree = false;
            for (GameObject object : objects) {
                if (object != null) {
                    containsAnything = true;
                    if (object.getId() == ObjectID.HUNTING_DEADFALL_BOULDER || object.getId() == ObjectID.HUNTING_MONKEYTRAP_UNSET) {
                        containsBoulder = true;
                        break;
                    }

                    // Check for young trees (used while catching salamanders) in the tile.
                    // Otherwise, hunter timers will never disappear after a trap is dismantled
                    if (object.getId() == ObjectID.HUNTING_SAPLING_UP_ORANGE || object.getId() == ObjectID.HUNTING_SAPLING_UP_RED ||
                            object.getId() == ObjectID.HUNTING_SAPLING_UP_BLACK || object.getId() == ObjectID.HUNTING_SAPLING_UP_SWAMP ||
                            object.getId() == ObjectID.HUNTING_SAPLING_UP_MOUNTAIN || object.getId() == ObjectID.HUNTING_SAPLING_SETTING_MOUNTAIN) {
                        containsYoungTree = true;
                    }
                }
            }

            if (!containsAnything || containsYoungTree) {
                it.remove();
                log.debug("Trap removed from personal trap collection, {} left", traps.size());
            } else if (containsBoulder) // For traps like deadfalls. This is different because when the trap is gone, there is still a GameObject (boulder)
            {
                it.remove();
                log.debug("Special trap removed from personal trap collection, {} left", traps.size());

                // Case we have notifications enabled and the action was not manual, throw notification
                if (trap.getObjectId() == ObjectID.HUNTING_MONKEYTRAP_SET && !trap.getState().equals(HunterTrap.State.FULL) && !trap.getState().equals(HunterTrap.State.OPEN)) {
                    //notifier.notify(config.maniacalMonkeyNotify(), "The monkey escaped.");
                }
            }
        }

        lastTickLocalPlayerLocation = client.getLocalPlayer().getWorldLocation();
    }

    @Override
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        if (event.getPlugin() == this) {
            if (script != null) {
                Rs2Bank.walkToBank();
            }
            Microbot.stopPlugin(this);
        }
    }

    @Override
    public LogicalCondition getStopCondition() {
        // Create a new stop condition
        return this.stopCondition;
    }
}
