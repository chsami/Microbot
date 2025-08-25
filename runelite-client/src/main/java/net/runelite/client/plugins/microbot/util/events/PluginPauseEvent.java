package net.runelite.client.plugins.microbot.util.events;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
/**
 * A blocking event that stops plugin execution when the user pauses plugins
 */
public class PluginPauseEvent implements BlockingEvent {
    
    private static AtomicBoolean isPaused = new AtomicBoolean(false);
    
    public static void setPaused(boolean paused) {        
        PluginPauseEvent.isPaused.compareAndSet(!paused, paused);
        // here we allow still the exceuction of blocking events, but not the scripts
    }
    public static void setPausedAll(boolean paused) {        
        PluginPauseEvent.isPaused.compareAndSet(!paused, paused);
        Microbot.pauseAllScripts.compareAndSet(!paused, paused);
        // here we block all scripts and blocking events form executing
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
        if (PluginPauseEvent.isPaused.get()){
            // sum custom logic can go here 
            // e.g. notifying about some game state, or we could think for "Rs2walker.setTarget(null); -> blocking all movement", but only one time
            // we could think of saving a reference of a script which paused the scripts, so only these script is allowed to run ? 
        }
        return !PluginPauseEvent.isPaused.get();
    }

    @Override
    public BlockingEventPriority priority() {
        // Highest priority to ensure it blocks all other events
        return BlockingEventPriority.HIGHEST;
    }
}