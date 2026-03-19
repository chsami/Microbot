package net.runelite.client.plugins.microbot.commandcenter.scripts.cooking;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CookableFood {
    SHRIMP("Raw shrimps", "Shrimps"),
    TROUT("Raw trout", "Trout"),
    LOBSTER("Raw lobster", "Lobster"),
    SWORDFISH("Raw swordfish", "Swordfish");

    private final String rawName;
    private final String cookedName;
}
