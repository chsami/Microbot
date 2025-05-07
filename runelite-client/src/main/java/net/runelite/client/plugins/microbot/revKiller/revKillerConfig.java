package net.runelite.client.plugins.microbot.revKiller;

import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.*;

@ConfigGroup("Rev Killer")
@ConfigInformation("1. Start fully equipped at the Enclave.<br /><br />Keep runtime low! Rev caves are HEAVILY monitored. <br /><br />Required items: Stamina potions, Ranging potions, Arrows, Sharks, Rings of Dueling, and Amulets of Glory.<br /><br />")
public interface revKillerConfig extends Config {

    @ConfigItem(
            keyName = "selectedRev",
            name = "Select a Rev",
            description = "Select a Rev",
            position = 0
    )
    default revKillerConfig.RevSelections selectedRev() {
        return RevSelections.IMP; // Default selection
    }

    enum RevSelections {
        IMP(new WorldPoint(3199, 10071, 0), "Rev Imps"),
        GOBLIN(new WorldPoint(3226, 10067, 0), "Rev Goblins"),
        HOBGOBLIN(new WorldPoint(3242, 10099, 0), "Rev Hobgoblins"),
        PYREFIEND(new WorldPoint(3174, 10154, 0), "Rev Pyrefiend"),
        CYCLOPS(new WorldPoint(3170, 10189, 0), "Rev Cyclops"),
        DEMON(new WorldPoint(3160, 10114, 0), "Rev Demon"),
        DARKBEAST(new WorldPoint(3207, 10163, 0), "Rev Darkbeast"),
        ORK(new WorldPoint(3215, 10096, 0), "Rev Orks");
        //more to come I'm lazy okay

        private final WorldPoint wp;
        private final String name;

        RevSelections(WorldPoint wp, String name) {
            this.wp = wp;
            this.name = name;
        }


        public WorldPoint getWorldPoint() {
            return wp;
        }

        public String getName() {
            return name;
        }

    }

    @ConfigItem(
            keyName = "selectedArrow",
            name = "Select an arrow type",
            description = "Select an arrow type",
            position = 0
    )
    default revKillerConfig.ArrowSelections selectedArrow() {
        return ArrowSelections.RUNE; // Default selection
    }

    enum ArrowSelections {
        RUNE(ItemID.RUNE_ARROW, "Rune arrows"),
        AMETHYST(ItemID.AMETHYST_ARROW, "Amethyst arrows");
        //more to come I'm lazy okay

        private final int id;
        private final String name;

        ArrowSelections(int id, String name) {
            this.id = id;
            this.name = name;
        }


        public int getArrowID() {
            return id;
        }

        public String getArrowName() {
            return name;
        }

    }

    @ConfigItem(
            keyName = "leaveAtValue",
            name = "Leave Caves at: ",
            description = "How much loot should we gain before banking? IE bank at 100,000 GP.",
            position = 3
    )
    @Range(min = 100000, max = 5000000)
    default int leaveAtValue() {
        return 100000;
    }

    @ConfigItem(
            keyName = "shouldUseTimedWorldHopper",
            name = "Hop worlds based on time?",
            description = "Should we hop worlds every X minutes?",
            position = 4
    )
    default boolean shouldUseTimedWorldHopper() {
        return false;
    }

    @ConfigItem(
            keyName = "hopInMinutes",
            name = "Time (minutes): ",
            description = "In minutes. How long should we stay on the same world? (will be slightly randomized after selection)",
            position = 5
    )
    @Range(min = 8, max = 30)
    default int hopInMinutes() {
        return 15;
    }
}