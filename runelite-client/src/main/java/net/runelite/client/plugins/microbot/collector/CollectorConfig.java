package net.runelite.client.plugins.microbot.collector;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigInformation;

@ConfigGroup("collector")
@ConfigInformation("1. <b>Mort Myre Fungus Collection:</b> <br />" +
        "   - Requires Silver sickle (b) in inventory <br />" +
        "   - Ardy cloak, Dramen staff & RoD equipped <br />" +
        "   - Automatically banks & replenishes prayer <br />" +
        "   - Automatically replaces broken RoD <br />" +
        "2. <b>Snape Grass Collection:</b> <br />" +
        "   - Collects from ground spawns in Hosidius <br />" +
        "   - Banks at Vinery bank <br />" +
        "3. <b>Super Anti-Poison Collection:</b> <br />" +
        "   - Collects from ground spawns <br />" +
        "   - Banks at Castle Wars <br />" +
        "   - Automatically replaces broken RoD <br />" +
        "   - Automatically hops worlds when no potions are available")
public interface CollectorConfig extends Config {
    @ConfigItem(
            keyName = "collectMortMyreFungus",
            name = "Collect Mort Myre Fungus",
            description = "Enable collecting Mort Myre Fungus",
            position = 0
    )
    default boolean collectMortMyreFungus() {
        return false;
    }

    @ConfigItem(
            keyName = "collectSnapeGrass",
            name = "Collect Snape Grass",
            description = "Enable to collect snape grass",
            position = 1
    )
    default boolean collectSnapeGrass()
    {
        return false;
    }

    @ConfigItem(
            keyName = "collectSuperAntiPoison",
            name = "Collect Super Anti-Poison",
            description = "Enable to collect super anti-poison",
            position = 2
    )
    default boolean collectSuperAntiPoison()
    {
        return false;
    }
}
