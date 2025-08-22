package net.runelite.client.plugins.microbot.GlassBlowing.tunaCommon;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class Helpers {

    public static void leftClickInventoryItem(String item) {
        int count = Rs2Inventory.count(item);
        Microbot.click(Rs2Inventory.itemBounds(Rs2Inventory.get(item)));
        sleepUntil(()-> Rs2Inventory.count(item) < count || count  == 0, 10000);
    }

    public void spamLeftClickInventoryItem(String item) {
        Microbot.click(Rs2Inventory.itemBounds(Rs2Inventory.get(item)));
        sleep(75,125);
        Microbot.click(Rs2Inventory.itemBounds(Rs2Inventory.get(item)));
        sleep(75,125);
        Microbot.click(Rs2Inventory.itemBounds(Rs2Inventory.get(item)));
        sleep(75,125);
        Microbot.click(Rs2Inventory.itemBounds(Rs2Inventory.get(item)));
        sleep(75,125);
    }

    public static void leftClickBankItem(String item) {
        int count = Rs2Inventory.count(item);
        Microbot.click(Rs2Bank.itemBounds(Rs2Bank.getBankItem(item)));
        sleepUntil(()-> Rs2Inventory.count(item) > count || count  == 0, 10000);
    }

    public void drinkPotion(String potionName) {
        if (Rs2Inventory.contains(potionName + "(1)")) {
            Rs2Inventory.interact(potionName + "(1)", "Drink");
        } else if (Rs2Inventory.contains(potionName + "(2)")) {
            Rs2Inventory.interact(potionName + "(2)", "Drink");
        } else if (Rs2Inventory.contains(potionName + "(3)")) {
            Rs2Inventory.interact(potionName + "(3)", "Drink");
        } else if (Rs2Inventory.contains(potionName + "(4)")) {
            Rs2Inventory.interact(potionName + "(4)", "Drink");
        }
    }

    public void checkPrayer(int minPrayer) {
        if (Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) < minPrayer) {
            drinkPotion("Prayer potion");
            sleep(200, 400);
        }
    }

    public void checkHealth(int minHP, String food) {
        if (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) < minHP) {
            Rs2Inventory.interact(food, "eat");
            sleep(100, 200);
        }
    }

    public static List<String> parseGear(String gearString) {
        return Arrays.asList(gearString.split(","));
    }

    public static boolean isGearEquipped(List<String> gear) {
        return gear.stream().allMatch(Rs2Equipment::isWearing);
    }

    public static void equipGear(List<String> gear) {
        for (String item : gear) {
            Rs2Inventory.wield(item);
            sleep(140);
        }
    }

    public void equip(String equipment) {
        Rs2Inventory.wield(equipment);
        if (Rs2Inventory.contains(equipment + " 100")) {
            Rs2Inventory.wield(equipment + " 100");
        } else if (Rs2Inventory.contains(equipment + " 75")) {
            Rs2Inventory.wield(equipment + " 75");
        } else if (Rs2Inventory.contains(equipment + " 50")) {
            Rs2Inventory.wield(equipment + " 50");
        } else if (Rs2Inventory.contains(equipment + " 25")) {
            Rs2Inventory.wield(equipment + " 25");
        }
        sleep(140, 250);
    }

    public static List<String> parseLootItems(String lootFilter) {
        return Arrays.asList(lootFilter.toLowerCase().split(","));
    }

    public static Rs2PrayerEnum switchDefensivePrayer(Rs2PrayerEnum newDefensivePrayer, Rs2PrayerEnum currentDefensivePrayer) {
        if (currentDefensivePrayer != null) {
            Rs2Prayer.toggle(currentDefensivePrayer, false);
        }
        Rs2Prayer.toggle(newDefensivePrayer, true);
        return newDefensivePrayer;
    }


    public static Rs2PrayerEnum switchOffensivePrayer(Rs2PrayerEnum newOffensivePrayer, Rs2PrayerEnum currentOffensivePrayer) {
        if (currentOffensivePrayer != null) {
            Rs2Prayer.toggle(currentOffensivePrayer, false);
        }
        Rs2Prayer.toggle(newOffensivePrayer, true);
        return newOffensivePrayer;
    }

    public static void lootAndScatterInfernalAshes() {
        String ashesName = "Infernal ashes";

        if (!Rs2Inventory.isFull() && Rs2GroundItem.lootItemsBasedOnNames(new LootingParameters(10, 1, 1, 0, false, true, ashesName))) {
            sleepUntil(() -> Rs2Inventory.contains(ashesName), 2000);

            if (Rs2Inventory.contains(ashesName)) {
                Rs2Inventory.interact(ashesName, "Scatter");
                sleep(600); // Wait briefly for scattering action
            }
        }
    }

    public static int normalRandom(int mean, double deviationPercent) {
        double stdDev = mean * deviationPercent;
        double standardGaussian = ThreadLocalRandom.current().nextGaussian();

        double value = mean + stdDev * standardGaussian;

        return (int) value;
    }

}
