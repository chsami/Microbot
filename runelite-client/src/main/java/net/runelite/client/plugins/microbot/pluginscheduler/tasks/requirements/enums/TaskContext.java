package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums;

public enum TaskContext {
    PRE_SCHEDULE,   // Before script execution (start location) -> can also be meant to be used as a pre task in script
    POST_SCHEDULE,  // After script completion (end location) -> can also be ment to be use a  post task in script
    BOTH           // Both before and after (if same location needed for both pre post schedule)
}
