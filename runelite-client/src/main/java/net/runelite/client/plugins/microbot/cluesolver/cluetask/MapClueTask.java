package net.runelite.client.plugins.microbot.cluesolver.cluetask;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.MapClue;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.ExecutorService;

@Slf4j
public class MapClueTask extends ClueTask {

    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private final WorldPoint location;
    private final int objectId;
    private final MapClue clue;

    private enum State {
        WALKING_TO_LOCATION,
        INTERACTING_WITH_OBJECT,
        DIGGING,
        COMPLETED
    }

    private State state = State.WALKING_TO_LOCATION;

    public MapClueTask(Client client, MapClue clue, ClueScrollPlugin clueScrollPlugin,
                       ClueSolverPlugin clueSolverPlugin, EventBus eventBus, ExecutorService backgroundExecutor) {
        super(client, clueScrollPlugin, clueSolverPlugin);
        this.clue = clue;
        this.eventBus = eventBus;
        this.backgroundExecutor = backgroundExecutor;
        this.location = clue.getLocation(clueScrollPlugin);
        this.objectId = clue.getObjectId();
    }

    @Override
    protected boolean executeTask() {
        eventBus.register(this);
        log.info("Starting MapClueTask.");
        walkToLocation();
        return true;
    }

    private void walkToLocation() {
        if (location == null) {
            log.error("Map clue location is null.");
            completeTask(false);
            return;
        }

        log.info("Submitting walking task to background executor for location: {}", location);
        backgroundExecutor.submit(() -> {
            boolean startedWalking = Rs2Walker.walkTo(location, 1);
            if (!startedWalking) {
                log.error("Failed to initiate walking to location: {}", location);
                completeTask(false);
            }
        });
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Player player = client.getLocalPlayer();
        switch (state) {
            case WALKING_TO_LOCATION:
                if (isWithinRadius(location, player.getWorldLocation(), 5)) {
                    log.info("Arrived at map clue location.");
                    if (objectId != -1) {
                        state = State.INTERACTING_WITH_OBJECT;
                        interactWithObject();
                    } else {
                        state = State.DIGGING;
                        performDig();
                    }
                }
                break;

            case INTERACTING_WITH_OBJECT:
                if (interactWithObject()) {
                    log.info("Interacted with object at location.");
                    state = State.COMPLETED;
                    completeTask(true);
                } else {
                    log.warn("Failed to interact with object.");
                    completeTask(false);
                }
                break;

            case DIGGING:
                performDig();
                break;

            case COMPLETED:
                log.info("Map clue task completed.");
                completeTask(true);
                break;
        }
    }

    private boolean interactWithObject() {
        if (objectId == -1) return false;

        boolean interactionSuccessful = Rs2GameObject.interact(objectId, "Search")
                || Rs2GameObject.interact(objectId, "Investigate")
                || Rs2GameObject.interact(objectId, "Examine")
                || Rs2GameObject.interact(objectId, "Look-at")
                || Rs2GameObject.interact(objectId, "Open");

        if (interactionSuccessful) {
            log.info("Interacted with required object for the clue.");
        } else {
            log.warn("Required object not found for interaction.");
        }
        return interactionSuccessful;
    }

    private void performDig() {
        if (isWithinRadius(location, client.getLocalPlayer().getWorldLocation(), 1) && Rs2Inventory.contains("Spade")) {
            log.info("Digging with Spade at location.");
            boolean dug = Rs2Inventory.interact(ItemID.SPADE, "Dig");
            if (dug) {
                state = State.COMPLETED;
                completeTask(true);
            } else {
                log.warn("Failed to dig at location.");
                completeTask(false);
            }
        } else if (!Rs2Inventory.contains("Spade")) {
            log.warn("Player does not have a spade.");
            completeTask(false);
        } else {
            log.info("Not at exact location, adjusting position.");
            Rs2Walker.walkFastCanvas(location);
        }
    }

    private boolean isWithinRadius(WorldPoint targetLocation, WorldPoint playerLocation, int radius) {
        int deltaX = Math.abs(targetLocation.getX() - playerLocation.getX());
        int deltaY = Math.abs(targetLocation.getY() - playerLocation.getY());
        return deltaX <= radius && deltaY <= radius;
    }

    @Override
    protected void completeTask(boolean success) {
        super.completeTask(success);
        eventBus.unregister(this);
        log.info("Map clue task completed with status: {}", success ? "Success" : "Failure");
    }
}
