package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.enums;
 /**
 * Enumeration of supported repeat cycle types
 */
public enum RepeatCycle {
    MILLIS("Every X milliseconds"),
    SECONDS("Every X seconds"),
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
    public String unit() {
        switch (this) {
            case MILLIS:
                return "ms";
            case SECONDS:
                return "s";
            case MINUTES:
                return "min";
            case HOURS:
                return "h";
            case DAYS:
                return "d";
            case WEEKS:
                return "w";
            default:
                return "";
        }
    }
}