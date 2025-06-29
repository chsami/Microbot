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
import net.runelite.client.plugins.cluescrolls.clues.MusicClue;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Slf4j
public class MusicClueTask extends ClueTask {
    private Future<?> currentTask;
    private final MusicClue clue;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private WorldPoint location;
    private String npcName = "Cecilia";
    private String songName;
    private enum State { WALKING_TO_LOCATION, PLAYING_SONG, INTERACTING_WITH_NPC, COMPLETED }
    private State state = State.WALKING_TO_LOCATION;

    public MusicClueTask(Client client, MusicClue clue, ClueScrollPlugin clueScrollPlugin,
                         ClueSolverPlugin clueSolverPlugin, EventBus eventBus, ExecutorService backgroundExecutor) {
        super(client, clueScrollPlugin, clueSolverPlugin);
        this.clue = clue;
        this.eventBus = eventBus;
        this.backgroundExecutor = backgroundExecutor;
        this.location = clue.getLocation(clueScrollPlugin);
        this.songName = clue.getSong();
    }

    @Override
    protected boolean executeTask() throws Exception {
        eventBus.register(this);
        log.info("Executing MusicClueTask.");
        walkToLocation();
        return true; // Task runs asynchronously; lifecycle managed in onGameTick.
    }

    private void walkToLocation() {
        if (location == null) {
            log.error("Music clue location is null.");
            completeTask(false);
            return;
        }

        log.info("Submitting walking task to background executor for location: {}", location);

            boolean startedWalking = Rs2Walker.walkTo(location, 1);
            if (!startedWalking) {
                log.error("Failed to initiate walking to location: {}", location);
                completeTask(false);
            }

    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (currentTask != null && !currentTask.isDone()) {
            log.warn("Previous task is still running, skipping this tick.");
            return;
        }
        currentTask = backgroundExecutor.submit(() -> {
            try {
                processGameTick(event);
            } catch (Exception e) {
                log.error("Error processing game tick in MusicClueTask: {}", e.getMessage(), e);
                completeTask(false);
            }
        });

    }

    private void processGameTick(GameTick event) {
        Player player = client.getLocalPlayer();
        if (player == null) return;

        switch (state) {
            case WALKING_TO_LOCATION:
                if (isWithinRadius(location, player.getWorldLocation(), 5)) {
                    log.info("Arrived at music clue location.");
                    state = State.PLAYING_SONG;
                }
                break;

            case PLAYING_SONG:
                if (playSong()) {
                    state = State.INTERACTING_WITH_NPC;
                } else {
                    completeTask(false);
                }
                break;

            case INTERACTING_WITH_NPC:
                if (interactWithNpc()) {
                    state = State.COMPLETED;
                    completeTask(true);
                } else {
                    completeTask(false);
                }
                break;

            case COMPLETED:
                log.info("Music clue task completed.");
                completeTask(true);
                break;

            default:
                log.warn("Unknown state in MusicClueTask: {}", state);
                completeTask(false);
                break;
        }
    }

    private boolean playSong() {
        log.info("Attempting to play song: {}", songName);
        if (!Rs2Tab.switchToMusicTab()) {
            log.warn("Failed to switch to music tab.");
            return false;
        }

        if (Rs2Widget.findWidget(songName) != null) {
            Rs2Widget.clickWidget(songName);
            log.info("Playing song: {}", songName);
            return true;
        } else {
            log.warn("Song widget not found: {}", songName);
            return false;
        }
    }

    private boolean interactWithNpc() {
        NPC npc = Rs2Npc.getNpc(npcName);
        if (npc == null) {
            log.warn("NPC {} not found near the clue location.", npcName);
            return false;
        }

        if (Rs2Npc.interact(npc, "Talk-to")) {
            log.info("Interacted with NPC: {}", npcName);
            return true;
        } else {
            log.warn("Failed to talk to NPC: {}", npcName);
            return false;
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
        log.info("Music clue task completed with status: {}", success ? "Success" : "Failure");
    }
}
