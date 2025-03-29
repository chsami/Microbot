package net.runelite.client.plugins.microbot.frosty.bloods.enums;

public enum HomeTeleports {
    CONSTRUCTION_CAPE("Construction cape", new Integer[]{9790}, "Tele to POH"),
    HOUSE_TAB("Teleport to House", new Integer[]{8013}, "Break");

    private final String name;
    private final Integer[] itemIds;
    private final String interaction;

    HomeTeleports(String name, Integer[] itemIds, String interaction) {
        this.name = name;
        this.itemIds = itemIds;
        this.interaction = interaction;
    }

    public String getName() {
        return name;
    }

    public Integer[] getItemIds() {
        return itemIds;
    }

    public String getInteraction() {
        return interaction;
    }

    @Override
    public String toString() {
        return name;
    }
}
