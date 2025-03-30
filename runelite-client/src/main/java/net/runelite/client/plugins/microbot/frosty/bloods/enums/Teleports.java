package net.runelite.client.plugins.microbot.frosty.bloods.enums;

import lombok.Getter;

@Getter
public enum Teleports {
    CRAFTING_CAPE("Crafting Cape", new Integer[]{9781}, 11571, "Teleport"), // Fixed missing comma
    FEROX_ENCLAVE("Ferox Enclave", new Integer[]{2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566}, 12600, "Ferox Enclave");

    private final String name;
    private final Integer[] itemIds;
    private final int bankingRegionId;
    private final String interaction;

    // Constructor with all required fields
    Teleports(String name, Integer[] itemIds, int bankingRegionId, String interaction) {
        this.name = name;
        this.itemIds = itemIds;
        this.bankingRegionId = bankingRegionId;
        this.interaction = interaction;
    }

    @Override
    public String toString() {
        return name;
    }
}
