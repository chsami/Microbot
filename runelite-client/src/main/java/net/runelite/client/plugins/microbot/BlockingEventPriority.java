package net.runelite.client.plugins.microbot;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BlockingEventPriority {
    HIGHEST(20),
    HIGH(10),
    NORMAL(0),
    LOW(-10),
    LOWEST(-20);

    private final int level;
}
