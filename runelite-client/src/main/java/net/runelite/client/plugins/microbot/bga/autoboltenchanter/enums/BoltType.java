package net.runelite.client.plugins.microbot.bga.autoboltenchanter.enums;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

public enum BoltType {
    // All bolt types ordered by magic level and type
    OPAL("Opal bolts", "Opal bolts (e)", ItemID.OPAL_BOLT, ItemID.XBOWS_CROSSBOW_BOLTS_BRONZE_TIPPED_OPAL_ENCHANTED, 4,
            new int[]{ItemID.AIRRUNE, ItemID.COSMICRUNE}, new int[]{2, 1}),
    
    OPAL_DRAGON("Opal dragon bolts", "Opal dragon bolts (e)", ItemID.DRAGON_BOLTS_UNENCHANTED_OPAL, ItemID.DRAGON_BOLTS_ENCHANTED_OPAL, 4,
            new int[]{ItemID.AIRRUNE, ItemID.COSMICRUNE}, new int[]{2, 1}),
    
    SAPPHIRE("Sapphire bolts", "Sapphire bolts (e)", ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_SAPPHIRE, ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_SAPPHIRE_ENCHANTED, 7,
            new int[]{ItemID.WATERRUNE, ItemID.COSMICRUNE, ItemID.MINDRUNE}, new int[]{1, 1, 1}),
    
    SAPPHIRE_DRAGON("Sapphire dragon bolts", "Sapphire dragon bolts (e)", ItemID.DRAGON_BOLTS_UNENCHANTED_SAPPHIRE, ItemID.DRAGON_BOLTS_ENCHANTED_SAPPHIRE, 7,
            new int[]{ItemID.WATERRUNE, ItemID.COSMICRUNE, ItemID.MINDRUNE}, new int[]{1, 1, 1}),
    
    JADE("Jade bolts", "Jade bolts (e)", ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_TIPPED_JADE, ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_TIPPED_JADE_ENCHANTED, 14,
            new int[]{ItemID.EARTHRUNE, ItemID.COSMICRUNE}, new int[]{2, 1}),
    
    JADE_DRAGON("Jade dragon bolts", "Jade dragon bolts (e)", ItemID.DRAGON_BOLTS_UNENCHANTED_JADE, ItemID.DRAGON_BOLTS_ENCHANTED_JADE, 14,
            new int[]{ItemID.EARTHRUNE, ItemID.COSMICRUNE}, new int[]{2, 1}),
    
    PEARL("Pearl bolts", "Pearl bolts (e)", ItemID.PEARL_BOLTS, ItemID.XBOWS_CROSSBOW_BOLTS_IRON_TIPPED_PEARL_ENCHANTED, 24,
            new int[]{ItemID.WATERRUNE, ItemID.COSMICRUNE}, new int[]{2, 1}),
    
    PEARL_DRAGON("Pearl dragon bolts", "Pearl dragon bolts (e)", ItemID.DRAGON_BOLTS_UNENCHANTED_PEARL, ItemID.DRAGON_BOLTS_ENCHANTED_PEARL, 24,
            new int[]{ItemID.WATERRUNE, ItemID.COSMICRUNE}, new int[]{2, 1}),
    
    EMERALD("Emerald bolts", "Emerald bolts (e)", ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_EMERALD, ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_TIPPED_EMERALD_ENCHANTED, 27,
            new int[]{ItemID.AIRRUNE, ItemID.COSMICRUNE, ItemID.NATURERUNE}, new int[]{3, 1, 1}),
    
    EMERALD_DRAGON("Emerald dragon bolts", "Emerald dragon bolts (e)", ItemID.DRAGON_BOLTS_UNENCHANTED_EMERALD, ItemID.DRAGON_BOLTS_ENCHANTED_EMERALD, 27,
            new int[]{ItemID.AIRRUNE, ItemID.COSMICRUNE, ItemID.NATURERUNE}, new int[]{3, 1, 1}),
    
    TOPAZ("Topaz bolts", "Topaz bolts (e)", ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_TIPPED_REDTOPAZ, ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_TIPPED_REDTOPAZ_ENCHANTED, 29,
            new int[]{ItemID.FIRERUNE, ItemID.COSMICRUNE}, new int[]{2, 1}),
    
