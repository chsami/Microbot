
// DEPRECATED: This event class is deprecated and will be removed in a future release.
// Use PluginScheduleEntryPostScheduleTaskEvent instead for post-schedule task signaling.
// (See PluginScheduleEntryPostScheduleTaskEvent.java)
//
// This class remains only for backward compatibility and migration purposes.
// Please update all usages to the new event class as soon as possible.

// TODO: Remove this class after migration to PluginScheduleEntryPostScheduleTaskEvent is complete.
//       (Scheduled for removal in the next major version.)
//
// Replacement: PluginScheduleEntryPostScheduleTaskEvent
//
// ---
// Original Javadoc below:
//
// Event fired when a plugin should start its post-schedule tasks.
// This is sent by the scheduler to plugins that implement SchedulablePlugin to initiate
// their post-schedule task execution after the plugin has been stopped.

package net.runelite.client.plugins.microbot.pluginscheduler.event;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import lombok.Getter;
import net.runelite.client.plugins.Plugin;

/**
 * Event fired when a scheduled plugin should be stopped
 */
/**
* @deprecated Use {@link net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPostScheduleTaskEvent} instead.
*/
@Deprecated
public class PluginScheduleEntrySoftStopEvent {
    @Getter
    private final Plugin plugin;
    @Getter
    private final ZonedDateTime stopDateTime;
    public PluginScheduleEntrySoftStopEvent(Plugin plugin, ZonedDateTime stopDateTime) {
        this.plugin = plugin;
        this.stopDateTime = stopDateTime;
    }
}