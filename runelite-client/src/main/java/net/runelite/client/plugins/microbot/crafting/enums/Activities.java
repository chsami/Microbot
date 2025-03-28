package net.runelite.client.plugins.microbot.crafting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Activities {
    NONE(" "),
    // DEFAULT("Default Script"),
    GEM_CUTTING("Cutting Gems"),
    GLASSBLOWING("Glassblowing"),
    STAFF_MAKING("Staff Making"),
    FLAX_SPINNING("Flax Spinning"),
    CUTTING_AMETHYST("Cutting Amethyst"),
    WEAVING_NETS("Weaving Nets");

    private final String name;

    @Override
    public String toString() {
        return name;
    }
}
