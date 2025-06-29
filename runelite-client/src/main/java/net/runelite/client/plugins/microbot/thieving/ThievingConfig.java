package net.runelite.client.plugins.microbot.thieving;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.thieving.enums.ThievingNpc;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup("Thieving")
public interface ThievingConfig extends Config {

    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 0,
            section = generalSection
    )
    default String GUIDE() {
        return "Start near any of the npc";
    }
    @ConfigSection(
            name = "general",
            description = "general",
            position = 0
    )
    String generalSection = "General";

    @ConfigItem(
            keyName = "Npc",
            name = "Npc",
            description = "Choose the npc to start thieving from",
            position = 0,
            section = generalSection
    )
    default ThievingNpc THIEVING_NPC()
    {
        return ThievingNpc.NONE;
    }

    @ConfigItem(
            keyName = "ardougneAreaCheck",
            name = "Ardy Knights Bank Area Check?",
            description = "Enforce Ardougne Knight to be in Ardougne Bank area",
            position = 1,
            section = generalSection
    )
    default boolean ardougneAreaCheck()
    {
        return false;
    }

    @ConfigSection(
            name = "buffs",
            description = "general",
            position = 0
    )
    String buffsSection = "Buffs";

    @ConfigItem(
            keyName = "shadowVeil",
            name = "Shadow veil",
            description = "Choose whether to shadow veil",
            position = 0,
            section = buffsSection
    )
    default boolean shadowVeil() {
        return false;
    }


    @ConfigSection(
            name = "Food",
            description = "Food",
            position = 1
    )
    String food = "Food";

    @ConfigItem(
            keyName = "UseFood",
            name = "Auto eat food",
            description = "Automatically eats food",
            position = 1,
            section = food
    )
    default boolean useFood() { return true; }

    @ConfigItem(
            keyName = "Hitpoints",
            name = "Eat at %",
            description = "Use food below certain hitpoint percent",
            position = 2,
            section = food
    )
    default int hitpoints()
    {
        return 20;
    }

    @ConfigItem(
            keyName = "Food",
            name = "Food",
            description = "type of food",
            position = 3,
            section = food
    )
    default Rs2Food food()
    {
        return Rs2Food.MONKFISH;
    }

    @ConfigItem(
            keyName = "FoodAmount",
            name = "Food Amount",
            description = "Amount of food to withdraw from bank",
            position = 4,
            section = food
    )
    default int foodAmount()
    {
        return 5;
    }

    @ConfigSection(
            name = "Coin pouch & Items",
            description = "Coin pouch & Items",
            position = 2
    )
    String coinPouchSection = "Coin pouch & Items";

    @ConfigItem(
            keyName = "Coin Pouch TreshHold",
            name = "How many coinpouches in your inventory before opening?",
            description = "How many coinpouches do you need in your inventory before opening them?",
            position = 1,
            section = coinPouchSection
    )
    @Range(min = 1, max = 28)
    default int coinPouchTreshHold()
    {
        return 28;
    }

    @ConfigItem(
            keyName = "KeepItem",
            name = "Keep items above value",
            description = "Keep items above the gp value",
            position = 1,
            section = coinPouchSection
    )
    default int keepItemsAboveValue()
    {
        return 10000;
    }

    @ConfigItem(
            keyName = "DodgyNecklaceAmount",
            name = "Dodgy necklace Amount",
            description = "Amount of dodgy necklace to withdraw from bank",
            position = 1,
            section = coinPouchSection
    )
    default int dodgyNecklaceAmount()
    {
        return 5;
    }

    @ConfigItem(
            keyName = "DoNotDropitemList",
            name = "Do not drop item list",
            description = "Do not drop item list comma seperated",
            position = 1,
            section = coinPouchSection
    )
    default String DoNotDropItemList()
    {
        return "";
    }

}
