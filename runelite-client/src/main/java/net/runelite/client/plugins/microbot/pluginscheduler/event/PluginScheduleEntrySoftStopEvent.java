package net.runelite.client.plugins.microbot.pluginscheduler.event;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import lombok.Getter;
import net.runelite.client.plugins.Plugin;

/**
 * Event fired when a scheduled plugin should be stopped
 */
@Getter
public class PluginScheduleEntrySoftStopEvent {
    private final Plugin plugin;
    private final ZonedDateTime stopDateTime;
    public PluginScheduleEntrySoftStopEvent(Plugin plugin, ZonedDateTime stopDateTime) {
        this.plugin = plugin;
        this.stopDateTime = stopDateTime;
    }
}