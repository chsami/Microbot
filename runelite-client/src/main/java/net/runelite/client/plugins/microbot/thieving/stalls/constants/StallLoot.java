package net.runelite.client.plugins.microbot.thieving.stalls.constants;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

public enum StallLoot {
    TEA(new Integer[]{ItemID.CUP_OF_TEA, ItemID.CUP_EMPTY}),
    BAKER(new Integer[]{ItemID.CAKE, ItemID.BREAD, ItemID.CHOCOLATE_SLICE}),
    SILK(new Integer[]{ItemID.SILK}),
    FRUIT(new Integer[]{ItemID.COOKING_APPLE, ItemID.BANANA, ItemID.STRAWBERRY,
                        ItemID.JANGERBERRIES, ItemID.LEMON, ItemID.REDBERRIES,
                        ItemID.PINEAPPLE, ItemID.LIME, ItemID.MACRO_TRIFFIDFRUIT,
                        ItemID.GOLOVANOVA_TOP, ItemID.PAPAYA})
    ;

    @Getter
    private Integer[] itemIds;

    StallLoot(Integer[] itemIds)
    {
        this.itemIds = itemIds;
    }
}
