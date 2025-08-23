package net.runelite.client.plugins.microbot.zerozero.bluedragons;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup(BlueDragonsPlugin.CONFIG)
@ConfigInformation("<center><p style='font-weight: bold;'>[00] BLUE DRAGON FARMER</p> <br />" +
        "<hr style='width: 75%; border-top: 1px solid white;'/>" +
        "<p style='color: white;'>The plugin will travel/kill and bank as needed. Early prayer farmer!</p>" +
        "<hr style='width: 75%; border-top: 1px solid #000;'/> </center> <br />" +
        "1. Equip your <b>RANGED</b> gear <br />" +
        "2. <b style='color: red;'>MUST</b> have Falador teleport (runes/teletab) <br />" +
        "3. Correct food amount from config <br />" +
        "4. Start with your dusty key in your inventory or have 70 agility <br /> <br />" +
        "<center>Press <b style='color: green;'>START</b> and enjoy :)</center>")
public interface BlueDragonsConfig extends Config {
    @ConfigItem(keyName = "startPlugin", name = "Start/Stop the Plugin", description = "This is start or stop the plugin on a toggle")
    default boolean startPlugin() { return true; }

    @ConfigSection(name = "Loot Options", description = "Settings related to item looting", position = 1)
    String lootSection = "lootSection";

    @ConfigItem(keyName = "lootDragonhide", name = "Loot Blue Dragonhide", description = "Loot blue dragonhide dropped by the dragon", section = lootSection)
    default boolean lootDragonhide() { return true; }

    @ConfigItem(keyName = "lootEnsouledHead", name = "Loot Ensouled heads", description = "LootEnsouled heads dropped by the dragon", section = lootSection)
    default boolean lootEnsouledHead() { return true; }

    @ConfigItem(keyName = "lootMiscItems", name = "Loot items above value", description = "Should we loot items above the specified value threshold?", section = lootSection)
    default boolean lootMiscItems() { return true; }

    @ConfigItem(keyName = "lootValueThreshold", name = "Loot value threshold", description = "Minimum value in GP for items to be looted", section = lootSection)
    default int lootValueThreshold() { return 3500; }

    @ConfigSection(name = "Food Options", description = "Settings for selecting food and health threshold", position = 2)
    String foodSection = "foodSection";

    @ConfigItem(keyName = "foodType", name = " ", description = "Select the type of food to withdraw", section = foodSection)
    default Rs2Food foodType() { return Rs2Food.LOBSTER; }

    @ConfigItem(keyName = "foodAmount", name = "Amount of Food", description = "Specify the number of food items to withdraw", section = foodSection)
    default int foodAmount() { return 3; }

    @ConfigItem(keyName = "eatAtHealthPercent", name = "Eat at Health Percentage", description = "Eat food when health drops below this percentage", section = foodSection)
    default int eatAtHealthPercent() { return 50; }

    @ConfigSection(name = "Debug", description = "Debugging", position = 3, closedByDefault = true)
    String debugSection = "debugSection";

    @ConfigItem(keyName = "debugLogs", name = "Enable Debug Logs", description = "Enable detailed debug logging for this script", section = debugSection)
    default boolean debugLogs() { return false; }
}
