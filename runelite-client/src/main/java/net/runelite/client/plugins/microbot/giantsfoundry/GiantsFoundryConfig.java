package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.SmithableBars;

@ConfigGroup(GiantsFoundryConfig.GROUP)
@ConfigInformation(
        "• Start at the giants foundry minigame. <br />" +
        "• Please select the bars in your UI <br />" +
        "• Make sure you are wearing ice gloves & no weapon/shield equipped <br />"
)
public interface GiantsFoundryConfig extends Config {

    String GROUP = "GiantsFoundry";

    @ConfigItem(
            keyName = "FirstBar",
            name = "First Bar",
            description = "Choose the first type of bar",
            position = 0
    )
    default SmithableBars FirstBar()
    {
        return SmithableBars.STEEL_BAR;
    }

    @ConfigItem(
            keyName = "firstBarAmount",
            name = "First Bar Amount",
            description = "Choose the first type of bar",
            position = 1
    )
    default int firstBarAmount()
    {
        return 14;
    }

    @ConfigItem(
            keyName = "SecondBars",
            name = "Second Bar",
            description = "Choose the second type of bar",
            position = 2
    )
    default SmithableBars SecondBar()
    {
        return SmithableBars.MITHRIL_BAR;
    }
    
    @ConfigItem(
            keyName = "secondBarAmount",
            name = "Second Bar Amount",
            description = "Choose the second type of bar",
            position = 3
    )
    default int secondBarAmount()
    {
        return 14;
    }
}
