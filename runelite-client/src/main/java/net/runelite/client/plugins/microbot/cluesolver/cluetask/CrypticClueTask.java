package net.runelite.client.plugins.microbot.cluesolver.cluetask;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.models.RS2Item;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

@Slf4j
public class CrypticClueTask extends ClueTask {

    private final CrypticClue clue;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private enum State { WALKING_TO_LOCATION, KILLING_ENEMY, LOOTING_ITEM, INTERACTING_WITH_OBJECT, INTERACTING_WITH_NPC, HANDLING_DIALOGUE, COMPLETED }
    private State state = State.WALKING_TO_LOCATION;

    public CrypticClueTask(Client client, CrypticClue clue, ClueScrollPlugin clueScrollPlugin,
                           ClueSolverPlugin clueSolverPlugin, EventBus eventBus, ExecutorService backgroundExecutor) {
        super(client, clueScrollPlugin, clueSolverPlugin);
        this.clue = clue;
        this.eventBus = eventBus;
        this.backgroundExecutor = backgroundExecutor;
    }

    @Override
    protected boolean executeTask() throws Exception {
        eventBus.register(this);
        log.info("Starting CrypticClueTask.");
        walkToLocation();
        return true; // Task runs asynchronously, lifecycle managed by `onGameTick`.
    }

    private void walkToLocation() {
        WorldPoint location = clue.getLocation(clueScrollPlugin);
        if (location == null) {
            log.error("Clue location is null.");
            completeTask(false);
            return;
        }

        log.info("Walking to clue location: {}", location);
        backgroundExecutor.submit(() -> {
            if (!Rs2Walker.walkTo(location)) {
                log.error("Failed to initiate walking to location: {}", location);
                completeTask(false);
            }
        });
    }

    private boolean isWithinRadius(WorldPoint targetLocation, WorldPoint playerLocation, int radius) {
        int deltaX = Math.abs(targetLocation.getX() - playerLocation.getX());
        int deltaY = Math.abs(targetLocation.getY() - playerLocation.getY());
        return deltaX <= radius && deltaY <= radius;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Player player = client.getLocalPlayer();
        WorldPoint playerLocation = player.getWorldLocation();
        WorldPoint clueLocation = clue.getLocation(clueScrollPlugin);

        switch (state) {
            case WALKING_TO_LOCATION:
                if (isWithinRadius(Objects.requireNonNull(clueLocation), playerLocation, 30)) {
                    log.info("Arrived at clue location.");
                    transitionToNextState();
                }
                break;

            case KILLING_ENEMY:
                if (killEnemy()) {
                    state = State.LOOTING_ITEM;
                }
                break;

            case LOOTING_ITEM:
                if (lootGroundItem()) {
                    transitionToNextState();
                }
                break;

            case INTERACTING_WITH_OBJECT:
                if (interactWithObject()) {
                    state = State.COMPLETED;
                    completeTask(true);
                } else {
                    log.warn("Failed to interact with object.");
                    completeTask(false);
                }
                break;

            case INTERACTING_WITH_NPC:
                if (interactWithNpc()) {
                    state = State.HANDLING_DIALOGUE;
                } else {
                    log.warn("Failed to interact with NPC.");
                    completeTask(false);
                }
                break;

            case HANDLING_DIALOGUE:
                if (handleDialogue()) {
                    state = State.COMPLETED;
                    completeTask(true);
                } else {
                    log.warn("Dialogue handling failed.");
                    completeTask(false);
                }
                break;

            case COMPLETED:
                log.info("Cryptic clue task completed.");
                completeTask(true);
                break;

            default:
                log.error("Unknown state: {}", state);
                completeTask(false);
                break;
        }
    }

    private void transitionToNextState() {
        if (clue.getEnemy() != null) {
            state = State.KILLING_ENEMY;
        } else if (clue.getObjectId() != -1) {
            state = State.INTERACTING_WITH_OBJECT;
        } else if (clue.getNpc(clueScrollPlugin) != null && Rs2Npc.getNpc(clue.getNpc(clueScrollPlugin)) != null) {
            state = State.INTERACTING_WITH_NPC;
        } else {
            state = State.COMPLETED;
            completeTask(true);
        }
    }

    private boolean killEnemy() {
        NPC enemy = Rs2Npc.getNpc(clue.getEnemy().name());
        if (enemy == null || enemy.getHealthRatio() <= 0) {
            log.info("Enemy {} is defeated. Searching for loot.", clue.getEnemy());
            return true;
        }
        if (Rs2Npc.interact(enemy, "Attack")) {
            log.info("Started attacking enemy: {}", clue.getEnemy());
        } else {
            log.warn("Failed to attack enemy: {}", clue.getEnemy());
        }
        return false;
    }

    private boolean lootGroundItem() {
        RS2Item[] groundItems = Rs2GroundItem.getAll(5);
        boolean anyLooted = false;

        for (RS2Item item : groundItems) {
            if (Rs2GroundItem.interact(item)) {
                log.info("Successfully picked up item: {}", item);
                anyLooted = true;
            } else {
                log.warn("Item {} not found or could not be picked up.", item);
            }
        }
        return anyLooted;
    }

    private boolean interactWithObject() {
        int targetObject = clue.getObjectId();
        if (Rs2GameObject.interact(targetObject, "Search")
                || Rs2GameObject.interact(targetObject, "Investigate")
                || Rs2GameObject.interact(targetObject, "Examine")
                || Rs2GameObject.interact(targetObject, "Look-at")
                || Rs2GameObject.interact(targetObject, "Open")) {
            log.info("Interacted with required object for the clue.");
            return true;
        }
        log.warn("Required object not found for interaction.");
        return false;
    }

    private boolean interactWithNpc() {
        NPC targetNpc = Rs2Npc.getNpc(clue.getNpc(clueScrollPlugin));
        if (targetNpc == null) {
            log.warn("NPC {} not found at the location.", clue.getNpc(clueScrollPlugin));
            return false;
        }
        return Rs2Npc.interact(targetNpc, "Talk-to");
    }

    private boolean handleDialogue() {
        if (Rs2Dialogue.isInDialogue() && Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            log.info("Handled dialogue continue.");
            return true;
        }
        log.warn("Dialogue with NPC did not progress as expected.");
        return false;
    }

    @Override
    protected void completeTask(boolean success) {
        super.completeTask(success);
        eventBus.unregister(this);
        log.info("Cryptic clue task completed with status: {}", success ? "Success" : "Failure");
    }
}
