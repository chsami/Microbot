package net.runelite.client.plugins.microbot.bga.autoherblore.enums;

public enum Mode {
    CLEAN_HERBS("Clean herbs"),
    UNFINISHED_POTIONS("Make unfinished potions"),
    FINISHED_POTIONS("Make finished potions");

    private final String displayName;

    Mode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
