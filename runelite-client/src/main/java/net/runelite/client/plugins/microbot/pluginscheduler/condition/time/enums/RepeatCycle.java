package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.enums;
 /**
 * Enumeration of supported repeat cycle types
 */
public enum RepeatCycle {
    MINUTES("Every X minutes"),
    HOURS("Every X hours"),
    DAYS("Every X days"),
    WEEKS("Every X weeks"),
    ONE_TIME("One time only");

    private final String displayName;

    RepeatCycle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}