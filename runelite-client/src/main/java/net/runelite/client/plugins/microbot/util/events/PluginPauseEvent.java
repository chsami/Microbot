package net.runelite.client.plugins.microbot.util.events;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
/**
 * A blocking event that stops plugin execution when the user pauses plugins
 */
public class PluginPauseEvent implements BlockingEvent {
    
    private static AtomicBoolean isPaused = new AtomicBoolean(false);
    
    public static void setPaused(boolean paused) {        
        PluginPauseEvent.isPaused.compareAndSet(!paused, paused);
    }
    
    public static boolean isPaused() {
        return PluginPauseEvent.isPaused.get();
    }

    @Override
    public boolean validate() {
        return PluginPauseEvent.isPaused.get();
    }

    @Override
    public boolean execute() {                
        // This is a passive blocking event - it just prevents other actions
        // by returning true to indicate it's "handling" the situation              
        return true; // 
    }

    @Override
    public BlockingEventPriority priority() {
        // Highest priority to ensure it blocks all other events
        return BlockingEventPriority.HIGHEST;
    }
}