package net.runelite.client.plugins.microbot.antibanpreloader;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;

@ConfigGroup("antibanpreset")
public interface AntibanPresetConfig extends Config {

    // ─────────────────────────────
    // ⚙️ MICRO BREAK SETTINGS
    // ─────────────────────────────

    @ConfigSection(
            name = "Micro Break Settings",
            description = "Fine-tune your micro break behavior",
            position = 0
    )
    String microBreakSection = "microBreakSection";

    @ConfigItem(
            keyName = "takeMicroBreaks",
            name = "Take Micro Breaks",
            description = "Enable micro breaks",
            section = microBreakSection
    )
    default boolean takeMicroBreaks() { return true; }

    @ConfigItem(
            keyName = "microBreakChance",
            name = "Break Chance",
            description = "Chance of taking a micro break (0.0 - 1.0)",
            section = microBreakSection
    )
    @Range(min = 0, max = 1)
    default double microBreakChance() { return 0.02; }

    @ConfigItem(
            keyName = "microBreakDurationLow",
            name = "Duration Min (s)",
            description = "Minimum break duration in seconds",
            section = microBreakSection
    )
    @Range(min = 1, max = 60)
    default int microBreakDurationLow() { return 1; }

    @ConfigItem(
            keyName = "microBreakDurationHigh",
            name = "Duration Max (s)",
            description = "Maximum break duration in seconds",
            section = microBreakSection
    )
    @Range(min = 1, max = 300)
    default int microBreakDurationHigh() { return 4; }

    // ─────────────────────────────
    // 🧠 ANTIBAN BEHAVIOR TOGGLES
    // ─────────────────────────────

    @ConfigSection(
            name = "Antiban Behavior",
            description = "Core antiban logic and interaction options",
            position = 10
    )
    String behaviorSection = "behaviorSection";

    @ConfigItem(
            keyName = "activityIntensity",
            name = "Activity Intensity",
            description = "Set overall antiban intensity level",
            section = behaviorSection
    )
    default ActivityIntensity activityIntensity() { return ActivityIntensity.LOW; }

    @ConfigItem(
            keyName = "usePlayStyle",
            name = "Use Play Style",
            description = "Enable dynamic play style simulation",
            section = behaviorSection
    )
    default boolean usePlayStyle() { return true; }

    @ConfigItem(
            keyName = "simulateFatigue",
            name = "Simulate Fatigue",
            description = "Simulate fatigue over time",
            section = behaviorSection
    )
    default boolean simulateFatigue() { return true; }

    @ConfigItem(
            keyName = "simulateAttentionSpan",
            name = "Simulate Attention Span",
            description = "Simulate changing attention levels",
            section = behaviorSection
    )
    default boolean simulateAttentionSpan() { return true; }

    @ConfigItem(
            keyName = "simulateMistakes",
            name = "Simulate Mistakes",
            description = "Allow intentional small misclicks",
            section = behaviorSection
    )
    default boolean simulateMistakes() { return true; }

    @ConfigItem(
            keyName = "nonLinearIntervals",
            name = "Non-linear Intervals",
            description = "Avoid predictable actions",
            section = behaviorSection
    )
    default boolean nonLinearIntervals() { return true; }

    @ConfigItem(
            keyName = "dynamicActivity",
            name = "Dynamic Activity",
            description = "Adapt behavior over time",
            section = behaviorSection
    )
    default boolean dynamicActivity() { return true; }

    @ConfigItem(
            keyName = "profileSwitching",
            name = "Profile Switching",
            description = "Switch between antiban profiles",
            section = behaviorSection
    )
    default boolean profileSwitching() { return false; }

    @ConfigItem(
            keyName = "naturalMouse",
            name = "Natural Mouse Movement",
            description = "Use smooth mouse pathing",
            section = behaviorSection
    )
    default boolean naturalMouse() { return true; }

    @ConfigItem(
            keyName = "moveMouseOffScreen",
            name = "Move Mouse Off Screen",
            description = "Move mouse off screen periodically",
            section = behaviorSection
    )
    default boolean moveMouseOffScreen() { return true; }

    @ConfigItem(
            keyName = "moveMouseOffScreenChance",
            name = "Off Screen Chance",
            description = "Chance to move mouse off screen (0.0 - 1.0)",
            section = behaviorSection
    )
    @Range(min = 0, max = 1)
    default double moveMouseOffScreenChance() { return 0.07; }

    @ConfigItem(
            keyName = "moveMouseRandomly",
            name = "Move Mouse Randomly",
            description = "Randomly wiggle the mouse",
            section = behaviorSection
    )
    default boolean moveMouseRandomly() { return true; }

    @ConfigItem(
            keyName = "moveMouseRandomlyChance",
            name = "Random Move Chance",
            description = "Chance to move mouse randomly (0.0 - 1.0)",
            section = behaviorSection
    )
    @Range(min = 0, max = 1)
    default double moveMouseRandomlyChance() { return 0.07; }

    // ─────────────────────────────
    // 🧪 EXPERIMENTAL / WIP FEATURES
    // ─────────────────────────────

    @ConfigSection(
            name = "Experimental Features (WIP)",
            description = "⚠️ These settings are experimental and may not work as expected.",
            position = 100,
            closedByDefault = true
    )
    String wipSection = "wipSection";

    @ConfigItem(
            keyName = "hoverNpc",
            name = "Hover NPCs",
            description = "Hover over NPCs randomly (WIP)",
            section = wipSection
    )
    default boolean hoverNpc() { return false; }

    @ConfigItem(
            keyName = "hoverItem",
            name = "Hover Items",
            description = "Hover over inventory items (WIP)",
            section = wipSection
    )
    default boolean hoverItem() { return false; }

    @ConfigItem(
            keyName = "hoverBankItem",
            name = "Hover Bank Items",
            description = "Hover over bank items (WIP)",
            section = wipSection
    )
    default boolean hoverBankItem() { return false; }

    @ConfigItem(
            keyName = "rotateCamera",
            name = "Rotate Camera",
            description = "Occasionally rotate the camera (WIP)",
            section = wipSection
    )
    default boolean rotateCamera() { return false; }

    @ConfigItem(
            keyName = "fakeIdle",
            name = "Fake Idling",
            description = "Stand still for extended periods (WIP)",
            section = wipSection
    )
    default boolean fakeIdle() { return false; }

    @ConfigItem(
            keyName = "randomizeActions",
            name = "Randomize Actions",
            description = "Randomize delays and order of actions (WIP)",
            section = wipSection
    )
    default boolean randomizeActions() { return false; }
}