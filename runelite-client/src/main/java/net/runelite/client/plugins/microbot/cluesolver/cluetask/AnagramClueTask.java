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
import net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.ExecutorService;

@Slf4j
public class AnagramClueTask extends ClueTask {
    private final AnagramClue clue;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private WorldPoint location;

    private enum State {
        WALKING_TO_LOCATION,
        INTERACTING_WITH_OBJECT,
        INTERACTING_WITH_NPC,
        HANDLING_DIALOGUE,
        COMPLETED
    }

    private State state = State.WALKING_TO_LOCATION;

    public AnagramClueTask(Client client, AnagramClue clue, ClueScrollPlugin clueScrollPlugin,
                           ClueSolverPlugin clueSolverPlugin, EventBus eventBus, ExecutorService backgroundExecutor) {
        super(client, clueScrollPlugin, clueSolverPlugin);
        this.clue = clue;
        this.eventBus = eventBus;
        this.backgroundExecutor = backgroundExecutor;
        this.location = clue.getLocation(clueScrollPlugin);
    }

    @Override
    protected boolean executeTask() throws Exception {
        eventBus.register(this);
        log.info("Executing AnagramClueTask.");
        walkToLocation();
        return true; // Task runs asynchronously; completion managed in onGameTick.
    }

    private void walkToLocation() {
        log.info("Walking to location: {}", location);
        boolean startedWalking = Rs2Walker.walkTo(location, 1);
        if (!startedWalking) {
            log.error("Failed to initiate walking to location: {}", location);
            completeTask(false);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Player player = client.getLocalPlayer();
        if (player == null) return;

        switch (state) {
            case WALKING_TO_LOCATION:
                if (hasArrived(player)) {
                    transitionToInteractionState();
                } else if (isWithinRadius(location, player.getWorldLocation(), 3)) {
                    Rs2Walker.walkFastCanvas(location);
                }
                break;

            case INTERACTING_WITH_OBJECT:
                if (interactWithObject()) {
                    completeTask(true);
                    state = State.COMPLETED;
                } else {
                    completeTask(false);
                }
                break;

            case INTERACTING_WITH_NPC:
                if (interactWithNpc()) {
                    state = State.HANDLING_DIALOGUE;
                } else {
                    completeTask(false);
                }
                break;

            case HANDLING_DIALOGUE:
                if (handleDialogue()) {
                    completeTask(true);
                    state = State.COMPLETED;
                } else {
                    completeTask(false);
                }
                break;

            case COMPLETED:
                completeTask(true);
                break;

            default:
                log.warn("Unknown state encountered in AnagramClueTask: {}", state);
                completeTask(false);
                break;
        }
    }

    private void transitionToInteractionState() {
        if (clue.getObjectId() != -1) {
            state = State.INTERACTING_WITH_OBJECT;
        } else if (clue.getNpc() != null && Rs2Npc.getNpc(clue.getNpc()) != null) {
            state = State.INTERACTING_WITH_NPC;
        } else {
            log.warn("No valid interaction target found.");
            completeTask(false);
        }
    }

    private boolean interactWithObject() {
        int targetObject = clue.getObjectId();
        boolean interacted = Rs2GameObject.interact(targetObject, "Search")
                || Rs2GameObject.interact(targetObject, "Investigate")
                || Rs2GameObject.interact(targetObject, "Examine")
                || Rs2GameObject.interact(targetObject, "Look-at")
                || Rs2GameObject.interact(targetObject, "Open");

        if (interacted) {
            log.info("Interacted with object for clue.");
        } else {
            log.warn("Object interaction failed.");
        }
        return interacted;
    }

    private boolean interactWithNpc() {
        NPC targetNpc = Rs2Npc.getNpc(clue.getNpc());
        if (targetNpc == null) {
            log.warn("NPC {} not found.", clue.getNpc());
            return false;
        }

        boolean interacted = Rs2Npc.interact(targetNpc, "Talk-to");
        if (interacted) {
            log.info("Talking to NPC: {}", clue.getNpc());
            Rs2Dialogue.sleepUntilInDialogue();
        } else {
            log.warn("Failed to talk to NPC: {}", clue.getNpc());
        }
        return interacted;
    }

    private boolean handleDialogue() {
        String answer = clue.getAnswer(clueScrollPlugin);
        log.info("Answering dialogue: {}", answer);

        if (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
            }

            Rs2Keyboard.typeString(answer);
            Rs2Dialogue.sleepUntilHasContinue();

            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
            }
            return true;
        }
        log.warn("Dialogue handling failed.");
        return false;
    }

    private boolean hasArrived(Player player) {
        return player.getWorldLocation().equals(location);
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
        log.info("Anagram clue task completed with status: {}", success ? "Success" : "Failure");
    }
}
