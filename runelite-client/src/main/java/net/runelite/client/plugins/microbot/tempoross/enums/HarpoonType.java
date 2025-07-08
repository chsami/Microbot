package net.runelite.client.plugins.microbot.tempoross.enums;

import net.runelite.api.AnimationID;
import net.runelite.api.ItemID;
import net.runelite.client.game.ItemVariationMapping;

import java.util.List;

public enum HarpoonType {
    HARPOON(AnimationID.FISHING_HARPOON, "Harpoon or variant"),
    BAREHAND(AnimationID.FISHING_BAREHAND, "Bare-handed");

    private final int animationId;
    private final String name;

    HarpoonType(int animationId, String name) {
        this.animationId = animationId;
        this.name = name;
    }

    public int getAnimationId() {
        return animationId;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns all supported item IDs for this type.
     * For HARPOON, uses ItemVariationMapping to fetch all variations of HARPOON.
     * For BAREHAND, returns -1.
     */
    public int[] getSupportedItemIds() {
        if (this == HARPOON) {
            List<Integer> variations = ItemVariationMapping.getVariations(ItemID.HARPOON);
            return variations.stream().mapToInt(Integer::intValue).toArray();
        } else if (this == BAREHAND) {
            return new int[] { -1 };
        }
        return new int[0];
    }

    public boolean matchesItemId(int itemId) {
        for (int id : getSupportedItemIds()) {
            if (id == itemId) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}