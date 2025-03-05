package net.runelite.client.plugins.microbot.frosty.trueblood.enums;

public enum HomeTeleports {
    CONSTRUCTION_CAPE("Construction cape", new Integer[]{9790}),
    HOUSE_TAB("Teleport to Home", new Integer[]{8013});

    private final String name;
    private final Integer[] itemIds;
    HomeTeleports(String name, Integer[] itemIds) {
        this.name = name;
        this.itemIds = itemIds;
    }

    public String getName() {
        return name;
    }

    public Integer[] getItemIds() {
        return itemIds;
    }

    @Override
    public String toString() {
        return name;
    }
}
