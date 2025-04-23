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
public class PluginScheduleEntryFinishedEvent {
    private final Plugin plugin;
    private final ZonedDateTime finishDateTime;
    private final String reason;
    private final boolean success;
    
    /**
     * Creates a new plugin finished event
     *
     * @param plugin The plugin that has finished
     * @param finishDateTime The time when the plugin finished
     * @param reason A description of why the plugin finished
     * @param success Whether the plugin completed successfully
     */
    public PluginScheduleEntryFinishedEvent(Plugin plugin, ZonedDateTime finishDateTime, String reason, boolean success) {
        this.plugin = plugin;
        this.finishDateTime = finishDateTime;
        this.reason = reason;
        this.success = success;
    }
    
    /**
     * Creates a new plugin finished event with current time
     *
     * @param plugin The plugin that has finished
     * @param reason A description of why the plugin finished
     * @param success Whether the plugin completed successfully
     */
    public PluginScheduleEntryFinishedEvent(Plugin plugin, String reason, boolean success) {
        this(plugin, ZonedDateTime.now(), reason, success);
    }
}