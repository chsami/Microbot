package net.runelite.client.plugins.microbot.aiofighter.enums;

import java.util.concurrent.ThreadLocalRandom;

public enum PlayStyle {
    EXTREME_AGGRESSIVE("Extreme Aggressive", 1, 2), // Almost no break between inputs
    AGGRESSIVE("Aggressive", 1, 5),                // Very short breaks
    MODERATE("Moderate", 5, 10),                   // Short breaks
    BALANCED("Balanced", 10, 15),                  // Moderate breaks
    CAREFUL("Careful", 15, 20),                    // Longer breaks
    CAUTIOUS("Cautious", 20, 30),                  // Very long breaks
    PASSIVE("Passive", 25, 35);                    // Minimal actions

    private final String name;
    private final int primaryTickInterval;
    private final int secondaryTickInterval;

    PlayStyle(String name, int primaryTickInterval, int secondaryTickInterval) {
        this.name = name;
        this.primaryTickInterval = primaryTickInterval;
        this.secondaryTickInterval = secondaryTickInterval;
    }

    public String getName() {
        return name;
    }

    public int getPrimaryTickInterval() {
        return primaryTickInterval;
    }

    public int getSecondaryTickInterval() {
        return secondaryTickInterval;
    }

    public int getRandomTickInterval() {
        return ThreadLocalRandom.current().nextInt(primaryTickInterval, secondaryTickInterval + 1);
    }
}
