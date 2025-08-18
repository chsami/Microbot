package net.runelite.client.plugins.microbot.tithefarm.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

@Getter
@RequiredArgsConstructor
public enum TitheFarmMaterial {
    GOLOVANOVA_SEED("Golovanova seed", 34, '1', ItemID.HOSIDIUS_TITHE_FRUIT_A),
    BOLOGANO_SEED("Bologano seed", 54, '2', ItemID.HOSIDIUS_TITHE_FRUIT_B),
    LOGAVANO_SEED("Logavano seed", 74, '3', ItemID.HOSIDIUS_TITHE_FRUIT_C);

    final String name;
    final int levelRequired;
    final char option;
    final int fruitId;

    public static TitheFarmMaterial getSeedForLevel() {
        if (Microbot.getClient().getRealSkillLevel(Skill.FARMING) >= LOGAVANO_SEED.levelRequired)
            return LOGAVANO_SEED;
        if (Microbot.getClient().getRealSkillLevel(Skill.FARMING) >= BOLOGANO_SEED.levelRequired)
            return BOLOGANO_SEED;
        if (Microbot.getClient().getRealSkillLevel(Skill.FARMING) >= GOLOVANOVA_SEED.levelRequired)
            return GOLOVANOVA_SEED;

        return LOGAVANO_SEED;
    }

    public static boolean hasWateringCanToBeFilled() {
        return Rs2Inventory.hasItem(ItemID.WATERING_CAN_7) || Rs2Inventory.hasItem(ItemID.WATERING_CAN_6)
                || Rs2Inventory.hasItem(ItemID.WATERING_CAN_5) || Rs2Inventory.hasItem(ItemID.WATERING_CAN_4)
                || Rs2Inventory.hasItem(ItemID.WATERING_CAN_3) || Rs2Inventory.hasItem(ItemID.WATERING_CAN_2)
                || Rs2Inventory.hasItem(ItemID.WATERING_CAN_1) || Rs2Inventory.hasItem(ItemID.WATERING_CAN_0);
    }

    public static boolean hasGricollersCan() {
        return Rs2Inventory.hasItem(ItemID.ZEAH_WATERINGCAN);
    }

    public static int getWateringCanToBeFilled() {
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_7)) {
            return ItemID.WATERING_CAN_7;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_6)) {
            return ItemID.WATERING_CAN_6;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_5)) {
            return ItemID.WATERING_CAN_5;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_4)) {
            return ItemID.WATERING_CAN_4;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_3)) {
            return ItemID.WATERING_CAN_3;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_2)) {
            return ItemID.WATERING_CAN_2;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_1)) {
            return ItemID.WATERING_CAN_1;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_0)) {
            return ItemID.WATERING_CAN_0;
        }
        return -1;
    }

    public static int getWateringCan() {
        if (hasGricollersCan()) {
            return ItemID.ZEAH_WATERINGCAN;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_1)) {
            return ItemID.WATERING_CAN_1;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_2)) {
            return ItemID.WATERING_CAN_2;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_3)) {
            return ItemID.WATERING_CAN_3;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_4)) {
            return ItemID.WATERING_CAN_4;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_5)) {
            return ItemID.WATERING_CAN_5;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_6)) {
            return ItemID.WATERING_CAN_6;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_7)) {
            return ItemID.WATERING_CAN_7;
        }
        if (Rs2Inventory.hasItem(ItemID.WATERING_CAN_8)) {
            return ItemID.WATERING_CAN_8;
        }
        return -1;
    }

    @Override
    public String toString() {
        return name;
    }
}
