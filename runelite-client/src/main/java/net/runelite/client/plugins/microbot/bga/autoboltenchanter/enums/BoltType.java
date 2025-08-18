package net.runelite.client.plugins.microbot.bga.autoboltenchanter.enums;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

public enum BoltType {
    OPAL("Opal bolts", ItemID.OPAL_BOLTS, ItemID.XBOWS_CROSSBOW_BOLTS_BRONZE_TIPPED_OPAL_ENCHANTED, 4, MagicAction.ENCHANT_OPAL_BOLT,
            new int[]{ItemID.AIRRUNE, ItemID.COSMICRUNE}, new int[]{2, 1}),
    
    SAPPHIRE("Sapphire bolts", ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_SAPPHIRE, ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_SAPPHIRE_ENCHANTED, 7, MagicAction.ENCHANT_SAPPHIRE_BOLT,
            new int[]{ItemID.WATERRUNE, ItemID.COSMICRUNE, ItemID.MINDRUNE}, new int[]{1, 1, 1}),
    
    JADE("Jade bolts", ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_TIPPED_JADE, ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_TIPPED_JADE_ENCHANTED, 14, MagicAction.ENCHANT_JADE_BOLT,
            new int[]{ItemID.EARTHRUNE, ItemID.COSMICRUNE}, new int[]{2, 1}),
    
    PEARL("Pearl bolts", ItemID.PEARL_BOLTS, ItemID.XBOWS_CROSSBOW_BOLTS_IRON_TIPPED_PEARL_ENCHANTED, 24, MagicAction.ENCHANT_PEARL_BOLT,
            new int[]{ItemID.WATERRUNE, ItemID.COSMICRUNE}, new int[]{2, 1}),
    
    EMERALD("Emerald bolts", ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_EMERALD, ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_EMERALD_ENCHANTED, 27, MagicAction.ENCHANT_EMERALD_BOLT,
            new int[]{ItemID.AIRRUNE, ItemID.COSMICRUNE, ItemID.NATURERUNE}, new int[]{3, 1, 1}),
    
    TOPAZ("Topaz bolts", ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_TIPPED_REDTOPAZ, ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_TIPPED_REDTOPAZ_ENCHANTED, 29, MagicAction.ENCHANT_TOPAZ_BOLT,
            new int[]{ItemID.FIRERUNE, ItemID.COSMICRUNE}, new int[]{2, 1}),
    
    RUBY("Ruby bolts", ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_RUBY, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_RUBY_ENCHANTED, 49, MagicAction.ENCHANT_RUBY_BOLT,
            new int[]{ItemID.FIRERUNE, ItemID.BLOODRUNE, ItemID.COSMICRUNE}, new int[]{5, 1, 1}),
    
    DIAMOND("Diamond bolts", ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_DIAMOND, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_DIAMOND_ENCHANTED, 57, MagicAction.ENCHANT_DIAMOND_BOLT,
            new int[]{ItemID.EARTHRUNE, ItemID.COSMICRUNE, ItemID.LAWRUNE}, new int[]{10, 1, 2}),
    
    DRAGONSTONE("Dragonstone bolts", ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_DRAGONSTONE, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_DRAGONSTONE_ENCHANTED, 68, MagicAction.ENCHANT_DRAGONSTONE_BOLT,
            new int[]{ItemID.EARTHRUNE, ItemID.COSMICRUNE, ItemID.SOULRUNE}, new int[]{15, 1, 1}),
    
    ONYX("Onyx bolts", ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX_ENCHANTED, 87, MagicAction.ENCHANT_ONYX_BOLT,
            new int[]{ItemID.FIRERUNE, ItemID.COSMICRUNE, ItemID.DEATHRUNE}, new int[]{20, 1, 1});

    private final String name;
    private final int unenchantedId;
    private final int enchantedId;
    private final int levelRequired;
    private final MagicAction magicAction;
    private final int[] runeIds;
    private final int[] runeQuantities;

    BoltType(String name, int unenchantedId, int enchantedId, int levelRequired, MagicAction magicAction, int[] runeIds, int[] runeQuantities) {
        this.name = name;
        this.unenchantedId = unenchantedId;
        this.enchantedId = enchantedId;
        this.levelRequired = levelRequired;
        this.magicAction = magicAction;
        this.runeIds = runeIds;
        this.runeQuantities = runeQuantities;
    }

    public String getName() {
        return name;
    }

    public int getUnenchantedId() {
        return unenchantedId;
    }

    public int getEnchantedId() {
        return enchantedId;
    }

    public int getLevelRequired() {
        return levelRequired;
    }

    public MagicAction getMagicAction() {
        return magicAction;
    }

    public int[] getRuneIds() {
        return runeIds;
    }

    public int[] getRuneQuantities() {
        return runeQuantities;
    }

    @Override
    public String toString() {
        return name;
    }
}