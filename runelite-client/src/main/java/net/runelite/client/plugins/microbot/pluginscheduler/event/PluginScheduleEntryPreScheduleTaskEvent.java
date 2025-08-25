package net.runelite.client.plugins.microbot.pluginscheduler.event;

import java.time.ZonedDateTime;

import lombok.Getter;
import net.runelite.client.plugins.Plugin;

/**
 * Event fired when a plugin should start its pre-schedule tasks.
 * This is sent by the scheduler to plugins that implement SchedulablePlugin to initiate
 * their pre-schedule task execution.
 */
@Getter
public class PluginScheduleEntryPreScheduleTaskEvent {
    private final Plugin plugin;
    private final ZonedDateTime startDateTime;
    private final boolean isSchedulerControlled;
    
    /**
     * Creates a new plugin pre-schedule task event
     *
     * @param plugin The plugin that should start pre-schedule tasks
     * @param startDateTime The time when the plugin should start
     * @param isSchedulerControlled Whether this plugin is under scheduler control
     */
    public PluginScheduleEntryPreScheduleTaskEvent(Plugin plugin, ZonedDateTime startDateTime, boolean isSchedulerControlled) {
        this.plugin = plugin;
        this.startDateTime = startDateTime;
        this.isSchedulerControlled = isSchedulerControlled;
    }
    
    /**
     * Creates a new plugin pre-schedule task event with current time
     *
     * @param plugin The plugin that should start pre-schedule tasks
     * @param isSchedulerControlled Whether this plugin is under scheduler control
     */
    public PluginScheduleEntryPreScheduleTaskEvent(Plugin plugin, boolean isSchedulerControlled) {
        this(plugin, ZonedDateTime.now(), isSchedulerControlled);
    }
}
