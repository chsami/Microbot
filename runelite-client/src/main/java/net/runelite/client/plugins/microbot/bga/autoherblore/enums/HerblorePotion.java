package net.runelite.client.plugins.microbot.bga.autoherblore.enums;

import net.runelite.api.gameval.ItemID;

public enum HerblorePotion {
    AGILITY("Agility potion", 34, ItemID.TOADFLAXVIAL, ItemID.TOADS_LEGS),
    ANTI_VENOM("Anti-venom potion", 87, ItemID.ANTIDOTE__4, ItemID.SNAKEBOSS_SCALE),
    ANTI_VENOM_PLUS("Anti-venom+ potion", 94, ItemID.ANTIVENOM4, ItemID.TORSTOL),
    ANTIDOTE_PLUS("Antidote+ potion", 68, ItemID.VIAL_COCONUT_MILK, ItemID.TOADFLAX),
    ANTIDOTE_PLUS_PLUS("Antidote++ potion", 79, ItemID.VIAL_COCONUT_MILK, ItemID.IRIT_LEAF),
    ANTIFIRE("Antifire potion", 69, ItemID.LANTADYMEVIAL, ItemID.DRAGON_SCALE_DUST),
    ANTIPOISON("Antipoison potion", 5, ItemID.MARRENTILLVIAL, ItemID.UNICORN_HORN_DUST),
    ATTACK("Attack potion", 1, ItemID.GUAMVIAL, ItemID.EYE_OF_NEWT),
    BASTION("Bastion potion", 80, ItemID.CADANTINE_BLOODVIAL, ItemID.WINE_OF_ZAMORAK),
    BATTLEMAGE("Battlemage potion", 80, ItemID.CADANTINE_BLOODVIAL, ItemID.CACTUS_POTATO),
    COMBAT("Combat potion", 36, ItemID.HARRALANDERVIAL, ItemID.GROUND_DESERT_GOAT_HORN),
    DEFENCE("Defence potion", 30, ItemID.RANARRVIAL, ItemID.WHITE_BERRIES),
    DIVINE_BASTION("Divine bastion potion", 86, ItemID._4DOSEBASTION, ItemID.PRIF_CRYSTAL_SHARD_CRUSHED),
    DIVINE_BATTLEMAGE("Divine battlemage potion", 86, ItemID._4DOSEBATTLEMAGE, ItemID.PRIF_CRYSTAL_SHARD_CRUSHED),
    DIVINE_MAGIC("Divine magic potion", 74, ItemID._4DOSE1MAGIC, ItemID.PRIF_CRYSTAL_SHARD_CRUSHED),
    DIVINE_RANGING("Divine ranging potion", 74, ItemID._4DOSERANGERSPOTION, ItemID.PRIF_CRYSTAL_SHARD_CRUSHED),
    DIVINE_SUPER_ATTACK("Divine super attack potion", 70, ItemID._4DOSE2ATTACK, ItemID.PRIF_CRYSTAL_SHARD_CRUSHED),
    DIVINE_SUPER_COMBAT("Divine super combat potion", 97, ItemID._4DOSE2COMBAT, ItemID.PRIF_CRYSTAL_SHARD_CRUSHED),
    DIVINE_SUPER_DEFENCE("Divine super defence potion", 70, ItemID._4DOSE2DEFENSE, ItemID.PRIF_CRYSTAL_SHARD_CRUSHED),
    DIVINE_SUPER_STRENGTH("Divine super strength potion", 70, ItemID._4DOSE2STRENGTH, ItemID.PRIF_CRYSTAL_SHARD_CRUSHED),
    ENERGY("Energy potion", 26, ItemID.HARRALANDERVIAL, ItemID.CHOCOLATE_DUST),
    EXTENDED_ANTI_VENOM_PLUS("Extended anti-venom+ potion", 94, ItemID.ANTIVENOM_4, ItemID.ARAXYTE_VENOM_SACK),
    EXTENDED_ANTIFIRE("Extended antifire potion", 84, ItemID._4DOSE1ANTIDRAGON, ItemID.LAVA_SHARD),
    EXTENDED_SUPER_ANTIFIRE("Extended super antifire potion", 98, ItemID._4DOSE3ANTIDRAGON, ItemID.LAVA_SHARD),
    FISHING("Fishing potion", 50, ItemID.AVANTOEVIAL, ItemID.SNAPE_GRASS),
    HUNTER("Hunter potion", 53, ItemID.AVANTOEVIAL, ItemID.HUNTINGBEAST_SABRETEETH_DUST),
    MAGIC("Magic potion", 76, ItemID.LANTADYMEVIAL, ItemID.CACTUS_POTATO),
    MAGIC_ESSENCE("Magic essence potion", 57, ItemID.FAIRYTALE2_STARFLOWERVIAL, ItemID.FAIRYTALE2_GROUND_GORAK_CLAWS),
    PRAYER("Prayer potion", 38, ItemID.RANARRVIAL, ItemID.SNAPE_GRASS),
    RANGING("Ranging potion", 72, ItemID.DWARFWEEDVIAL, ItemID.WINE_OF_ZAMORAK),
    RELICYMS_BALM("Relicym's balm", 8, ItemID.VIAL_WATER, ItemID.ROGUES_PURSE),
    RESTORE("Restore potion", 22, ItemID.HARRALANDERVIAL, ItemID.RED_SPIDERS_EGGS),
    SANFEW_SERUM("Sanfew serum", 65, ItemID._4DOSE2RESTORE, ItemID.UNICORN_HORN_DUST),
    SARADOMIN_BREW("Saradomin brew", 81, ItemID.TOADFLAXVIAL, ItemID.CRUSHED_BIRD_NEST),
    STAMINA("Stamina potion", 77, ItemID._4DOSE2ENERGY, ItemID.AMYLASE),
    STRENGTH("Strength potion", 12, ItemID.TARROMINVIAL, ItemID.LIMPWURT_ROOT),
    SUPER_ANTIFIRE("Super antifire potion", 92, ItemID._4DOSE1ANTIDRAGON, ItemID.CRUSHED_DRAGON_BONES),
    SUPER_ANTIPOISON("Super antipoison potion", 48, ItemID.IRITVIAL, ItemID.UNICORN_HORN_DUST),
    SUPER_ATTACK("Super attack potion", 45, ItemID.IRITVIAL, ItemID.EYE_OF_NEWT),
    SUPER_COMBAT("Super combat potion", 90, ItemID.TORSTOL, ItemID._4DOSE2ATTACK),
    SUPER_DEFENCE("Super defence potion", 66, ItemID.CADANTINEVIAL, ItemID.WHITE_BERRIES),
    SUPER_ENERGY("Super energy potion", 52, ItemID.AVANTOEVIAL, ItemID.MORTMYREMUSHROOM),
    SUPER_RESTORE("Super restore potion", 63, ItemID.SNAPDRAGONVIAL, ItemID.RED_SPIDERS_EGGS),
    SUPER_STRENGTH("Super strength potion", 55, ItemID.KWUARMVIAL, ItemID.LIMPWURT_ROOT),
    WEAPON_POISON("Weapon poison", 60, ItemID.KWUARMVIAL, ItemID.DRAGON_SCALE_DUST),
    WEAPON_POISON_PLUS("Weapon poison+", 73, ItemID.VIAL_COCONUT_MILK, ItemID.CACTUS_SPINE),
    WEAPON_POISON_PLUS_PLUS("Weapon poison++", 82, ItemID.VIAL_COCONUT_MILK, ItemID.NIGHTSHADE),
    ZAMORAK_BREW("Zamorak brew", 78, ItemID.TORSTOLVIAL, ItemID.JANGERBERRIES);

    private final String displayName;
    public final int level;
    public final int unfinished;
    public final int secondary;

    HerblorePotion(String displayName, int level, int unfinished, int secondary) {
        this.displayName = displayName;
        this.level = level;
        this.unfinished = unfinished;
        this.secondary = secondary;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
