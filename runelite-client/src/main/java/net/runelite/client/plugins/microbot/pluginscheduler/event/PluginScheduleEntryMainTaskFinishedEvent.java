package net.runelite.client.plugins.microbot.pluginscheduler.event;

import java.time.ZonedDateTime;

import lombok.Getter;
import net.runelite.client.plugins.Plugin;

/**
 * Event fired when a plugin has completed its work and is ready to be stopped
 * This is different from ScheduledStopEvent as it represents a plugin self-reporting
 * that it has finished its task rather than the scheduler determining it should be stopped
 * due to conditions.
 */
@Getter
public class PluginScheduleEntryMainTaskFinishedEvent {
    private final Plugin plugin;
    private final ZonedDateTime finishDateTime;
    private final String reason;
    private final ExecutionResult result;
    
    /**
     * Creates a new plugin finished event
     *
     * @param plugin The plugin that has finished
     * @param finishDateTime The time when the plugin finished
     * @param reason A description of why the plugin finished
     * @param result The execution result (SUCCESS, SOFT_FAILURE, or HARD_FAILURE)
     */
    public PluginScheduleEntryMainTaskFinishedEvent(Plugin plugin, ZonedDateTime finishDateTime, String reason, ExecutionResult result) {
        this.plugin = plugin;
        this.finishDateTime = finishDateTime;
        this.reason = reason;
        this.result = result;
    }
    
    /**
     * Creates a new plugin finished event with current time
     *
     * @param plugin The plugin that has finished
     * @param reason A description of why the plugin finished
     * @param result The execution result (SUCCESS, SOFT_FAILURE, or HARD_FAILURE)
     */
    public PluginScheduleEntryMainTaskFinishedEvent(Plugin plugin, String reason, ExecutionResult result) {
        this(plugin, ZonedDateTime.now(), reason, result);
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