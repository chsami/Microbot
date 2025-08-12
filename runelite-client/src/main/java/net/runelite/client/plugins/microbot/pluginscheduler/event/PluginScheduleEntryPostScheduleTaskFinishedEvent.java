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
    private final boolean success;
    private final String message;
    
    /**
     * Creates a new plugin post-schedule task finished event
     *
     * @param plugin The plugin that finished post-schedule tasks
     * @param finishDateTime The time when the plugin finished
     * @param success Whether the post-schedule tasks completed successfully
     * @param message Optional message describing the completion
     */
    public PluginScheduleEntryPostScheduleTaskFinishedEvent(Plugin plugin, ZonedDateTime finishDateTime, boolean success, String message) {
        this.plugin = plugin;
        this.finishDateTime = finishDateTime;
        this.success = success;
        this.message = message;
    }
    
    /**
     * Creates a new plugin post-schedule task finished event with current time
     *
     * @param plugin The plugin that finished post-schedule tasks
     * @param success Whether the post-schedule tasks completed successfully
     * @param message Optional message describing the completion
     */
    public PluginScheduleEntryPostScheduleTaskFinishedEvent(Plugin plugin, boolean success, String message) {
        this(plugin, ZonedDateTime.now(), success, message);
    }
}
