package net.runelite.client.plugins.microbot.frosty.bloods.enums;

import lombok.Getter;

@Getter
public enum Teleports {
    CRAFTING_CAPE("Crafting Cape", new Integer[]{9781}, 11571),
    RING_OF_DUELING("Ring of Dueling", new Integer[]{2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566}, 9776);

    private final String name;
    private final Integer[] itemIds;
    private final int bankingRegionId;

    Teleports(String name, Integer[] itemIds, int bankingRegionId) {
        this.name = name;
        this.itemIds = itemIds;
        this.bankingRegionId = bankingRegionId;
    }

    @Override
    public String toString() {
        return name;
    }
}

