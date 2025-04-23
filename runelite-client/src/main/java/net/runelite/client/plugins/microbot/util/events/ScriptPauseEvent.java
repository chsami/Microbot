package net.runelite.client.plugins.microbot.util.events;

import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * A blocking event that stops script execution when the user pauses scripts
 */
public class ScriptPauseEvent implements BlockingEvent {
    
    private static boolean isPaused = false;
    
    public static void setPaused(boolean paused) {
        isPaused = paused;
    }
    
    public static boolean isPaused() {
        return isPaused;
    }

    @Override
    public boolean validate() {
        return isPaused;
    }

    @Override
    public boolean execute() {
        // This is a passive blocking event - it just prevents other actions
        // by returning true to indicate it's "handling" the situation
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        // Highest priority to ensure it blocks all other events
        return BlockingEventPriority.HIGHEST;
    }
}