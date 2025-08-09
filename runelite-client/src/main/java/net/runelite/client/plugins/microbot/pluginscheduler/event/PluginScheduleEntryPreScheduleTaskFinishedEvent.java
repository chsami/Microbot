package net.runelite.client.plugins.microbot.pluginscheduler.event;

import java.time.ZonedDateTime;

import lombok.Getter;
import net.runelite.client.plugins.Plugin;

/**
 * Event fired when a plugin's pre-schedule tasks have finished.
 * This is sent by plugins back to the scheduler to indicate completion of pre-schedule tasks.
 */
@Getter
public class PluginScheduleEntryPreScheduleTaskFinishedEvent {
    private final Plugin plugin;
    private final ZonedDateTime finishDateTime;
    private final boolean success;
    private final String message;
    
    /**
     * Creates a new plugin pre-schedule task finished event
     *
     * @param plugin The plugin that finished pre-schedule tasks
     * @param finishDateTime The time when the plugin finished
     * @param success Whether the pre-schedule tasks completed successfully
     * @param message Optional message describing the completion
     */
    public PluginScheduleEntryPreScheduleTaskFinishedEvent(Plugin plugin, ZonedDateTime finishDateTime, boolean success, String message) {
        this.plugin = plugin;
        this.finishDateTime = finishDateTime;
        this.success = success;
        this.message = message;
    }
    
    /**
     * Creates a new plugin pre-schedule task finished event with current time
     *
     * @param plugin The plugin that finished pre-schedule tasks
     * @param success Whether the pre-schedule tasks completed successfully
     * @param message Optional message describing the completion
     */
    public PluginScheduleEntryPreScheduleTaskFinishedEvent(Plugin plugin, boolean success, String message) {
        this(plugin, ZonedDateTime.now(), success, message);
    }
}
