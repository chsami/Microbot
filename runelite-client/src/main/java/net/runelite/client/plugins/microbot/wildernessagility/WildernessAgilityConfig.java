package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.ConfigInformation;

@ConfigGroup("wildernessagility")
@ConfigInformation(
    "Wilderness Agility Course by Cranny<br><br>" +
    "• Works both mass and solo<br>" +
    "• See discord for setup guides<br>" +
    "• Enable \"start at course?\" if you've already deposited 150k coins or want to run without getting loot (still will get tickets)<br>" +
    "• Inventory requirements for mass (starting at a bank): 150k coins, knife, ice plateau teleport"
)
public interface WildernessAgilityConfig extends Config {
    @ConfigItem(
        keyName = "leaveAtValue",
        name = "Leave Course at Value",
        description = "How much loot should we gain before banking? (e.g., 100,000 GP)",
        position = 2
    )
    @Range(min = 1, max = 50000000)
    default int leaveAtValue() {
        return 100000;
    }

    @ConfigItem(
        keyName = "useTicketsWhen",
        name = "Use tickets when",
        description = "Use tickets on the dispenser when you have this many or more in your inventory.",
        position = 4
    )
    @Range(min = 0, max = 200000)
    default int useTicketsWhen() { return 0; }

    @ConfigItem(
        keyName = "startAtCourse",
        name = "Start at Course?",
        description = "If enabled, skip the coin/dispenser check and start the course immediately.",
        position = 5
    )
    default boolean startAtCourse() { return false; }
} 