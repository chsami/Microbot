package net.runelite.client.plugins.microbot.autoBuyer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("GE buyer")
@ConfigInformation("Acun's GE buyer <br><br> Start near GE. <br> Make sure to spell item names correct. " +
        "Collects items to bank. " +
        "<br> Be cautious and monitor when using, because there are no failsafes added yet. " +
        "<br><br> Item name[quantity] for example: rune arrow[50],amulet of glory(6)[1]"+
        "<br><br>If Buy Quest Items selected then buy List will be ignored. Ensure Quest Helper has quest selected before starting."
)
public interface AutoBuyerConfig extends Config {

    @ConfigItem(
        keyName = "buyQuest",
            name = "Buy Quest Items",
            description = "Select whether to buy items required by QuestHelper(beta)",
            position = 2
    )
    default boolean buyQuest() {
        return false;
    }

    @ConfigItem(
            keyName = "BuyList",
            name = "Buy items list",
            description = "Give the name of items to buy plus the [quantity], separate by comma for example: Rune arrow[100],Chaos rune[25],Amulet of glory(6)[1]",
            position = 1
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
        return Percentage.PERCENT_10;
    }
}
