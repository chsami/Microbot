package net.runelite.client.plugins.microbot.tempoross.enums;

import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;

public enum HarpoonType
{

// HARPOON, BARBTAIL_HARPOON, DRAGON_HARPOON, INFERNAL_HARPOON, CRYSTAL_HARPOON

    HARPOON(ItemID.HARPOON, AnimationID.HUMAN_HARPOON, "Harpoon"),
    BAREHAND(-1, AnimationID.BRUT_PLAYER_HAND_FISHING_END_BLANK, "Bare-handed"),
    BARBTAIL_HARPOON(ItemID.HUNTING_BARBED_HARPOON, AnimationID.HUMAN_HARPOON_BARBED, "Barb-tail harpoon"),
    DRAGON_HARPOON(ItemID.DRAGON_HARPOON, AnimationID.HUMAN_HARPOON_DRAGON,  "Dragon harpoon"),
    INFERNAL_HARPOON(ItemID.INFERNAL_HARPOON, AnimationID.HUMAN_HARPOON_INFERNAL, "Infernal harpoon"),
    CRYSTAL_HARPOON(ItemID.CRYSTAL_HARPOON, AnimationID.HUMAN_HARPOON_CRYSTAL, "Crystal harpoon");


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