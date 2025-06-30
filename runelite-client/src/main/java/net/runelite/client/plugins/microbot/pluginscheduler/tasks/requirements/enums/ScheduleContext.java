package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums;

public enum ScheduleContext {
    PRE_SCHEDULE,   // Before script execution (start location)
    POST_SCHEDULE,  // After script completion (end location)
    BOTH           // Both before and after (if same location needed for both)
}
