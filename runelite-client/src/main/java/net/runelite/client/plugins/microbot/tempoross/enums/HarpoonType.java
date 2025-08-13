package net.runelite.client.plugins.microbot.tempoross.enums;

import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;

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

    /**
     * Checks if a variant of this harpoon type is in the player's inventory or equipment.
     * This method encapsulates the logic for checking both inventory and equipment for any variant
     * of the selected harpoon type.
     *
     * @return true if a variant of this harpoon is found in inventory or equipment, false otherwise
     */
    public boolean hasVariantInInventoryOrEquipment() {
        // For barehand fishing, no item is needed
        if (this == BAREHAND) {
            return true;
        }
        
        // First check for the main harpoon ID
        if (Rs2Inventory.contains(this.id) || Rs2Equipment.isWearing(this.id)) {
            return true;
        }
        
        // Check for specific variants based on the harpoon type
        switch (this) {
            case DRAGON_HARPOON:
                return Rs2Inventory.contains(ItemID.TRAILBLAZER_HARPOON_NO_INFERNAL, ItemID.TRAILBLAZER_RELOADED_HARPOON_NO_INFERNAL) || 
                       Rs2Equipment.isWearing(ItemID.TRAILBLAZER_HARPOON_NO_INFERNAL) || 
                       Rs2Equipment.isWearing(ItemID.TRAILBLAZER_RELOADED_HARPOON_NO_INFERNAL);
                
            case INFERNAL_HARPOON:
                return Rs2Inventory.contains(ItemID.INFERNAL_HARPOON_EMPTY, 
                                           ItemID.TRAILBLAZER_HARPOON, 
                                           ItemID.TRAILBLAZER_HARPOON_EMPTY,
                                           ItemID.TRAILBLAZER_RELOADED_HARPOON,
                                           ItemID.TRAILBLAZER_RELOADED_HARPOON_EMPTY) || 
                       Rs2Equipment.isWearing(ItemID.INFERNAL_HARPOON_EMPTY) || 
                       Rs2Equipment.isWearing(ItemID.TRAILBLAZER_HARPOON) || 
                       Rs2Equipment.isWearing(ItemID.TRAILBLAZER_HARPOON_EMPTY) ||
                       Rs2Equipment.isWearing(ItemID.TRAILBLAZER_RELOADED_HARPOON) ||
                       Rs2Equipment.isWearing(ItemID.TRAILBLAZER_RELOADED_HARPOON_EMPTY);
                
            case CRYSTAL_HARPOON:
                return Rs2Inventory.contains(ItemID.CRYSTAL_HARPOON_INACTIVE) || 
                       Rs2Equipment.isWearing(ItemID.CRYSTAL_HARPOON_INACTIVE);
                
            default:
                return false;
        }
    }
}