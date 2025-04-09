package net.runelite.client.plugins.microbot.nmz;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;

@ConfigGroup("nmz")
@ConfigInformation(
        "Before starting: <br>" +
                "<ul>" +
                "<li>Set up your inventory setup with required items</li>" +
                "<li>Ensure you have GP in the coffer</li>" +
                "<li>Have a previous dream setup already</li>" +
                "<li>Turn ON Auto Retaliate in combat settings</li>" +
                "</ul>" +
                "<p>These steps are essential for the plugin to function correctly.</p>"
)
public interface NmzConfig extends Config {
    String GROUP = "Nmz";

    @ConfigSection(
            name = "General",
            description = "General",
            position = 0,
            closedByDefault = false
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory Setup",
            description = "Inventory Setup to use for NMZ",
            position = 1,
            section = generalSection
    )
    default InventorySetup inventorySetup() { return null; }

    @ConfigItem(
            keyName = "How many overload potions to use",
            name = "How many overload potions to use",
            description = "How many overload potions to use",
            position = 3,
            section = generalSection
    )
    default int overloadPotionAmount()
    {
        return 8;
    }

    @ConfigItem(
            keyName = "How many absorption potions to use",
            name = "How many absorption potions to use",
            description = "How many absorption potions to use",
            position = 4,
            section = generalSection
    )
    default int absorptionPotionAmount()
    {
        return 19;
    }

    @ConfigItem(
            keyName = "Stop after death",
            name = "Stop after death",
            description = "Stop after death",
            position = 4,
            section = generalSection
    )
    default boolean stopAfterDeath()
    {
        return true;
    }

    @ConfigItem(
            keyName = "Use Zapper",
            name = "Use Zapper",
            description = "Use Zapper to increase nightmare zone points",
            position = 4,
            section = generalSection
    )
    default boolean useZapper()
    {
        return false;
    }

    @ConfigItem(
            keyName = "Use Reccurent damage",
            name = "Use Reccurent damage",
            description = "Use reccurent damage to increase nightmare zone points",
            position = 4,
            section = generalSection
    )
    default boolean useReccurentDamage()
    {
        return false;
    }

    @ConfigItem(
            keyName = "Use Power Surge",
            name = "Use Power Surge",
            description = "Use power surge for infinite special attack",
            position = 4,
            section = generalSection
    )
    default boolean usePowerSurge()
    {
        return false;
    }
    @ConfigItem(
            keyName = "Auto Prayer Potion",
            name = "Auto drink prayer potion",
            description = "Automatically drinks prayer potions",
            position = 5,
            section = generalSection
    )
    default boolean togglePrayerPotions()
    {
        return false;
    }
    @ConfigItem(
            keyName = "Random Mouse Movements",
            name = "Random Mouse Movements",
            description = "Random Mouse Movements",
            position = 6,
            section = generalSection
    )
    default boolean randomMouseMovements()
    {
        return true;
    }
    @ConfigItem(
            keyName = "Walk to center",
            name = "Walk to center",
            description = "Walk to center of nmz",
            position = 7,
            section = generalSection
    )
    default boolean walkToCenter()
    {
        return true;
    }
    @ConfigItem(
            keyName = "Randomly trigger rapid heal",
            name = "Randomly trigger rapid heal",
            description = "Will randomly trigger rapid heal",
            position = 8,
            section = generalSection
    )
    default boolean randomlyTriggerRapidHeal()
    {
        return true;
    }
}
