package net.runelite.client.plugins.microbot.moonsOfPeril;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup("perilousMoons")
public interface moonsOfPerilConfig extends Config {

    @ConfigSection(
            name     = "General",
            description = "Global settings for the plugin",
            position = 0
    )
    String generalSection = "General";

    @ConfigSection(
            name = "Resupply",
            description = "Configure your resupply settings at the end of each run",
            position = 1
    )
    String resupplySection = "Resupply Settings";

    @ConfigSection(
            name = "Eclipse Moon",
            description = "Eclipse Moon",
            position = 2
    )
    String eclipseMoonSection = "Eclipse Moon";

    @ConfigSection(
            name = "Blue Moon",
            description = "Blue Moon",
            position = 3
    )
    String blueMoonSection = "Blue Moon";

    @ConfigSection(
            name = "Blood Moon",
            description = "Blood Moon",
            position = 4
    )
    String bloodMoonSection = "Blood Moon";

    @ConfigItem(
            keyName  = "debugLogging",
            name     = "Debug logging",
            description = "Logs to the Microbot console",
            position = 0,
            section  = generalSection
    )
    default boolean debugLogging() { return false; }

    @ConfigItem(
            keyName  = "shutdownOnDeath",
            name     = "Shutdown on death",
            description = "Automatically stop the script after a death event",
            position = 1,
            section  = generalSection
    )
    default boolean shutdownOnDeath() { return false; }

    @ConfigItem(
            keyName  = "healthPercentage",
            name     = "Health % topup",
            description = "Strategically eat during boss sequences below this health %",
            position = 2,
            section  = generalSection
    )
    default int healthPercentage() { return 70; }

    @ConfigItem(
            keyName  = "prayerPercentage",
            name     = "Prayer % topup",
            description = "Strategically drink during boss sequences below this prayer %",
            position = 2,
            section  = generalSection
    )
    default int prayerPercentage() { return 70; }

    @ConfigItem(
            keyName = "moonlightPotionsQuantum",
            name = "Moonlight Potions Resupply",
            description = "Choose how many moonlight potions to have in inventory post resupply",
            position = 0,
            section = resupplySection
    )
    default int moonlightPotionsQuantum()
    {
        return 4;
    }

    @ConfigItem(
            keyName = "moonlightPotionsMinimum",
            name = "Moonlight Potions Minimum",
            description = "The minimum moonlight potions to hold in inventory before bossing",
            position = 1,
            section = resupplySection
    )
    default int moonlightPotionsMinimum()
    {
        return 2;
    }

    @ConfigItem(
            keyName = "cookedBreamMinimum",
            name = "Cooked Bream Minimum",
            description = "The minimum cooked bream to hold in inventory before bossing",
            position = 2,
            section = resupplySection
    )
    default int cookedBreamMinimum()
    {
        return 10;
    }

    @ConfigItem(
            keyName  = "enableEclipse",
            name     = "Fight Eclipse Moon",
            description = "Untick to skip Eclipse Moon runs",
            position = 0,
            section  = eclipseMoonSection
    )
    default boolean enableEclipse() { return true; }

    @ConfigItem(
            keyName = "eclipseEquipmentNormal",
            name = "Eclipse - Normal Setup",
            description = "The InventorySetup to use during the Eclipse Moon normal attack sequence",
            position = 1,
            section = eclipseMoonSection
    )
    default InventorySetup eclipseEquipmentNormal()
    {
        return null;
    }

    @ConfigItem(
            keyName = "eclipseEquipmentClones",
            name = "Eclipse - Clones Setup",
            description = "The InventorySetup to use during the Eclipse Moon Clones attack sequence",
            position = 2,
            section = eclipseMoonSection
    )

    default InventorySetup eclipseEquipmentClones()
    {
        return null;
    }

    @ConfigItem(
            keyName  = "enableBlue",
            name     = "Fight Blue Moon",
            description = "Untick to skip Blue Moon runs",
            position = 0,
            section  = blueMoonSection
    )
    default boolean enableBlue() { return true; }

    @ConfigItem(
            keyName = "blueEquipmentNormal",
            name = "Blue - Normal Setup",
            description = "The InventorySetup to use during the Blue Moon normal attack sequence",
            position = 1,
            section = blueMoonSection
    )
    default InventorySetup blueEquipmentNormal()
    {
        return null;
    }

    @ConfigItem(
            keyName  = "enableBlood",
            name     = "Fight Blood Moon",
            description = "Untick to skip Blood Moon runs",
            position = 0,
            section  = bloodMoonSection
    )
    default boolean enableBlood() { return true; }

    @ConfigItem(
            keyName = "bloodEquipmentNormal",
            name = "Blood - Normal Setup",
            description = "The InventorySetup to use during the Blood Moon normal attack sequence",
            position = 1,
            section = bloodMoonSection
    )
    default InventorySetup bloodEquipmentNormal()
    {
        return null;
    }
}
