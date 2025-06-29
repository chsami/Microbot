package net.runelite.client.plugins.microbot.cluesolver.cluetask;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;

import java.util.concurrent.CompletableFuture;

/**
 * Abstract class representing a generic task for solving a clue.
 * Specific tasks should extend this class.
 */
@Slf4j
public abstract class ClueTask implements Runnable {

    protected final Client client;
    protected final ClueScrollPlugin clueScrollPlugin;
    protected final ClueSolverPlugin clueSolverPlugin;

    @Setter
    private CompletableFuture<Boolean> future;  // Future to indicate task completion

    public ClueTask(Client client, ClueScrollPlugin clueScrollPlugin, ClueSolverPlugin clueSolverPlugin) {
        this.client = client;
        this.clueScrollPlugin = clueScrollPlugin;
        this.clueSolverPlugin = clueSolverPlugin;
    }

    @Override
    public void run() {
        try {
            boolean success = executeTask();
            //completeTask(success);
        } catch (Exception e) {
            log.error("Error executing ClueTask: {}", e.getMessage(), e);
            completeTask(false);
        }
    }

    /**
     * Abstract method that specific tasks must implement to define their own task logic.
     * @return true if the task succeeds, false otherwise.
     * @throws Exception if any error occurs during task execution.
     */
    protected abstract boolean executeTask() throws Exception;

    /**
     * Completes the task and sets the result of the future.
     * @param success whether the task completed successfully.
     */
    protected void completeTask(boolean success) {
        if (future != null && !future.isDone()) {
            future.complete(success);
            log.info("ClueTask completed with status: {}", success ? "Success" : "Failure");
        }
    }

    /**
     * Utility method for subclasses to perform necessary checks or preparations.
     * This can be overridden by subclasses for specific preconditions.
     * @return true if the task can proceed, false otherwise.
     */
    protected boolean preTaskCheck() {
        return client != null && client.getLocalPlayer() != null;
    }
}
