package net.runelite.client.plugins.microbot.pluginscheduler.event;

import java.time.ZonedDateTime;

import lombok.Getter;
import net.runelite.client.plugins.Plugin;

/**
 * Event fired when a plugin's post-schedule tasks have finished.
 * This is sent by plugins back to the scheduler to indicate completion of post-schedule tasks.
 */
@Getter
public class PluginScheduleEntryPostScheduleTaskFinishedEvent {
    private final Plugin plugin;
    private final ZonedDateTime finishDateTime;
    private final ExecutionResult result;
    private final String message;
    
    /**
     * Creates a new plugin post-schedule task finished event
     *
     * @param plugin The plugin that finished post-schedule tasks
     * @param finishDateTime The time when the plugin finished
     * @param result The execution result (SUCCESS, SOFT_FAILURE, or HARD_FAILURE)
     * @param message Optional message describing the completion
     */
    public PluginScheduleEntryPostScheduleTaskFinishedEvent(Plugin plugin, ZonedDateTime finishDateTime, ExecutionResult result, String message) {
        this.plugin = plugin;
        this.finishDateTime = finishDateTime;
        this.result = result;
        this.message = message;
    }
    
    /**
     * Creates a new plugin post-schedule task finished event with current time
     *
     * @param plugin The plugin that finished post-schedule tasks
     * @param result The execution result (SUCCESS, SOFT_FAILURE, or HARD_FAILURE)
     * @param message Optional message describing the completion
     */
    public PluginScheduleEntryPostScheduleTaskFinishedEvent(Plugin plugin, ExecutionResult result, String message) {
        this(plugin, ZonedDateTime.now(), result, message);
    }
    
    /**
     * @deprecated Use {@link #getResult()} instead for more granular result information
     * @return true if the result indicates success
     */
    @Deprecated
    public boolean isSuccess() {
        return result.isSuccess();
    }
}
