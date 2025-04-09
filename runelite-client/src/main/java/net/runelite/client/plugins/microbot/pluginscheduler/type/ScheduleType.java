package net.runelite.client.plugins.microbot.pluginscheduler.type;

public enum ScheduleType {
    MINUTES("Minutes"),
    HOURS("Hours"),
    DAYS("Days");

    private final String displayName;

    ScheduleType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
