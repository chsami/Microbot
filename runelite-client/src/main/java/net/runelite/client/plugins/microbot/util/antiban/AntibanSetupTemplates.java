package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;

/**
 * The {@code AntibanSetupTemplates} class provides predefined antiban setup configurations tailored to specific
 * in-game activities. These configurations are designed to mimic human-like behaviors such as fatigue simulation,
 * attention span, and behavioral variability, with the goal of reducing detection risks by anti-cheat systems.
 *
 * <p>
 * Each method in this class corresponds to a specific activity (e.g., combat, runecrafting, construction) and
 * adjusts various settings to create a more realistic and undetectable experience for the bot.
 * These setups adjust mouse movement patterns, simulate breaks, introduce variability, and more.
 * </p>
 *
 * <h3>Main Features:</h3>
 * <ul>
 *   <li>Activity-Specific Setups: Methods are provided to apply antiban configurations for a wide range of activities,
 *       including skilling, combat, and more specialized tasks like runecrafting and herblore.</li>
 *   <li>Human-Like Behavior Simulation: The setups simulate human-like behaviors to avoid detection, including fatigue,
 *       attention span, micro breaks, mouse movement patterns, and random intervals.</li>
 *   <li>Flexible Configurations: Each setup method customizes settings such as action cooldowns, behavioral variability,
 *       and micro breaks to tailor the antiban behavior to the specific activity.</li>
 *   <li>Basic Setup: A general setup method is included for cases where simpler antiban measures are needed without
 *       advanced features like attention span or micro breaks.</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <p>
 * These methods are intended to be called before executing specific game activities to ensure that the antiban measures
 * are properly configured for the task. The configurations can be adjusted to suit different activities or playstyles
 * by enabling or disabling certain features like profile switching or dynamic intensity.
 * </p>
 *
 * <h3>Example:</h3>
 * <p>Inside your plugin script class, execute the initialization outside the main loop.</p>
 * <pre>
 * private void initialize() {
 *         Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
 *     }
 * </pre>
 *
 * <h3>Available Setup Methods:</h3>
 * <ul>
 *   <li><code>applyCombatSetup()</code>: Configures antiban settings for combat activities.</li>
 *   <li><code>applyRunecraftingSetup()</code>: Configures antiban settings for runecrafting activities.</li>
 *   <li><code>applyConstructionSetup()</code>: Configures antiban settings for construction activities.</li>
 *   <li><code>applyAgilitySetup()</code>: Configures antiban settings for agility tasks.</li>
 *   <li><code>applyHerbloreSetup()</code>: Configures antiban settings for herblore tasks.</li>
 *   <li><code>applyThievingSetup()</code>: Configures antiban settings for thieving tasks.</li>
 *   <li><code>applyCraftingSetup()</code>: Configures antiban settings for crafting tasks.</li>
 *   <li><code>applyFletchingSetup()</code>: Configures antiban settings for fletching tasks.</li>
 *   <li><code>applyHunterSetup()</code>: Configures antiban settings for hunter tasks.</li>
 *   <li><code>applyMiningSetup()</code>: Configures antiban settings for mining tasks.</li>
 *   <li><code>applySmithingSetup()</code>: Configures antiban settings for smithing tasks.</li>
 *   <li><code>applyFishingSetup()</code>: Configures antiban settings for fishing tasks.</li>
 *   <li><code>applyCookingSetup()</code>: Configures antiban settings for cooking tasks.</li>
 *   <li><code>applyFiremakingSetup()</code>: Configures antiban settings for firemaking tasks.</li>
 *   <li><code>applyWoodcuttingSetup()</code>: Configures antiban settings for woodcutting tasks.</li>
 *   <li><code>applyFarmingSetup()</code>: Configures antiban settings for farming tasks.</li>
 *   <li><code>applyGeneralBasicSetup()</code>: Applies a basic antiban configuration without advanced features.</li>
 * </ul>
 */

