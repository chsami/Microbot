package net.runelite.client.plugins.microbot.woodcutting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WoodcuttingSecondaryAction {
    NONE("No secondary action"),
    BANK("Bank fletched items"),
    DROP("Drop fletched items"),
    STRING_AND_DROP("String bows (if bowstring available)"),
    STRING_AND_BANK("String bows (if bowstring available) and bank fletched items");

    private final String description;

    @Override
    public String toString() {
        return description;
    }
}