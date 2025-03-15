package net.runelite.client.plugins.microbot.kaas.pyrefox;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.kaas.pyrefox.enums.MeatPouch;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup("kaas-pyrefox")
@ConfigInformation("<h3>[KaaS] Pyrefox</h3>\n" +
        "<p>1. <strong>Start the bot near the hunters guild or pyre foxes.</strong>.</p>\n" +
        "<p>2. <strong>Grab your large meat pouch, knife, axe and some logs.</strong>.</p>\n" +
        "<p>3. <strong>Lock all inventory slots that shouldn't be deposited (we will deposit all).</p>\n" +
        "<p>3. <strong>Will eat at the bank on low hp or banking (if enabled)</p>\n" +
        "<p>4. <strong>Large meat pouch is supported, make sure it's open!</p>\n")

public interface PyreFoxConfig extends Config
{
    /**
     * SECTION DEFINITIONS
     */
    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 1
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Food options",
            description = "Settings for managing food",
            position = 2
    )
    String foodSection = "food";

    @ConfigSection(
            name = "Dev tools",
            description = "Dev tools for debugging",
            position = 3,
            closedByDefault = true
    )
    String devSection = "testing";

    /**
     * GENERAL
     */
    @ConfigItem(
            keyName = "UseMeatPouch",
            name = "Use meat pouch?",
            description = "Do you have a meat pouch?",
            section = generalSection
    )
    default boolean UseMeatPouch()
    {
        return true;
    }

    @ConfigItem(
            keyName = "MeatPouch",
            name = "Meat pouch",
            description = "Which meat pouch should the script use?",
            section = generalSection
    )
    default MeatPouch MeatPouch()
    {
        return MeatPouch.LARGE_MEAT_POUCH;
    }

    /**
     * FOOD
     */
    @ConfigItem(
            keyName = "EatAtBank",
            name = "Eat at bank",
            description = "Auto eats food at the bank to fill up your hitpoints.",
            section = foodSection
    )
    default boolean AutoEat()
    {
        return true;
    }

    @ConfigItem(
            keyName = "FoodToEatAtBank",
            name = "Food to eat at bank",
            description = "What food should we eat at the bank?",
            section = foodSection
    )
    default Rs2Food FoodToEatAtBank()
    {
        return Rs2Food.LOBSTER;
    }

    @ConfigItem(
            keyName = "runToBankHP",
            name = "Run to bank at HP",
            description = "Run to the bank to eat <= hitpoints",
            section = foodSection
    )
    default int runToBankHP() {
        return 25;
    }

//    @ConfigItem(
//            keyName = "UsePrayPots",
//            name = "UsePrayPots",
//            description = "Uses prayer potions on low health, includes moth mixes!",
//            position = 6,
//            section = generalSection
//    )
//    default boolean UsePrayPots()
//    {
//        return true;
//    }


    /**
     * Development section
     */
    @ConfigItem(
            keyName = "ForceBank",
            name = "Force banking",
            description = "Forces the script to bank, used mainly for debugging.",
            position = 1,
            section = devSection
    )
    default boolean ForceBank()
    {
        return false;
    }

    @ConfigItem(
            keyName = "EnableVerboseLogging",
            name = "Enable verbose logging",
            description = "Log extended messages from the script - used for testing.",
            position = 2,
            section = devSection
    )
    default boolean EnableVerboseLogging() {
        return false;
    }
}
