package net.runelite.client.plugins.microbot.wintertodt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.wintertodt.enums.Brazier;

@ConfigGroup("wintertodt")
public interface MWintertodtConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General",
            position = 0
    )
    String generalSection = "general";
    @ConfigSection(
            name = "Food",
            description = "Food",
            position = 1
    )
    String foodSection = "Food";
    @ConfigSection(
            name = "Brazier",
            description = "Braziers are found in the four corners of the Wintertodt's prison north of the Wintertodt Camp. They can be fueled using bruma roots or bruma kindling in order to drain the Wintertodt's energy.",
            position = 2
    )
    String brazierSection = "brazier";


    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 0,
            section = generalSection
    )
    default String GUIDE() {
        return "Start the script at the wintertodt bank with all your equipment and inventory setup";
    }

    @ConfigItem(
            keyName = "RelightBrazier",
            name = "Relight Brazier",
            description = "If the braziers go out, relighting the brazier will reward 6x the player's Firemaking level in experience.",
            position = 1,
            section = generalSection
    )
    default boolean relightBrazier() {
        return true;
    }

    @ConfigItem(
            keyName = "FletchRoots",
            name = "Fletch roots into kindlings",
            description = "Bruma kindling is obtained by using a knife on a bruma root, granting Fletching experience appropriate to the player's level. The Fletching experience given is equal to 0.6 times the player's Fletching level.",
            position = 2,
            section = generalSection
    )
    default boolean fletchRoots() {
        return true;
    }

    @ConfigItem(
            keyName = "FixBrazier",
            name = "Fix Brazier",
            description = "The Wintertodt will occasionally break the braziers; they must be repaired again before use. This rewards 4x the player's Construction level in experience, provided they own a player-owned house.",
            position = 3,
            section = generalSection
    )
    default boolean fixBrazier() {
        return true;
    }

    @ConfigItem(
            keyName = "OpenCrates",
            name = "Open Supply Crates",
            description = "Open supply crates",
            position = 4,
            section = generalSection
    )
    default boolean openCrates() {
        return true;
    }

    @ConfigItem(
            keyName = "AxeInventory",
            name = "Axe In Inventory?",
            description = "Axe in inventory?",
            position = 5,
            section = generalSection
    )
    default boolean axeInInventory() {
        return false;
    }

    @ConfigItem(
            keyName = "Food",
            name = "Food",
            description = "Select the type of food to use",
            position = 1,
            section = foodSection
    )
    default Rs2Food food() {
        return Rs2Food.MONKFISH;
    }

    @ConfigItem(
            keyName = "Amount",
            name = "Amount",
            description = "Alount of food to use",
            position = 2,
            section = foodSection
    )
    default int foodAmount() {
        return 6;
    }

    @ConfigItem(
            keyName = "MinFood",
            name = "Min Food",
            description = "Minimum food to start a new game",
            position = 3,
            section = foodSection
    )
    default int minFood() {
        return 2;
    }

    @ConfigItem(
            keyName = "Eat at %",
            name = "Eat at %",
            description = "Eat at specific percentage health.",
            position = 4,
            section = foodSection
    )
    default int eatAt() {
        return 70;
    }

    @ConfigItem(
            keyName = "Hitpoints Tresshold",
            name = "HP % to run away",
            description = "Runs to the bank if a specific health treshhold is reached and the player does not have any food in their inventory.",
            position = 5,
            section = foodSection
    )
    default int hpTreshhold() {
        return 30;
    }

    @ConfigItem(
            keyName = "Brazier",
            name = "Brazier",
            description = "The brazier to feed",
            position = 1,
            section = brazierSection
    )
    default Brazier brazierLocation() {
        return Brazier.SOUTH_EAST;
    }
}
