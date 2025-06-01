package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigSection;

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

    @ConfigSection(
        name = "Banking Settings",
        description = "Settings for advanced banking logic",
        position = 10
    )
    String bankingSection = "bankingSection";

    @ConfigItem(
        keyName = "enableWorldHop",
        name = "World hop?",
        description = "Enable world hopping during banking (recommended for anti-ban)",
        position = 10,
        section = bankingSection
    )
    default boolean enableWorldHop() { return true; }

    // Add enum for dropdown
    public enum BankWorldOption {
        Random,
        W303, W304, W305, W306, W307, W309, W310, W311, W312, W313, W314, W315, W317, W320, W321, W322, W323, W324, W325, W327, W328, W329, W330, W331, W332, W333, W334, W336, W337, W338, W339, W340, W341, W342, W343, W344, W345, W346, W347, W348, W350, W351, W352, W354, W355, W356, W357, W358, W359, W360, W362, W365, W367, W368, W369, W370, W371, W374, W375, W376, W377, W378, W385, W386, W387, W388, W389, W394, W395, W421, W422, W423, W424, W425, W426, W438, W439, W440, W441, W443, W444, W445, W446, W458, W459, W463, W464, W465, W466, W474, W477, W478, W479, W480, W481, W482, W484, W485, W486, W487, W488, W489, W490, W491, W492, W493, W494, W495, W496, W505, W506, W507, W508, W509, W510, W511, W512, W513, W514, W515, W516, W517, W518, W519, W520, W521, W522, W523, W524, W525, W529, W531, W532, W533, W534, W535, W567, W570, W573, W578
    }

    @ConfigItem(
        keyName = "bankWorld1",
        name = "World 1",
        description = "First world to hop to for banking (or Random)",
        position = 11,
        section = bankingSection
    )
    default BankWorldOption bankWorld1() {
        return BankWorldOption.Random;
    }

    @ConfigItem(
        keyName = "bankWorld2",
        name = "World 2",
        description = "Second world to hop to for banking (or Random)",
        position = 12,
        section = bankingSection
    )
    default BankWorldOption bankWorld2() {
        return BankWorldOption.Random;
    }

    @ConfigItem(
        keyName = "bankNow",
        name = "Bank now",
        description = "Force banking on the next dispenser loot regardless of threshold.",
        position = 99,
        section = bankingSection
    )
    default boolean bankNow() { return false; }
} 