package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("wildernessagility")
@ConfigInformation(
    "Wilderness Agility v1.5.0<br>" +
    "• Mass & solo compatible<br>" +
    "• See Discord for setup guides<br>" +
    "• Enable 'Start at Course' to skip banking if already paid<br>" +
    "• Inventory for banking start: 150k coins + knife"
)
public interface WildernessAgilityConfig extends Config {

    // === General Settings ===
    @ConfigItem(
        keyName = "startAtCourse",
        name = "Start at Course?",
        description = "Skips coin/dispenser setup. Use if already paid or just farming tickets.",
        position = 1
    )
    default boolean startAtCourse() { return false; }

    @ConfigItem(
        keyName = "joinFc",
        name = "Join fc?",
        description = "If enabled, the script will join the friends chat after banking/world hop.",
        position = 103,
        section = bankingSection
    )
    default boolean joinFc() { return true; }

    @ConfigItem(
        keyName = "runBack",
        name = "Run Back After Death",
        description = "If enabled, script will walk back from death and resume.",
        position = 3
    )
    default boolean runBack() { return false; }

    @ConfigItem(
        keyName = "logoutAfterDeath",
        name = "Log Out After Death",
        description = "If enabled, the script will log out after dying instead of stopping or running back.",
        position = 4
    )
    default boolean logoutAfterDeath() { return true; }

    // === Looting Settings ===
    @ConfigSection(
        name = "Looting Settings",
        description = "Settings for loot thresholds and ticket usage.",
        position = 10
    )
    String lootingSection = "lootingSection";

    @ConfigItem(
        keyName = "leaveAtValue",
        name = "Leave Course At Value",
        description = "Trigger banking after reaching this inventory value.",
        position = 11,
        section = lootingSection
    )
    @Range(min = 1, max = 50_000_000)
    default int leaveAtValue() { return 5_000_000; }

    @ConfigItem(
        keyName = "bankAfterDispensers",
        name = "Bank After Dispensers",
        description = "Bank after this many dispenser loots. 0 = disabled.",
        position = 12,
        section = lootingSection
    )
    @Range(min = 0, max = 1000)
    default int bankAfterDispensers() { return 100; }

    @ConfigItem(
        keyName = "useTicketsWhen",
        name = "Use Tickets When",
        description = "Spend tickets if you have this many in inventory.",
        position = 13,
        section = lootingSection
    )
    @Range(min = 1, max = 200_000)
    default int useTicketsWhen() { return 101; }

    // === Banking Settings ===
    @ConfigSection(
        name = "Banking Settings",
        description = "Settings for world hopping and item preparation during banking.",
        position = 100
    )
    String bankingSection = "bankingSection";

    @ConfigItem(
        keyName = "enableWorldHop",
        name = "Enable World Hop",
        description = "Hop to alternate worlds during banking for anti-ban.",
        position = 101,
        section = bankingSection
    )
    default boolean enableWorldHop() { return true; }

    @ConfigItem(
        keyName = "swapBack",
        name = "Swap Back to Original World",
        description = "Return to the original world after banking.",
        position = 102,
        section = bankingSection
    )
    default boolean swapBack() { return true; }

    @ConfigItem(
        keyName = "useIcePlateauTp",
        name = "Withdraw Ice Plateau TP",
        description = "Take teleport from bank during banking (if used for return).",
        position = 103,
        section = bankingSection
    )
    default boolean useIcePlateauTp() { return false; }

    @ConfigItem(
        keyName = "bankNow",
        name = "Force Bank Next Loot",
        description = "Immediately bank on next dispenser loot, regardless of thresholds.",
        position = 104,
        section = bankingSection
    )
    default boolean bankNow() { return false; }

    @ConfigItem(
        keyName = "fcChannel",
        name = "FC Name",
        description = "The friends chat channel to join after banking/world hop (if enabled).",
        position = 107,
        section = bankingSection
    )
    default String fcChannel() { return "agility fc"; }

    @ConfigItem(
        keyName = "withdrawCoins",
        name = "Withdraw Coins",
        description = "If enabled, the script will withdraw coins during banking.",
        position = 108,
        section = bankingSection
    )
    default boolean withdrawCoins() { return false; }

    enum BankWorldOption {
        Random,
        W303, W304, W305, W306, W307, W309, W310, W311, W312, W313, W314, W315, W317, W320,
        W321, W322, W323, W324, W325, W327, W328, W329, W330, W331, W332, W333, W334, W336,
        W337, W338, W339, W340, W341, W342, W343, W344, W346, W347, W348, W350, W351,
        W352, W354, W355, W356, W357, W358, W359, W360, W362, W365, W367, W368, W369, W370,
        W371, W374, W375, W376, W377, W378, W385, W386, W387, W388, W389, W394, W395, W421,
        W422, W423, W424, W425, W426, W438, W439, W440, W441, W443, W444, W445, W446, W458,
        W459, W463, W464, W465, W466, W474, W477, W478, W479, W480, W481, W482, W484, W485,
        W486, W487, W488, W489, W490, W491, W492, W493, W494, W495, W496, W505, W506, W507,
        W508, W509, W510, W511, W512, W513, W514, W515, W516, W517, W518, W519, W520, W521,
        W522, W523, W524, W525, W529, W531, W532, W533, W534, W535, W567, W570, W573, W578
    }

    @ConfigItem(
        keyName = "bankWorld1",
        name = "Bank World #1",
        description = "First world to hop to for banking.",
        position = 105,
        section = bankingSection
    )
    default BankWorldOption bankWorld1() { return BankWorldOption.Random; }

    @ConfigItem(
        keyName = "bankWorld2",
        name = "Bank World #2",
        description = "Second world to hop to for banking.",
        position = 106,
        section = bankingSection
    )
    default BankWorldOption bankWorld2() { return BankWorldOption.Random; }

    // === Fail-Safe Settings ===
    @ConfigSection(
        name = "Fail-Safe Settings",
        description = "Timeouts, recovery, and debug options for failed interactions.",
        position = 200,
        closedByDefault = true
    )
    String failSafeSection = "failSafeSection";

    @ConfigItem(
        keyName = "failTimeoutMs",
        name = "Animation Fail Timeout (ms)",
        description = "If no animation starts after interacting, retry after this timeout.",
        position = 201,
        section = failSafeSection
    )
    @Range(min = 500, max = 8000)
    default int failTimeoutMs() { return 4500; }

    @ConfigItem(
        keyName = "debugMode",
        name = "Debug Mode",
        description = "Enable debug mode to start the script in a specific state.",
        position = 210,
        section = failSafeSection
    )
    default boolean debugMode() { return false; }

    enum DebugStateOption {
        INIT, START, PIPE, ROPE, STONES, LOG, ROCKS, DISPENSER, CONFIG_CHECKS,
        WORLD_HOP_1, WORLD_HOP_2, WALK_TO_LEVER, INTERACT_LEVER, BANKING, POST_BANK_CONFIG, WALK_TO_COURSE, SWAP_BACK, PIT_RECOVERY
    }

    @ConfigItem(
        keyName = "debugStartState",
        name = "Start in which state",
        description = "State to start in when Debug Mode is enabled.",
        position = 211,
        section = failSafeSection
    )
    default DebugStateOption debugStartState() { return DebugStateOption.START; }

    @ConfigItem(
        keyName = "debugStartStateVisible",
        name = "Show Debug Start State",
        description = "Show the debug start state dropdown only if debug mode is enabled.",
        hidden = true
    )
    default boolean debugStartStateVisible() { return debugMode(); }
}
