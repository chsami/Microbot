package net.runelite.client.plugins.microbot.pluginscheduler.event;

import lombok.Getter;
import net.runelite.client.plugins.Plugin;

/**
 * Event fired when a scheduled plugin should be stopped
 */
@Getter
public class ScheduledStopEvent {
    private final Plugin plugin;

    public ScheduledStopEvent(Plugin plugin) {
        this.plugin = plugin;
    }
}