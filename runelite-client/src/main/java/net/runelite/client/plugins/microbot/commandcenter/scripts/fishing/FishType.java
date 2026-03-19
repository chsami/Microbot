package net.runelite.client.plugins.microbot.commandcenter.scripts.fishing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FishType {
    SHRIMP("Fishing spot", "Net", "Raw shrimps"),
    TROUT("Rod Fishing spot", "Lure", "Raw trout"),
    LOBSTER("Fishing spot", "Cage", "Raw lobster"),
    SWORDFISH("Fishing spot", "Harpoon", "Raw swordfish");

    private final String npcName;
    private final String action;
    private final String fishName;
}
