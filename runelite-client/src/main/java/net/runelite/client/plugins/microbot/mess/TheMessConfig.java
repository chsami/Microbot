package net.runelite.client.plugins.microbot.mess;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.mess.TheMessScript.Dish;

@ConfigGroup("the_mess")
@ConfigInformation("<b>-- ABOUT THE MESS PLUGIN --</b><br /><br />" +
        "<b>Wiki:</b> https://oldschool.runescape.wiki/w/Mess<br /><br />" +
        "• It's basically Overcooked on OSRS<br />" +
        "• Perfect for ironman to bump cook level as there are no items needed to get good cooking xp/h.<br />" +
        "• There are no rewards other than XP in this.<br /><br />" +
        "<b>-- REQUIREMENTS --</b><br />" +
        "• Servery Meat Pie (20 Cooking)<br />" +
        "• Servery Stew (25 Cooking)<br />" +
        "• Servery Pineapple Pizza (65 Cooking)<br /><br />" +
        "<b>FLOW OF THE PLUGIN:</b><br /><br />" +
        "• If you are not in the zone, it will travel to the Mess Hall.<br />" +
        "• When reaching there, if inventory is not empty, it will bank it nearby.<br />" +
        "• If everything is in order it will loop into making dishes and turning them in.<br />" +
        "• Whenever it sees the appreciation for the selected dish below the set threshold, it will hop worlds till it's over.<br />" +
        "(beware that threshold over 33 may cause hop loop.)")
public interface TheMessConfig extends Config {
    @ConfigSection(
            name = "Settings",
            description = "Settings for the script itself.",
            position = 0
    )
    String settingsSection = "settings";

    @ConfigItem(
            keyName = "dish",
            name = "Dish",
            description = "Select the dish to cook, make sure to\n" +
                    "to have the required cooking level shown above.",
            position = 0,
            section = settingsSection
    )
    default Dish dish() {
        return Dish.STEW;
    }

    @ConfigItem(
            keyName = "appreciation_threshold",
            name = "Minimum Appreciation",
            description = "Percentage of appreciation required to continue serving.\n" +
                    "If appreciation is below this value, the bot will hop worlds.",
            position = 1,
            section = settingsSection
    )
    default int appreciation_threshold() {
        return 29;
    }

    @ConfigItem(
            keyName = "debug_mode",
            name = "Debug Mode",
            description = "Enables debug mode for the script.\n" +
                    "Useful for troubleshooting and development.",
            position = 2,
            section = settingsSection
    )
    default boolean debug_mode() {
        return false;
    }
}
