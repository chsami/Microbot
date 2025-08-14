package net.runelite.client.plugins.microbot.pluginscheduler.event;

import java.time.ZonedDateTime;

import lombok.Getter;
import net.runelite.client.plugins.Plugin;

/**
 * Event fired when a plugin should start its post-schedule tasks.
 * This is sent by the scheduler to plugins that implement SchedulablePlugin to initiate
 * their post-schedule task execution after the plugin has been stopped.
 */
@Getter
public class PluginScheduleEntryPostScheduleTaskEvent {
    private final Plugin plugin;
    private final ZonedDateTime stopDateTime;
    
    /**
     * Creates a new plugin post-schedule task event
     *
     * @param plugin The plugin that should start post-schedule tasks
     * @param stopDateTime The time when the plugin was stopped
     */
    public PluginScheduleEntryPostScheduleTaskEvent(Plugin plugin, ZonedDateTime stopDateTime) {
        this.plugin = plugin;
        this.stopDateTime = stopDateTime;
    }
    
    /**
     * Creates a new plugin post-schedule task event with current time
     *
     * @param plugin The plugin that should start post-schedule tasks
     * @param wasSuccessful Whether the plugin run was successful
     */
    public PluginScheduleEntryPostScheduleTaskEvent(Plugin plugin) {
        this(plugin, ZonedDateTime.now());
    }
}
