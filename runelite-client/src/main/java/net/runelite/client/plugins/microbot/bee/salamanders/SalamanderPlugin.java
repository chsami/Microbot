package net.runelite.client.plugins.microbot.bee.salamanders;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.Angle;
import net.runelite.api.coords.Direction;
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
import net.runelite.client.plugins.microbot.magic.orbcharger.enums.OrbChargerState;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Salamanders",
        description = "Automates salamander hunting",
        tags = {"hunter", "salamanders", "skilling"},
        enabledByDefault = false
)
public class SalamanderPlugin extends Plugin {

    @Getter
    private final Map<WorldPoint, HunterTrap> traps = new HashMap<>();
    @Inject
    private Client client;
    @Inject
    private SalamanderConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private SalamanderOverlay salamanderOverlay;
    private SalamanderScript script;
    private SalamanderGroundItemLooter looter;
    private WorldPoint lastTickLocalPlayerLocation;
    private Instant scriptStartTime;

    @Provides
    SalamanderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SalamanderConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        log.info("Salamander Plugin started!");
        scriptStartTime = Instant.now();
        overlayManager.add(salamanderOverlay);
        script = new SalamanderScript();
        script.run(config, this);
        looter = new SalamanderGroundItemLooter();
        looter.run(config, script);
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Salamander Plugin stopped!");
        scriptStartTime = null;
        overlayManager.remove(salamanderOverlay);
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
            case ObjectID.HUNTING_SAPLING_NET_SET_SWAMP: // Net trap placed at Green salamanders
            case ObjectID.HUNTING_SAPLING_NET_SET_ORANGE: // Net trap placed at Orange salamanders
            case ObjectID.HUNTING_SAPLING_NET_SET_RED: // Net trap placed at Red salamanders
            case ObjectID.HUNTING_SAPLING_NET_SET_BLACK: // Net trap placed at Black salamanders
            case ObjectID.HUNTING_SAPLING_NET_SET_MOUNTAIN: // Net trap placed at Tecu salamanders
                if (lastTickLocalPlayerLocation != null
                        && trapLocation.distanceTo(lastTickLocalPlayerLocation) == 0) {
                    // Net traps facing to the north and east must have their tile translated.
                    // As otherwise, the wrong tile is stored.
                    Direction trapOrientation = new Angle(gameObject.getOrientation()).getNearestDirection();
                    WorldPoint translatedTrapLocation = trapLocation;

                    switch (trapOrientation) {
                        case SOUTH:
                            translatedTrapLocation = trapLocation.dy(-1);
                            break;
                        case WEST:
                            translatedTrapLocation = trapLocation.dx(-1);
                            break;
                    }

                    log.debug("Trap placed by \"{}\" on {} facing {}", localPlayer.getName(), translatedTrapLocation, trapOrientation);
                    traps.put(translatedTrapLocation, new HunterTrap(gameObject));
                }
                break;

            case ObjectID.HUNTING_SAPLING_FULL_GREEN: // Green salamander caught
            case ObjectID.HUNTING_SAPLING_FULL_RED: // Red salamander caught
            case ObjectID.HUNTING_SAPLING_FULL_ORANGE: // Orange salamander caught
            case ObjectID.HUNTING_SAPLING_FULL_BLACK: // Black salamander caught
            case ObjectID.HUNTING_SAPLING_FULL_MOUNTAIN: // Tecu salamander caught
                if (myTrap != null) {
                    myTrap.setState(HunterTrap.State.FULL);
                    myTrap.resetTimer();

                    if (myTrap.getObjectId() == ObjectID.HUNTING_MONKEYTRAP_SET) {
                        //notifier.notify(config.maniacalMonkeyNotify(), "You've caught part of a monkey's tail.");
                    }
                }

                break;
            case ObjectID.HUNTING_SAPLING_FAILED_MOUNTAIN: //Empty net trap
                if (myTrap != null) {
                    myTrap.setState(HunterTrap.State.EMPTY);
                    myTrap.resetTimer();
                }

                break;
            // Net trap
            case ObjectID.HUNTING_SAPLING_CATCHING_GREEN:
            case ObjectID.HUNTING_SAPLING_FAILING_SWAMP:
            case ObjectID.HUNTING_SAPLING_CATCHING_ORANGE:
            case ObjectID.HUNTING_SAPLING_FAILING_ORANGE:
            case ObjectID.HUNTING_SAPLING_CATCHING_RED:
            case ObjectID.HUNTING_SAPLING_FAILING_RED:
            case ObjectID.HUNTING_SAPLING_CATCHING_BLACK:
            case ObjectID.HUNTING_SAPLING_FAILING_BLACK:
            case ObjectID.HUNTING_SAPLING_CATCHING_MOUNTAIN:
            case ObjectID.HUNTING_SAPLING_FAILING_MOUNTAIN:
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
}
