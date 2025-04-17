package net.runelite.client.plugins.microbot.frosty.frostyrc.enums;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

@Getter
public enum Teleports {
    FEROX_ENCLAVE("Ferox Enclave", new Integer[]{2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566}, new Integer[]{12600, 12344}, "Ferox Enclave"),
    HOUSE_TAB("Teleport to House", new Integer[]{8013}, null, "Break"),
    ARDOUGNE_CLOAK("Ardougne Cloak", new Integer[]{13121, 13122, 13123, 13124}, null, "Kandarin monastery"),
    CRAFTING_CAPE("Crafting Cape", new Integer[]{9780, 9781}, new Integer[]{11571}, "Teleport"),
    CONSTRUCTION_CAPE("Construction cape", new Integer[]{9789, 9790}, null, "Tele to POH"),
    MYTH_CAPE("Mythical cape", new Integer[]{ItemID.MYTHICAL_CAPE}, new Integer[]{9772}, "Teleport"),
    FARMING_CAPE("Farming cape", new Integer[]{9810, 9811}, new Integer[]{4922}, "Teleport");


    @Getter
    private final String name;
    @Getter
    private final Integer[] itemIds;
    private final Integer[] RegionIds;
    @Getter
    private final String interaction;

    Teleports(String name, Integer[] itemIds, Integer[] RegionIds, String interaction) {
        this.name = name;
        this.itemIds = itemIds;
        this.RegionIds = RegionIds;
        this.interaction = interaction;
    }


    public boolean matchesRegion(int regionId) {
        if (RegionIds == null) return false;
        for (int id : RegionIds) {
            if (id == regionId) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}
