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
import net.runelite.client.plugins.cluescrolls.clues.CipherClue;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.ExecutorService;

import static org.lwjgl.system.windows.User32.VK_RETURN;

@Slf4j
public class CipherClueTask extends ClueTask {
    private final CipherClue clue;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;

    private enum State {
        WALKING_TO_LOCATION,
        INTERACTING_WITH_NPC,
        HANDLING_DIALOGUE,
        COMPLETED
    }

    private State state = State.WALKING_TO_LOCATION;

    public CipherClueTask(Client client, CipherClue clue, ClueScrollPlugin clueScrollPlugin,
                          ClueSolverPlugin clueSolverPlugin, EventBus eventBus, ExecutorService backgroundExecutor) {
        super(client, clueScrollPlugin, clueSolverPlugin);
        this.clue = clue;
        this.eventBus = eventBus;
        this.backgroundExecutor = backgroundExecutor;
    }

    @Override
    protected boolean executeTask() {
        eventBus.register(this);
        log.info("Starting CipherClueTask.");
        walkToLocation();
        return true;
    }

    private void walkToLocation() {
        WorldPoint location = clue.getLocation(clueScrollPlugin);
        if (location == null) {
            log.error("Clue location is null.");
            completeTask(false);
            return;
        }

        log.info("Walking to location: {}", location);
        backgroundExecutor.submit(() -> {
            boolean startedWalking = Rs2Walker.walkTo(location);
            if (!startedWalking) {
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
                if (isWithinRadius(clueLocation, playerLocation, 5)) {
                    log.info("Arrived at clue location.");
                    state = State.INTERACTING_WITH_NPC;
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
                log.info("Cipher clue task completed.");
                completeTask(true);
                break;
        }
    }

    private boolean interactWithNpc() {
        var npc = Rs2Npc.getNpc(clue.getNpcId());
        if (npc == null) {
            log.warn("NPC with ID {} not found at the location.", clue.getNpcId());
            return false;
        }

        if (Rs2Npc.interact(npc, "Talk-to")) {
            log.info("Interacting with NPC for cipher clue.");
            Rs2Dialogue.sleepUntilInDialogue();
            return handleDialogue();
        }

        log.warn("Failed to initiate interaction with NPC ID: {}", clue.getNpcId());
        return false;
    }

    private boolean handleDialogue() {
        if (Rs2Dialogue.isInDialogue()) {
            log.info("Handling cipher clue dialogue.");

            while (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                Rs2Dialogue.sleepUntilHasContinue();
                log.info("Clicked continue in dialogue.");
            }

            if (Rs2Inventory.contains("Challenge scroll")) {
                Rs2Dialogue.clickContinue();
                Rs2Dialogue.sleepUntilHasContinue();
                log.info("Challenge scroll added to inventory.");
            }

            if (clue.getAnswer() != null && !Rs2Dialogue.hasContinue()) {
                log.info("Answering question for cipher clue.");
                Rs2Keyboard.typeString(clue.getAnswer());
                Rs2Keyboard.keyPress(VK_RETURN);
                log.info("Entered answer for cipher clue question: {}", clue.getAnswer());
                return true;
            }
        }

        log.warn("Dialogue or challenge scroll response not handled as expected.");
        return false;
    }

    @Override
    protected void completeTask(boolean success) {
        super.completeTask(success);
        eventBus.unregister(this);
        log.info("Cipher clue task completed with status: {}", success ? "Success" : "Failure");
    }
}
