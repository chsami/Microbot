package net.runelite.client.plugins.microbot.tempoross.enums;

import net.runelite.api.AnimationID;
import net.runelite.api.ItemID;
import net.runelite.client.game.ItemVariationMapping;

public enum HarpoonType
{
    HARPOON(ItemID.HARPOON, AnimationID.FISHING_HARPOON, "Harpoon"),
    BAREHAND(-1, AnimationID.FISHING_BAREHAND, "Bare-handed"),
    BARBTAIL_HARPOON(ItemID.BARBTAIL_HARPOON, AnimationID.FISHING_BARBTAIL_HARPOON, "Barb-tail harpoon"),
    DRAGON_HARPOON(ItemID.DRAGON_HARPOON, AnimationID.FISHING_DRAGON_HARPOON, "Dragon harpoon"),
    INFERNAL_HARPOON(ItemID.INFERNAL_HARPOON, AnimationID.FISHING_INFERNAL_HARPOON, "Infernal harpoon"),
    CRYSTAL_HARPOON(ItemID.CRYSTAL_HARPOON, AnimationID.FISHING_CRYSTAL_HARPOON, "Crystal harpoon");

    private final int canonicalItemId;
    private final int animationId;
    private final String name;

    HarpoonType(int itemId, int animationId, String name)
    {
        this.canonicalItemId = ItemVariationMapping.map(itemId); // canonicalize here
        this.animationId = animationId;
        this.name = name;
    }

    public int getCanonicalItemId()
    {
        return canonicalItemId;
    }

    public int getAnimationId()
    {
        return animationId;
    }

    public String getName()
    {
        return name;
    }

    public static HarpoonType fromItemId(int itemId)
    {
        int canonical = ItemVariationMapping.map(itemId);

        for (HarpoonType type : values())
        {
            if (type.canonicalItemId == canonical)
            {
                return type;
            }
        }

        return null;
    }
}