    TOPAZ_DRAGON("Topaz dragon bolts", "Topaz dragon bolts (e)", ItemID.DRAGON_BOLTS_UNENCHANTED_TOPAZ, ItemID.DRAGON_BOLTS_ENCHANTED_TOPAZ, 29,
            new int[]{ItemID.FIRERUNE, ItemID.COSMICRUNE}, new int[]{2, 1}),
    
    RUBY("Ruby bolts", "Ruby bolts (e)", ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_RUBY, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_RUBY_ENCHANTED, 49,
            new int[]{ItemID.FIRERUNE, ItemID.BLOODRUNE, ItemID.COSMICRUNE}, new int[]{5, 1, 1}),
    
    RUBY_DRAGON("Ruby dragon bolts", "Ruby dragon bolts (e)", ItemID.DRAGON_BOLTS_UNENCHANTED_RUBY, ItemID.DRAGON_BOLTS_ENCHANTED_RUBY, 49,
            new int[]{ItemID.FIRERUNE, ItemID.BLOODRUNE, ItemID.COSMICRUNE}, new int[]{5, 1, 1}),
    
    DIAMOND("Diamond bolts", "Diamond bolts (e)", ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_DIAMOND, ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_TIPPED_DIAMOND_ENCHANTED, 57,
            new int[]{ItemID.EARTHRUNE, ItemID.COSMICRUNE, ItemID.LAWRUNE}, new int[]{10, 1, 2}),
    
    DIAMOND_DRAGON("Diamond dragon bolts", "Diamond dragon bolts (e)", ItemID.DRAGON_BOLTS_UNENCHANTED_DIAMOND, ItemID.DRAGON_BOLTS_ENCHANTED_DIAMOND, 57,
            new int[]{ItemID.EARTHRUNE, ItemID.COSMICRUNE, ItemID.LAWRUNE}, new int[]{10, 1, 2}),
    
    DRAGONSTONE("Dragonstone bolts", "Dragonstone bolts (e)", ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_DRAGONSTONE, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_DRAGONSTONE_ENCHANTED, 68,
            new int[]{ItemID.EARTHRUNE, ItemID.COSMICRUNE, ItemID.SOULRUNE}, new int[]{15, 1, 1}),
    
    DRAGONSTONE_DRAGON("Dragonstone dragon bolts", "Dragonstone dragon bolts (e)", ItemID.DRAGON_BOLTS_UNENCHANTED_DRAGONSTONE, ItemID.DRAGON_BOLTS_ENCHANTED_DRAGONSTONE, 68,
            new int[]{ItemID.EARTHRUNE, ItemID.COSMICRUNE, ItemID.SOULRUNE}, new int[]{15, 1, 1}),
    
    ONYX("Onyx bolts", "Onyx bolts (e)", ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX_ENCHANTED, 87,
            new int[]{ItemID.FIRERUNE, ItemID.COSMICRUNE, ItemID.DEATHRUNE}, new int[]{20, 1, 1}),
    
    ONYX_DRAGON("Onyx dragon bolts", "Onyx dragon bolts (e)", ItemID.DRAGON_BOLTS_UNENCHANTED_ONYX, ItemID.DRAGON_BOLTS_ENCHANTED_ONYX, 87,
            new int[]{ItemID.FIRERUNE, ItemID.COSMICRUNE, ItemID.DEATHRUNE}, new int[]{20, 1, 1});

    private final String name;
    private final String enchantedName;
    private final int unenchantedId;
    private final int enchantedId;
    private final int levelRequired;
    private final int[] runeIds;
    private final int[] runeQuantities;

    BoltType(String name, String enchantedName, int unenchantedId, int enchantedId, int levelRequired, int[] runeIds, int[] runeQuantities) {
        this.name = name;
        this.enchantedName = enchantedName;
        this.unenchantedId = unenchantedId;
        this.enchantedId = enchantedId;
        this.levelRequired = levelRequired;
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

    public String getEnchantedName() {
        return enchantedName;
    }

    public static MagicAction getCrossbowBoltSpell() {
        return MagicAction.ENCHANT_SAPPHIRE_BOLT; // all bolt types use the same crossbow bolt spell
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