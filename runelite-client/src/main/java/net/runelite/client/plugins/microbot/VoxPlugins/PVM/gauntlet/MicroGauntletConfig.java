package net.runelite.client.plugins.microbot.VoxPlugins.PVM.gauntlet;

import net.runelite.client.config.*;

/**
 * Configuration for Gauntlet automation plugin
 *
 * Author: Voxslyvae
 */
@ConfigGroup("microgauntlet")
public interface MicroGauntletConfig extends Config {

    // general settings
    @ConfigSection(
        name = "General settings",
        description = "basic plugin configuration",
        position = 0
    )
    String generalSection = "general";

    @ConfigItem(
        keyName = "enablePlugin",
        name = "Enable automation",
        description = "enable automated gauntlet helper",
        section = generalSection,
        position = 0
    )
    default boolean enablePlugin() {
        return true;
    }

    @ConfigItem(
        keyName = "corruptedMode",
        name = "Corrupted mode",
        description = "enable for corrupted gauntlet (hard mode)",
        section = generalSection,
        position = 1
    )
    default boolean corruptedMode() {
        return false;
    }

    // combat settings
    @ConfigSection(
        name = "Combat settings",
        description = "health, eating, and combat behavior",
        position = 1
    )
    String combatSection = "combat";

    @ConfigItem(
        keyName = "eatingThreshold",
        name = "Eating HP %",
        description = "eat when HP falls below this percentage (default: 60%)",
        section = combatSection,
        position = 0
    )
    @Range(min = 30, max = 90)
    default int eatingThreshold() {
        return 60;
    }

    @ConfigItem(
        keyName = "emergencyEatingThreshold",
        name = "Emergency eat HP %",
        description = "emergency eat threshold - ignores tick loss (default: 40%)",
        section = combatSection,
        position = 1
    )
    @Range(min = 20, max = 60)
    default int emergencyEatingThreshold() {
        return 40;
    }

    @ConfigItem(
        keyName = "foodType",
        name = "Food type",
        description = "preferred food type (auto-detect recommended)",
        section = combatSection,
        position = 2
    )
    default FoodType foodType() {
        return FoodType.AUTO_DETECT;
    }

    @ConfigItem(
        keyName = "dodgeStomps",
        name = "Dodge stomps",
        description = "automatically dodge Hunllef's stomp attacks",
        section = combatSection,
        position = 3
    )
    default boolean dodgeStomps() {
        return true;
    }

    @ConfigItem(
        keyName = "dodgeHazards",
        name = "Dodge hazards",
        description = "automatically avoid tornadoes and floor tiles",
        section = combatSection,
        position = 4
    )
    default boolean dodgeHazards() {
        return true;
    }

    // anti-ban settings
    @ConfigSection(
        name = "Anti-ban settings",
        description = "human-like behavior simulation to avoid detection",
        position = 2
    )
    String antiBanSection = "antiBan";

    @ConfigItem(
        keyName = "antiBanEnabled",
        name = "Enable anti-ban",
        description = "add human-like variance and occasional mistakes (HIGHLY RECOMMENDED)",
        section = antiBanSection,
        position = 0
    )
    default boolean antiBanEnabled() {
        return true;
    }

    @ConfigItem(
        keyName = "errorRate",
        name = "Mistake rate %",
        description = "percentage of actions that are imperfect (0-10%, default: 3%)",
        section = antiBanSection,
        position = 1
    )
    @Range(min = 0, max = 10)
    default int errorRate() {
        return 3;
    }

    @ConfigItem(
        keyName = "reactionDelay",
        name = "Reaction delay",
        description = "simulate human reaction time (50-250ms delays)",
        section = antiBanSection,
        position = 2
    )
    default boolean reactionDelay() {
        return true;
    }

    @ConfigItem(
        keyName = "timingVariance",
        name = "Timing variance",
        description = "randomize action timing by ±100ms (less predictable)",
        section = antiBanSection,
        position = 3
    )
    default boolean timingVariance() {
        return true;
    }

    @ConfigItem(
        keyName = "weaponSwitchVariance",
        name = "Weapon switch variance",
        description = "randomize weapon choice when multiple options valid",
        section = antiBanSection,
        position = 4
    )
    default boolean weaponSwitchVariance() {
        return true;
    }

    // debug settings
    @ConfigSection(
        name = "Debug settings",
        description = "overlay and logging configuration",
        position = 3
    )
    String debugSection = "debug";

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show overlay",
        description = "display debug information overlay",
        section = debugSection,
        position = 0
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
        keyName = "showAttackCounter",
        name = "Show attack counter",
        description = "display attack cycle counter",
        section = debugSection,
        position = 1
    )
    default boolean showAttackCounter() {
        return true;
    }

    @ConfigItem(
        keyName = "verboseLogging",
        name = "Verbose logging",
        description = "log detailed debug information to console",
        section = debugSection,
        position = 2
    )
    default boolean verboseLogging() {
        return false;
    }

    /**
     * food type enumeration
     */
    enum FoodType {
        AUTO_DETECT("Auto-detect (recommended)"),
        COOKED_PADDLEFISH("Cooked paddlefish"),
        CRYSTAL_FOOD("Crystal food"),
        CORRUPTED_FOOD("Corrupted food");

        private final String displayName;

        FoodType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
