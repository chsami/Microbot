package net.runelite.client.plugins.microbot.sticktothescript.varrockanvil;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.smelting.enums.AnvilItem;
import net.runelite.client.plugins.microbot.smelting.enums.Bars;


@ConfigGroup("VarrockAnvil")
@ConfigInformation("This plugin smiths bars at the Varrock anvil.<br /><br />For bugs or feature requests, contact me through Discord (@StickToTheScript).")
public interface VarrockAnvilConfig extends Config {

    @ConfigSection(
            name = "Smithing",
            description = "Smithing Settings",
            position = 0
    )
    String smithingSection = "Smithing";

    @ConfigItem(
            keyName = "barType",
            name = "Bar Type",
            description = "The type of bar to use on the anvil",
            position = 0,
            section = smithingSection
    )
    default Bars sBarType()
    {
        return Bars.BRONZE;
    }

    @ConfigItem(
            keyName = "smithObject",
            name = "Smith Object",
            description = "The desired object to smith at the anvil",
            position = 1,
            section = smithingSection
    )
    default AnvilItem sAnvilItem()
    {
        return AnvilItem.SCIMITAR;
    }

    @ConfigItem(
            keyName = "logout",
            name = "Logout On Complete",
            description = "Log out when completed all bars.",
            position = 2,
            section = smithingSection
    )
    default boolean sLogout()
    {
        return true;
    }

    @ConfigItem(
            keyName = "debug",
            name = "Debug",
            description = "Enable debug information",
            position = 3,
            section = smithingSection
    )
    default boolean sDebug()
    {
        return false;
    }
}
