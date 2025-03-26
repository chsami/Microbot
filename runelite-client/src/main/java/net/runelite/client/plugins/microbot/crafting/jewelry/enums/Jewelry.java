package net.runelite.client.plugins.microbot.crafting.jewelry.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

import java.lang.reflect.Array;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum Jewelry {

    GOLD_RING("gold ring", ItemID.GOLD_RING, Gem.NONE, ItemID.RING_MOULD, JewelryType.GOLD, null, 5),
    GOLD_NECKLACE("gold necklace", ItemID.GOLD_NECKLACE, Gem.NONE, ItemID.NECKLACE_MOULD, JewelryType.GOLD, null, 6),
    GOLD_BRACELET("gold bracelet", ItemID.GOLD_BRACELET, Gem.NONE, ItemID.BRACELET_MOULD, JewelryType.GOLD, null, 7),
    OPAL_RING("opal ring", ItemID.OPAL_RING, Gem.OPAL, ItemID.RING_MOULD, JewelryType.SILVER, EnchantSpell.LEVEL_1, 1),
    OPAL_NECKLACE("opal necklace", ItemID.OPAL_NECKLACE, Gem.OPAL, ItemID.NECKLACE_MOULD, JewelryType.SILVER, EnchantSpell.LEVEL_1, 16),
