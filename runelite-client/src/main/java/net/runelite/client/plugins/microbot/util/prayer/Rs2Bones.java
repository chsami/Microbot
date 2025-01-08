package net.runelite.client.plugins.microbot.util.prayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

@Getter
@RequiredArgsConstructor
public enum Rs2Bones {
    BONES(ItemID.BONES), // 4.5 XP
    BURNT_BONES(ItemID.BURNT_BONES), // 4.5 XP
    WOLF_BONES(ItemID.WOLF_BONES), // 4.5 XP
    MONKEY_BONES(ItemID.MONKEY_BONES), // 5 XP
    BAT_BONES(ItemID.BAT_BONES), // 5.3 XP
    BIG_BONES(ItemID.BIG_BONES), // 15 XP
    ZOGRE_BONES(ItemID.ZOGRE_BONES), // 22.5 XP
    SHAIKAHAN_BONES(ItemID.SHAIKAHAN_BONES), // 25 XP
    BABYDRAGON_BONES(ItemID.BABYDRAGON_BONES), // 30 XP
    WYRM_BONES(ItemID.WYRM_BONES), // 50 XP
    DRAGON_BONES(ItemID.DRAGON_BONES), // 72 XP
    DRAKE_BONES(ItemID.DRAKE_BONES), // 80 XP
    FAYRG_BONES(ItemID.FAYRG_BONES), // 84 XP
    RAURG_BONES(ItemID.RAURG_BONES), // 96 XP
    DAGANNOTH_BONES(ItemID.DAGANNOTH_BONES), // 125 XP
    OURG_BONES(ItemID.OURG_BONES), // 140 XP
    LAVA_DRAGON_BONES(ItemID.LAVA_DRAGON_BONES), // 85 XP
    WYVERN_BONES(ItemID.WYVERN_BONES), // 72 XP
    HYDRA_BONES(ItemID.HYDRA_BONES), // 110 XP
    SUPERIOR_DRAGON_BONES(ItemID.SUPERIOR_DRAGON_BONES); // 150 XP

    private final int itemID;
}

