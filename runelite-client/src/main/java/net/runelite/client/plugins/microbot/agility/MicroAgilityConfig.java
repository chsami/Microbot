package net.runelite.client.plugins.microbot.agility;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.agility.enums.AgilityCourseName;

@ConfigGroup("MicroAgility")
@ConfigInformation("Enable the plugin near the start of your selected agility course. <br />" +
        "<b>Course requirements:</b>" +
        "<ul>" +
        "<li> Ape Atoll - Kruk or Ninja greegree equipped. Stamina pots recommended. </li>" +
        "<li>Shayzien Advanced - Crossbow and Mith Grapple equipped.</li>" +
        "</ul>")
public interface MicroAgilityConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General",
            position = 0,
            closedByDefault = false
    )
    String generalSection = "general";
    @ConfigItem(
            keyName = "Course",
            name = "Course",
            description = "Choose your agility course",
            position = 1,
            section = generalSection
    )
    default AgilityCourseName agilityCourse()
    {
        return AgilityCourseName.CANIFIS_ROOFTOP_COURSE;
    }

    @ConfigItem(
            keyName = "Hitpoints",
            name = "Eat at %)",
            description = "Use food below certain hitpoint percent. If there's no food in the inventory, the script stops. Set to 0 in order to disable.",
            position = 2,
            section = generalSection
    )
    default int hitpoints()
    {
        return 20;
    }


    @ConfigItem(
            keyName = "Alchemy",
            name = "Alch",
            description = "Use Low/High Alchemy while doing agility",
            position = 3,
            section = generalSection
    )
    default boolean alchemy()
    {
        return false;
    }

    @ConfigItem(
            keyName = "itemsToAlch",
            name = "Items to Alch",
            description = "Enter items to alch, separated by commas (e.g., Rune sword, Dragon dagger, Mithril platebody)",
            position = 3,
            section = generalSection
    )
    default String itemsToAlch() {
        return "";
    }
}