package net.runelite.client.plugins.microbot.bga.autoherblore.enums;

import net.runelite.api.gameval.ItemID;

public enum HerblorePotion {
    ATTACK(1, ItemID.GUAMVIAL, ItemID.EYE_OF_NEWT),
    ANTIPOISON(5, ItemID.MARRENTILLVIAL, ItemID.UNICORN_HORN_DUST),
    STRENGTH(12, ItemID.TARROMINVIAL, ItemID.LIMPWURT_ROOT),
    ENERGY(26, ItemID.HARRALANDERVIAL, ItemID.CHOCOLATE_DUST),
    DEFENCE(30, ItemID.RANARRVIAL, ItemID.WHITE_BERRIES),
    PRAYER(38, ItemID.RANARRVIAL, ItemID.SNAPE_GRASS),
    SUPER_ATTACK(45, ItemID.IRITVIAL, ItemID.EYE_OF_NEWT),
    SUPER_STRENGTH(55, ItemID.KWUARMVIAL, ItemID.LIMPWURT_ROOT),
    SUPER_RESTORE(63, ItemID.SNAPDRAGONVIAL, ItemID.RED_SPIDERS_EGGS),
    SUPER_DEFENCE(66, ItemID.CADANTINEVIAL, ItemID.WHITE_BERRIES),
    RANGING(72, ItemID.DWARFWEEDVIAL, ItemID.WINE_OF_ZAMORAK),
    MAGIC(76, ItemID.LANTADYMEVIAL, ItemID.CACTUS_POTATO);
    public final int level;
    public final int unfinished;
    public final int secondary;
    HerblorePotion(int level, int unfinished, int secondary) {
        this.level = level;
        this.unfinished = unfinished;
        this.secondary = secondary;
    }
}
