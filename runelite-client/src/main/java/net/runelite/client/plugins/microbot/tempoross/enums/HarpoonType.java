package net.runelite.client.plugins.microbot.tempoross.enums;

import net.runelite.api.AnimationID;
import net.runelite.api.ItemID;
import net.runelite.client.game.ItemVariationMapping;

import java.util.List;

public enum HarpoonType
{
    HARPOON(AnimationID.FISHING_HARPOON, "Harpoon or variant") {
        @Override
        public int[] getSupportedItemIds() {
            List<Integer> variations = ItemVariationMapping.getVariations(ItemID.HARPOON);
            return variations.stream().mapToInt(Integer::intValue).toArray();
        }
    },
    INFERNAL_HARPOON(AnimationID.FISHING_HARPOON, "Infernal Harpoon") {
        @Override
        public int[] getSupportedItemIds() {
            return new int[] { ItemID.INFERNAL_HARPOON };
        }
    },
    BAREHAND(AnimationID.FISHING_BAREHAND, "Bare-handed") {
        @Override
        public int[] getSupportedItemIds() {
            return new int[] { -1 };
        }
    };

    private final int animationId;
    private final String name;

    HarpoonType(int animationId, String name)
    {
        this.animationId = animationId;
        this.name = name;
    }

    public int getAnimationId()
    {
        return animationId;
    }

    public String getName()
    {
        return name;
    }

    public int[] getSupportedItemIds()
    {
        return new int[0];
    }

    public boolean matchesItemId(int itemId)
    {
        for (int id : getSupportedItemIds())
        {
            if (id == itemId)
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
