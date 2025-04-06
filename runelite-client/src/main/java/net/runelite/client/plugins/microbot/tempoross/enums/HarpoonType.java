package net.runelite.client.plugins.microbot.tempoross.enums;

import net.runelite.api.AnimationID;
import net.runelite.api.ItemID;

public enum HarpoonType
{

// HARPOON, BARBTAIL_HARPOON, DRAGON_HARPOON, INFERNAL_HARPOON, CRYSTAL_HARPOON

    HARPOON(ItemID.HARPOON, AnimationID.FISHING_HARPOON, "Harpoon"),
    BAREHAND(-1, AnimationID.FISHING_BAREHAND, "Bare-handed"),
    BARBTAIL_HARPOON(ItemID.BARBTAIL_HARPOON, AnimationID.FISHING_BARBTAIL_HARPOON, "Barb-tail harpoon"),
    DRAGON_HARPOON(ItemID.DRAGON_HARPOON, AnimationID.FISHING_DRAGON_HARPOON,  "Dragon harpoon"),
    INFERNAL_HARPOON(ItemID.INFERNAL_HARPOON, AnimationID.FISHING_INFERNAL_HARPOON, "Infernal harpoon"),
    CRYSTAL_HARPOON(ItemID.CRYSTAL_HARPOON, AnimationID.FISHING_CRYSTAL_HARPOON, "Crystal harpoon");


    private final int id;
    private final int animationId;
    private final String name;

    HarpoonType(int id, int animationId, String name)
    {
        this.id = id;
        this.animationId = animationId;
        this.name = name;
    }

    public int getId()
    {
        return id;
    }

    public int getAnimationId()
    {
        return animationId;
    }

    public String getName()
    {
        return name;
    }


}