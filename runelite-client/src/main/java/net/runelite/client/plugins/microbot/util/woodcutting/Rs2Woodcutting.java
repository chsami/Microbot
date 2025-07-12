package net.runelite.client.plugins.microbot.util.woodcutting;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;

public class Rs2Woodcutting {

    public static boolean isWearingAxeWithSpecialAttack() {
        return Rs2Equipment.isWearing(ItemID.DRAGON_AXE) || Rs2Equipment.isWearing(ItemID.DRAGON_AXE_2H) || Rs2Equipment.isWearing(ItemID.CRYSTAL_AXE) ||
                Rs2Equipment.isWearing(ItemID.CRYSTAL_AXE_2H) || Rs2Equipment.isWearing(ItemID.INFERNAL_AXE) ||
                Rs2Equipment.isWearing(ItemID.TRAILBLAZER_AXE);
    }
}
