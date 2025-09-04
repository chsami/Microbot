package net.runelite.client.plugins.microbot.woodcutting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WoodcuttingPrimaryAction {
    BANK("Bank logs"),
    DROP("Drop logs"),
    BURN("Burn logs"),
    BURN_CAMPFIRE("Burn logs at campfire"),
    FLETCH("Fletch logs");

    private final String description;

    @Override
    public String toString() {
        return description;
    }
}