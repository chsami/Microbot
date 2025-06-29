package net.runelite.client.plugins.microbot.cluesolver.cluetask;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.CoordinateClue;
import net.runelite.client.plugins.cluescrolls.clues.Enemy;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Slf4j
public class CoordinateClueTask extends ClueTask {
    private Future<?> currentTask;
    private final CoordinateClue clue;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private final WorldPoint location;
    private final Enemy enemy;

    private enum State {
        WALKING_TO_LOCATION,
        FIGHTING_ENEMY,
        DIGGING,
        COMPLETED
    }

    private State state = State.WALKING_TO_LOCATION;

    public CoordinateClueTask(Client client, CoordinateClue clue, ClueScrollPlugin clueScrollPlugin,
                              ClueSolverPlugin clueSolverPlugin, EventBus eventBus, ExecutorService backgroundExecutor) {
        super(client, clueScrollPlugin, clueSolverPlugin);
        this.clue = clue;
        this.eventBus = eventBus;
        this.backgroundExecutor = backgroundExecutor;
        this.location = clue.getLocation(clueScrollPlugin);
        this.enemy = clue.getEnemy();
    }

    @Override
    protected boolean executeTask() throws Exception {
        eventBus.register(this);
        log.info("Starting CoordinateClueTask.");
        walkToLocation();
        return true; // Task lifecycle is handled asynchronously by `onGameTick`.
    }

    private void walkToLocation() {
        if (location == null) {
            log.error("Clue location is null.");
            completeTask(false);
            return;
        }

        log.info("Walking to clue location: {}", location);

        if (!Rs2Walker.walkTo(location, 1)) {
            log.error("Failed to initiate walking to location: {}", location);
            completeTask(false);
        }
    }


    private boolean isWithinRadius(WorldPoint targetLocation, WorldPoint playerLocation, int radius) {
        int deltaX = Math.abs(targetLocation.getX() - playerLocation.getX());
        int deltaY = Math.abs(targetLocation.getY() - playerLocation.getY());
        return deltaX <= radius && deltaY <= radius;
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
            } catch (Exception ex) {
                log.error("Error processing game tick", ex);
            }
        });
    }

    private void processGameTick(GameTick event) {
        Player player = client.getLocalPlayer();
        if (player == null) return;

        switch (state) {
            case WALKING_TO_LOCATION:
                if (isWithinRadius(location, player.getWorldLocation(), 5)) {
                    log.info("Arrived at coordinate clue location.");
                    state = (enemy != null) ? State.FIGHTING_ENEMY : State.DIGGING;
                }
                break;

            case FIGHTING_ENEMY:
                if (enemy != null && engageEnemy()) {
                    log.info("Enemy defeated, ready to dig.");
                    state = State.DIGGING;
                }
                break;

            case DIGGING:
                if (prepareToDig()) {
                    log.info("Digging at clue location.");
                    state = State.COMPLETED;
                    completeTask(true);
                }
                break;

            case COMPLETED:
                log.info("Coordinate clue task completed.");
                completeTask(true);
                break;

            default:
                log.error("Unknown state: {}", state);
                completeTask(false);
                break;
        }
    }

    private boolean engageEnemy() {
        NPC targetNpc = Rs2Npc.getNpc(enemy.getText());
        if (targetNpc == null) {
            log.warn("Expected enemy not found.");
            completeTask(false);
            return false;
        }
        if (Rs2Npc.interact(targetNpc, "Attack")) {
            log.info("Engaging enemy: {}", enemy.getText());
            return waitForEnemyDefeat(targetNpc);
        }
        log.warn("Failed to attack enemy: {}", enemy.getText());
        return false;
    }

    private boolean waitForEnemyDefeat(NPC targetNpc) {
        return targetNpc.isDead(); // This assumes an enemy tracking system
    }

    private boolean prepareToDig() {
        Player player = client.getLocalPlayer();
        if (!isWithinRadius(location, player.getWorldLocation(), 1)) {
            log.info("Adjusting position to exact location.");
            Rs2Walker.walkFastCanvas(location);
            return false;
        }
        if (!Rs2Inventory.contains("Spade")) {
            log.warn("Spade not found in inventory.");
            completeTask(false);
            return false;
        }
        return Rs2Inventory.interact(ItemID.SPADE, "Dig");
    }

    @Override
    protected void completeTask(boolean success) {
        super.completeTask(success);
        eventBus.unregister(this);
        log.info("Coordinate clue task completed with status: {}", success ? "Success" : "Failure");
    }
}
