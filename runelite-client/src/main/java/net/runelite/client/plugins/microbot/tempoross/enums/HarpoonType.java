package net.runelite.client.plugins.microbot.tempoross.enums;

import net.runelite.api.AnimationID;
import net.runelite.api.ItemID;

public enum HarpoonType
{

// HARPOON, BARBTAIL_HARPOON, DRAGON_HARPOON, INFERNAL_HARPOON, CRYSTAL_HARPOON

    HARPOON(ItemID.HARPOON, AnimationID.FISHING_HARPOON, "Harpoon"),
    BAREHAND(-1, AnimationID.FISHING_BAREHAND, "Bare-handed"),
    BARBTAIL_HARPOON(ItemID.BARBTAIL_HARPOON, AnimationID.FISHING_BARBTAIL_HARPOON, "Barb-tail harpoon"),
    DRAGON_HARPOON(ItemID.DRAGON_HARPOON, AnimationID.FISHING_DRAGON_HARPOON, "Dragon harpoon"),
    DRAGON_HARPOON_OR(ItemID.DRAGON_HARPOON_OR, AnimationID.FISHING_TRAILBLAZER_HARPOON, "Dragon harpoon (or)"),
    DRAGON_HARPOON_OR_30349(ItemID.DRAGON_HARPOON_OR_30349, AnimationID.FISHING_TRAILBLAZER_HARPOON, "Dragon harpoon reloaded (or)"),                
    INFERNAL_HARPOON(ItemID.INFERNAL_HARPOON, AnimationID.FISHING_INFERNAL_HARPOON, "Infernal harpoon"),
    INFERNAL_HARPOON_UNCHARGED(ItemID.INFERNAL_HARPOON_UNCHARGED, AnimationID.FISHING_INFERNAL_HARPOON, "Infernal harpoon (uncharged)"),
    INFERNAL_HARPOON_OR_30342(ItemID.INFERNAL_HARPOON_OR_30342, AnimationID.FISHING_TRAILBLAZER_HARPOON, "Infernal harpoon (or)"),
    INFERNAL_HARPOON_UNCHARGED_30343(ItemID.INFERNAL_HARPOON_UNCHARGED_30343, AnimationID.FISHING_TRAILBLAZER_HARPOON, "Infernal harpoon uncharged (or)"),
    CRYSTAL_HARPOON(ItemID.CRYSTAL_HARPOON, AnimationID.FISHING_CRYSTAL_HARPOON, "Crystal harpoon"),
    CRYSTAL_HARPOON_INACTIVE(ItemID.CRYSTAL_HARPOON_INACTIVE, AnimationID.FISHING_CRYSTAL_HARPOON, "Crystal harpoon (inactive)");


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
