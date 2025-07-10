package net.runelite.client.plugins.microbot.farmTreeRun.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum TreeEnums {
    OAK("Oak sapling", ItemID.PLANTPOT_OAK_SAPLING, ItemID.BASKET_TOMATO_5, 1,15),
    WILLOW("Willow sapling", ItemID.PLANTPOT_WILLOW_SAPLING, ItemID.BASKET_APPLE_5, 1,30),
    MAPLE("Maple sapling", ItemID.PLANTPOT_MAPLE_SAPLING, ItemID.BASKET_ORANGE_5, 1,45),
    YEW("Yew sapling", ItemID.PLANTPOT_YEW_SAPLING, ItemID.CACTUS_SPINE, 10,60),
    MAGIC("Magic sapling", ItemID.PLANTPOT_MAGIC_TREE_SAPLING, ItemID.COCONUT, 25,75);


    private final String name;
    private final int saplingId;
    private final int paymentId;
    private final int paymentAmount;
    private final int farmingLevel;

    @Override
    public String toString() {
        return name;
    }

    public boolean hasRequiredLevel() {
        return Rs2Player.getSkillRequirement(Skill.FARMING, this.farmingLevel);
    }
}