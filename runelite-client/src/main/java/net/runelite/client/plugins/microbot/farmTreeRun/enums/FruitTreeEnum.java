package net.runelite.client.plugins.microbot.farmTreeRun.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum FruitTreeEnum {
    APPLE("Apple sapling", ItemID.PLANTPOT_APPLE_SAPLING, "Sweetcorn", ItemID.SWEETCORN, 9, 27),
    BANANA("Banana sapling", ItemID.PLANTPOT_BANANA_SAPLING, "Apples(5)", ItemID.BASKET_APPLE_5, 4, 33),
    ORANGE("Orange sapling", ItemID.PLANTPOT_ORANGE_SAPLING, "Strawberries(5)", ItemID.BASKET_STRAWBERRY_5, 3, 39),
    CURRY("Curry sapling", ItemID.PLANTPOT_CURRY_SAPLING, "Bananas(5)", ItemID.BASKET_BANANA_5, 5, 42),
    PINEAPPLE("Pineapple sapling", ItemID.PLANTPOT_PINEAPPLE_SAPLING, "Watermelon", ItemID.WATERMELON, 10, 51),
    PAPAYA("Papaya sapling", ItemID.PLANTPOT_PAPAYA_SAPLING, "Pineapple", ItemID.PINEAPPLE, 10, 57),
    PALM("Palm sapling", ItemID.PLANTPOT_PALM_SAPLING, "Papaya fruit", ItemID.PAPAYA, 15, 68),
    DRAGONFRUIT("Dragonfruit sapling", ItemID.PLANTPOT_DRAGONFRUIT_SAPLING, "Coconut", ItemID.COCONUT, 15, 81);


    private final String name;
    private final int saplingId;
    private final String payment;
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
