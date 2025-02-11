



package net.runelite.client.plugins.microbot.blastoisefurnace;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.blastoisefurnace.enums.Bars;

@ConfigGroup("blastoisefurnace")
@ConfigInformation("must have Ice Gloves or smiths gloves (i) equiped<br />+<br />coalbag, stamina potions and energy potions in bank<br /><br />| if doing gold bars; must have Goldsmiths Gauntlet and bank your coalbag |<br /><br />(makes 1.5m an hour minimum with steel and should cover expenses)<br /><br />current version does not support foremen or coffer refill<br /><br />im working on that aswell as full native use of antiban<br />Enjoy! :)")
public interface BlastoiseFurnaceConfig extends Config {
    @ConfigSection(
            name = "Blast Furnace Settings",
            description = "Blast Furnace Settings",
            position = 0,
            closedByDefault = false
    )
    String bFSettingsSection = "bFSettings";

    @ConfigItem(
            keyName = "stamina",
            name = "Energy/Stams",
            description = "Disable this if you're an ironman without staminas or energy potions!",
            position = 0,
            section = "general"
    )

    default boolean useStamina() {
        return true;
    }



    @ConfigItem(
            keyName = "Bars",
            name = "Bars",
            description = "Bars",
            position = 1,
            section = "bFSettings"
    )
    default Bars getBars() {
        return Bars.STEEL_BAR;
    }
    @ConfigSection(
            name = "Credits",
            description = "Credits",
            position = 2,
            closedByDefault = false
    )
    String Credits = "Credits";
    @ConfigItem(
            keyName = "Credits",
            name = "Credits",
            description = "Credits",
            position = 3,
            section = "Credits"
    )
    default String Credits() {
        return "Special thanks to:\n\nExioStorm, MrPecan, george\n\nfor building the backbone of this plugin!\nand teaching me how to java while doing so\ni will forever be gratefull.\nFishy ^_^";
    }
}