public class AntibanSetupTemplates {
    /**
     * Applies the antiban setup tailored for general combat activities.
     * This setup enables various human-like behaviors such as fatigue simulation, attention span,
     * and mouse movement variability to reduce detection risk.
     */
    public void applyCombatSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_COMBAT);
    }

    /**
     * Applies the antiban setup tailored for runecrafting activities.
     * This setup adjusts settings to simulate human-like behaviors during runecrafting tasks.
     */
    public void applyRunecraftingSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Runecrafting setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_RUNECRAFT);
    }

    /**
     * Applies the antiban setup tailored for construction activities.
     * This setup focuses on mimicking human-like behaviors during construction tasks.
     */
    public void applyConstructionSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Construction setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_CONSTRUCTION);
    }

    /**
     * Applies the antiban setup tailored for agility activities.
     * This setup includes adjustments to simulate human-like behaviors during agility tasks.
     */

    public void applyAgilitySetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Agility setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 0.2;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_AGILITY);
    }

    /**
     * Applies the antiban setup tailored for herblore activities.
     * This setup configures settings to mimic human-like behaviors during herblore tasks.
     */
    public void applyHerbloreSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Herblore setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_HERBLORE);
    }

    /**
     * Applies the antiban setup tailored for thieving activities.
     * This setup simulates human-like behaviors during thieving tasks to reduce detection risk.
     */
    public void applyThievingSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Thieving setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_THIEVING);
    }

    /**
     * Applies the antiban setup tailored for crafting activities.
     * This setup focuses on human-like behavior simulation during crafting tasks.
     */
    public void applyCraftingSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Crafting setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_CRAFTING);
    }

    /**
     * Applies the antiban setup tailored for fletching activities.
     * This setup adjusts settings to mimic human behavior during fletching tasks.
     */
    public void applyFletchingSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Fletching setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_FLETCHING);
    }

    public void applySlayerSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Slayer setup
    }

    /**
     * Applies the antiban setup tailored for hunter activities.
     * This setup simulates human-like behaviors during hunting tasks.
     */
    public void applyHunterSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Hunter setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_HUNTER);
    }

    /**
     * Applies the antiban setup tailored for mining activities.
     * This setup includes adjustments to mimic human behaviors during mining tasks.
     */
    public void applyMiningSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Mining setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = false;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 1;
        Rs2AntibanSettings.microBreakDurationHigh = 4;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_MINING);
    }

    /**
     * Applies the antiban setup tailored for smithing activities.
     * This setup configures settings to simulate human-like behaviors during smithing tasks.
     */
    public void applySmithingSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Smithing setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_SMITHING);
    }

    /**
     * Applies the antiban setup tailored for fishing activities.
     * This setup focuses on mimicking human-like behaviors during fishing tasks.
     */
    public void applyFishingSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Fishing setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_FISHING);
    }

    /**
     * Applies the antiban setup tailored for cooking activities.
     * This setup simulates human-like behaviors during cooking tasks to reduce detection risk.
     */
    public void applyCookingSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Cooking setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_COOKING);
    }

    /**
     * Applies the antiban setup tailored for firemaking activities.
     * This setup is designed to simulate human behavior during firemaking tasks.
     */
    public void applyFiremakingSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Firemaking setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_FIREMAKING);
    }

    /**
     * Applies the antiban setup tailored for woodcutting activities.
     * This setup mimics human-like behaviors during woodcutting tasks to reduce detection risk.
     */
    public void applyWoodcuttingSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Woodcutting setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_WOODCUTTING);
    }

    /**
     * Applies the antiban setup tailored for farming activities.
     * This setup configures settings to simulate human-like behaviors during farming tasks.
     */
    public void applyFarmingSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for Farming setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = false;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
        Rs2Antiban.setActivity(Activity.GENERAL_FARMING);
    }

    /**
     * Applies the basic antiban setup.
     * This setup configures settings to simulate human-like mouse movement and reduce detection risk.
     * This setup does not include advanced features such as action cooldown, attention span or micro breaks.
     */
    public void applyGeneralBasicSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for General Basic setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = false;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = false;
        Rs2AntibanSettings.behavioralVariability = false;
        Rs2AntibanSettings.nonLinearIntervals = false;
        Rs2AntibanSettings.profileSwitching = false;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = false;
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = false;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
    }
    /**
     * Applies the basic antiban setup.
     * This setup configures settings to simulate human-like mouse movement and reduce detection risk.
     * This setup does not include advanced features such as action cooldown, attention span or micro breaks.
     */
    public void applyUniversalAntibanSetup() {
        if (Rs2AntibanSettings.overwriteScriptSettings){
            return;
        }
        // Implementation for General Basic setup
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.playSchedule = false;
        Rs2AntibanSettings.universalAntiban = true;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 8;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.05;
    }

}

