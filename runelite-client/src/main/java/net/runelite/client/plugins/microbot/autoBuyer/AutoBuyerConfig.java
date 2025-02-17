package net.runelite.client.plugins.microbot.autoBuyer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("GE buyer")
@ConfigInformation("Acun <br> 0.1.0-alpha.1 <br><br> Start near GE. <br> Make sure to spell item names correct. " +
        "Collects items to bank. " +
        "<br> Be cautious and monitor when using, because there are no failsafes added yet. " +
        "<br><br> Item name[quantity] for example: rune arrow[50],amulet of glory(6)[1]"
)
public interface AutoBuyerConfig extends Config {
    @ConfigItem(
            keyName = "BuyList",
            name = "Buy items list",
            description = "Give the name of items to buy plus the [quantity], separate by comma for example: Rune arrow[100],Chaos rune[25],Amulet of glory(6)[1]",
            position = 2
    )
    default String listOfItemsToBuy() {
        return "bronze arrow[5],air rune[2]";
    }

    @ConfigItem(
            keyName = "pricePerItem",
            name = "Increase price per item",
            description = "Set the price per item for example +5%, this will increase the chance to instant buying",
            position = 1
    )
    default Percentage pricePerItem() {
        return Percentage.PERCENT_5;
    }
}
