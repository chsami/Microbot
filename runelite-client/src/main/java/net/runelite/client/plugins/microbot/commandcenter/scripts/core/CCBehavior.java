package net.runelite.client.plugins.microbot.commandcenter.scripts.core;

/**
 * Composable behavior that handles a cross-cutting concern (eating, banking, etc.).
 * Behaviors are checked each tick in priority order. The first to activate handles the tick.
 */
public interface CCBehavior {
    /** Lower number = higher priority. Eating (10) runs before Banking (50). */
    int priority();

    /** Checked every tick. Return true to claim this tick. */
    boolean shouldActivate();

    /** Perform the behavior's action. Only called when shouldActivate() returned true. */
    void execute();

    /** Clean up state on script shutdown. */
    void reset();

    /** Name for logging/debugging. */
    String name();
}
