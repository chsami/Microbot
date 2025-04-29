package net.runelite.client.plugins.microbot.farmTreeRun.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum FruitTreeEnum {
    APPLE("Apple sapling", ItemID.APPLE_SAPLING, "Sweetcorn", ItemID.SWEETCORN, 9, 27),
    BANANA("Banana sapling", ItemID.BANANA_SAPLING, "Apples(5)", ItemID.APPLES5, 4, 33),
    ORANGE("Orange sapling", ItemID.ORANGE_SAPLING, "Strawberries(5)", ItemID.STRAWBERRIES5, 3, 39),
    CURRY("Curry sapling", ItemID.CURRY_SAPLING, "Bananas(5)", ItemID.BANANAS5, 5, 42),
    PINEAPPLE("Pineapple sapling", ItemID.PINEAPPLE_SAPLING, "Watermelon", ItemID.WATERMELON, 10, 51),
    PAPAYA("Papaya sapling", ItemID.PAPAYA_SAPLING, "Pineapple", ItemID.PINEAPPLE, 10, 57),
    PALM("Palm sapling", ItemID.PALM_SAPLING, "Papaya fruit", ItemID.PAPAYA_FRUIT, 15, 68),
    DRAGONFRUIT("Dragonfruit sapling", ItemID.DRAGONFRUIT_SAPLING, "Coconut", ItemID.COCONUT, 15, 81);


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
