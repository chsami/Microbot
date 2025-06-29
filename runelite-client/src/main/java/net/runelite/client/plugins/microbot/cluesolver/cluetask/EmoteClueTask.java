package net.runelite.client.plugins.microbot.cluesolver.cluetask;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

@Slf4j
public class EmoteClueTask extends ClueTask {

    private NPC doubleAgent;
    private final EmoteClue clue;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private boolean firstEmotePerformed = false;
    private boolean secondEmotePerformed = false;
    private boolean enemyDefeated = false;
    private Future<?> currentTask;

    private static final String DOUBLE_AGENT_NAME = "Double Agent";
    private static final int URI_ID = NpcID.URI;

    private enum State {
        WALKING_TO_LOCATION,
        PERFORMING_EMOTES,
        WAITING_FOR_ENEMY_SPAWN,
        FIGHTING_ENEMY,
        COMPLETED
    }

    private State state = State.WALKING_TO_LOCATION;

    public EmoteClueTask(Client client, EmoteClue clue, ClueScrollPlugin clueScrollPlugin,
                         ClueSolverPlugin clueSolverPlugin, EventBus eventBus, ExecutorService backgroundExecutor) {
        super(client, clueScrollPlugin, clueSolverPlugin);
        this.clue = clue;
        this.eventBus = eventBus;
        this.backgroundExecutor = backgroundExecutor;
    }

    @Override
    protected boolean executeTask() {
        if (!preTaskCheck()) {
            completeTask(false);
            return false;
        }
        eventBus.register(this);
        log.info("Starting EmoteClueTask.");
        walkToLocation(); // Only initiate the walk here; further logic is handled in onGameTick
        return true;
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
            if (!Rs2Walker.walkTo(location, 0)) {
                log.error("Failed to initiate walking to location: {}", location);
                completeTask(false);
            }
        });
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (null != currentTask && !currentTask.isDone()) {
            log.warn("Previous task is still running, skipping this tick.");
            return;
        }

        currentTask = backgroundExecutor.submit(() -> {
            try {
                processGameTick(event);
            } catch (Exception e) {
                log.error("Error processing game tick in EmoteClueTask: {}", e.getMessage(), e);
                completeTask(false);
            }
        });

    }

    private void processGameTick(GameTick event) {
        switch (state) {
            case WALKING_TO_LOCATION:
                handleWalkingToLocation();
                break;
            case PERFORMING_EMOTES:
                handlePerformingEmotes();
                break;
            case WAITING_FOR_ENEMY_SPAWN:
                log.info("Awaiting enemy spawn for the Double Agent.");
                break;
            case FIGHTING_ENEMY:
                if (enemyDefeated) {
                    log.info("Double agent defeated; proceeding to second emote if necessary.");
                    performSecondEmote();
                }
                break;
            case COMPLETED:
                log.info("Emote clue task completed.");
                completeTask(true);
                break;
        }
    }

    private void handleWalkingToLocation() {
        WorldPoint location = clue.getLocation(clueScrollPlugin);
        if (client.getLocalPlayer().getWorldLocation().equals(location)) {
            log.info("Arrived at Emote Clue location.");
            state = State.PERFORMING_EMOTES;
        } else {
            log.debug("Walking to clue location: {}", location);
            Rs2Walker.walkTo(location, 0);
        }
    }

    private void handlePerformingEmotes() {
        if (!firstEmotePerformed) {
            performEmote(clue.getFirstEmote().getName());
            firstEmotePerformed = true;
            if (clue.getEnemy() != null) {
                state = State.WAITING_FOR_ENEMY_SPAWN;
                log.info("Waiting for Double Agent to spawn.");
            }
        } else if (firstEmotePerformed && clue.getSecondEmote() != null && !secondEmotePerformed) {
            performSecondEmote();
        } else {
            interactWithUri();
            completeTask(true);
        }
    }

    private void performSecondEmote() {
        if (clue.getSecondEmote() != null) {
            performEmote(clue.getSecondEmote().getName());
            secondEmotePerformed = true;
        }
        interactWithUri();
        state = State.COMPLETED;
    }

    private void performEmote(String emoteName) {
        if (Rs2Tab.switchToEmotesTab()) {
            log.info("Performing emote: {}", emoteName);
            boolean clicked = Rs2Widget.clickWidget(emoteName);
            sleep(750);
            if (!clicked) {
                log.warn("Emote widget not found or could not be clicked: {}", emoteName);
            }
        }
    }

    private void interactWithUri() {
        Rs2Npc.interact("Uri", "Talk-to");
        log.info("Interacted with Uri.");
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        if (npc.getName() != null) {
            if (state == State.WAITING_FOR_ENEMY_SPAWN && npc.getName().equalsIgnoreCase(DOUBLE_AGENT_NAME)) {
                doubleAgent = npc;
                log.info("Double agent spawned.");
                state = State.FIGHTING_ENEMY;
                attackDoubleAgent();
            }
        }
    }

    private void attackDoubleAgent() {
        if (doubleAgent != null) {
            Rs2Npc.interact(doubleAgent, "Attack");
            log.info("Attacking Double Agent.");
        } else {
            log.warn("Double Agent NPC is null or missing.");
            completeTask(false);
        }
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event) {
        if (state == State.FIGHTING_ENEMY && event.getSource() == client.getLocalPlayer() &&
                event.getTarget() == null) {
            if (doubleAgent == null || Rs2Npc.getHealth(doubleAgent) <= 0) {
                log.info("Double agent defeated.");
                enemyDefeated = true;
            }
        }
    }

    @Override
    protected void completeTask(boolean success) {
        super.completeTask(success);
        eventBus.unregister(this);
        log.info("Emote clue task completed with status: {}", success ? "Success" : "Failure");
    }
}
