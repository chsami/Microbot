package net.runelite.client.plugins.microbot.aiofighter.enums;

import lombok.Getter;

@Getter
public enum PrayerStyle {
    LAZY_FLICK("Lazy Flick", "Flicks tick before hit"),
    PERFECT_LAZY_FLICK("Perfect Lazy Flick", "Flicks on hit"),
    CONTINUOUS("Continuous", "Quick prayer is on when in combat"),
    ALWAYS_ON("Always On", "Quick prayer is always on");

    private final String name;
    private final String description;

    PrayerStyle(String name, String description) {
        this.name = name;
        this.description = description;
    }

}

