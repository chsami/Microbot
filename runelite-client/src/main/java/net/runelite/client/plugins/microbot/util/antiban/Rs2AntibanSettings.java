package net.runelite.client.plugins.microbot.util.antiban;

/**
 * Provides configuration settings for the anti-ban system used by various plugins within the bot framework.
 *
 * <p>
 * The <code>Rs2AntibanSettings</code> class contains a collection of static fields that define behaviors
 * and settings related to anti-ban mechanisms. These settings control how the bot simulates human-like
 * behavior to avoid detection during automated tasks. Each setting adjusts a specific aspect of the
 * anti-ban system, including break patterns, mouse movements, play style variability, and other behaviors
 * designed to mimic natural human interaction with the game.
 * </p>
 *
 * <h3>Main Features:</h3>
 * <ul>
 *   <li><strong>Action Cooldowns:</strong> Controls the cooldown behavior of actions, including random intervals
 *   and non-linear patterns.</li>
 *   <li><strong>Micro Breaks:</strong> Defines settings for taking small breaks at random intervals to simulate human pauses.</li>
 *   <li><strong>Play Style Simulation:</strong> Includes variables to simulate different play styles, attention span,
 *   and behavioral variability to create a more realistic user profile.</li>
 *   <li><strong>Mouse Movements:</strong> Settings to control mouse behavior, such as moving off-screen or randomly,
 *   mimicking natural user actions.</li>
 *   <li><strong>Dynamic Behaviors:</strong> Provides options to dynamically adjust activity intensity and behavior
 *   based on context and time of day.</li>
 * </ul>
 *
 * <h3>Fields:</h3>
 * <ul>
 *   <li><code>actionCooldownActive</code>: Tracks whether action cooldowns are currently active.</li>
 *   <li><code>microBreakActive</code>: Indicates if a micro break is currently active.</li>
 *   <li><code>antibanEnabled</code>: Globally enables or disables the anti-ban system.</li>
 *   <li><code>usePlayStyle</code>: Determines whether play style simulation is active.</li>
 *   <li><code>randomIntervals</code>: Enables random intervals between actions to avoid detection.</li>
 *   <li><code>simulateFatigue</code>: Simulates user fatigue by introducing delays or slower actions.</li>
 *   <li><code>simulateAttentionSpan</code>: Simulates varying levels of user attention over time.</li>
 *   <li><code>behavioralVariability</code>: Adds variability to actions to simulate a human's inconsistency.</li>
 *   <li><code>nonLinearIntervals</code>: Activates non-linear time intervals between actions.</li>
 *   <li><code>profileSwitching</code>: Simulates user behavior switching profiles at intervals.</li>
 *   <li><code>timeOfDayAdjust</code>: (TODO) Adjusts behaviors based on the time of day.</li>
 *   <li><code>simulateMistakes</code>: Simulates user mistakes, often controlled by natural mouse movements.</li>
 *   <li><code>naturalMouse</code>: Enables natural-looking mouse movements.</li>
 *   <li><code>moveMouseOffScreen</code>: Moves the mouse off-screen during breaks to simulate user behavior.</li>
 *   <li><code>moveMouseRandomly</code>: Moves the mouse randomly to simulate human inconsistency.</li>
 *   <li><code>contextualVariability</code>: Adjusts behaviors based on the context of the user's actions.</li>
 *   <li><code>dynamicIntensity</code>: Dynamically adjusts the intensity of user actions based on context.</li>
 *   <li><code>dynamicActivity</code>: Adjusts activities dynamically based on the user's behavior profile.</li>
 *   <li><code>devDebug</code>: Enables debug mode for developers to inspect the anti-ban system's state.</li>
 *   <li><code>takeMicroBreaks</code>: Controls whether the bot takes micro breaks at random intervals.</li>
 *   <li><code>playSchedule</code>: (TODO) Allows scheduling of playtime based on specific conditions.</li>
 *   <li><code>universalAntiban</code>: Applies the same anti-ban settings across all plugins.</li>
 *   <li><code>microBreakDurationLow</code>: Minimum duration for micro breaks, in minutes.</li>
 *   <li><code>microBreakDurationHigh</code>: Maximum duration for micro breaks, in minutes.</li>
 *   <li><code>actionCooldownChance</code>: Probability of triggering an action cooldown.</li>
 *   <li><code>microBreakChance</code>: Probability of taking a micro break.</li>
 *   <li><code>moveMouseRandomlyChance</code>: Probability of moving the mouse randomly.</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <p>
 * These settings are typically used by anti-ban mechanisms within various plugins to adjust their behavior
 * dynamically based on the user's preferences or to simulate human-like play styles. Developers can adjust
 * these fields based on the needs of their specific automation scripts.
 * </p>
 *
 * <h3>Example:</h3>
 * <pre>
 * // Enable fatigue simulation and random intervals
 * Rs2AntibanSettings.simulateFatigue = true;
 * Rs2AntibanSettings.randomIntervals = true;
 *
 * // Set the micro break chance to 20%
 * Rs2AntibanSettings.microBreakChance = 0.2;
 * </pre>
 */

public class Rs2AntibanSettings {
    public static boolean actionCooldownActive = false;
    public static boolean microBreakActive = false;
    public static boolean antibanEnabled = true;
    public static boolean usePlayStyle = false;
    public static boolean randomIntervals = false;
    public static boolean simulateFatigue = false;
    public static boolean simulateAttentionSpan = false;
    public static boolean behavioralVariability = false;
    public static boolean nonLinearIntervals = false;
    public static boolean profileSwitching = false;
    public static boolean timeOfDayAdjust = false; //TODO: Implement this
    public static boolean simulateMistakes = false; //Handled by the natural mouse
    public static boolean naturalMouse = false;
    public static boolean moveMouseOffScreen = false;
    public static boolean moveMouseRandomly = false;
    public static boolean contextualVariability = false;
    public static boolean dynamicIntensity = false;
    public static boolean dynamicActivity = false;
    public static boolean devDebug = false;
    public static boolean overwriteScriptSettings = false;

    public static boolean takeMicroBreaks = false; // will take micro breaks lasting 3-15 minutes at random intervals by default.
    public static boolean playSchedule = false; //TODO: Implement this
    public static boolean universalAntiban = false; // Will attempt to use the same antiban settings for all plugins that has not yet implemented their own antiban settings.
    public static int microBreakDurationLow = AntibanPlugin.MICRO_BREAK_DURATION_LOW_DEFAULT; // 3 minutes
    public static int microBreakDurationHigh = AntibanPlugin.MICRO_BREAK_DURATION_HIGH_DEFAULT; // 15 minutes
    public static double actionCooldownChance = 0.1; // 10% chance of activating the action cooldown by default
    public static double microBreakChance = 0.1; // 10% chance of taking a micro break by default
    public static double moveMouseRandomlyChance = 0.1; // 10% chance of moving the mouse randomly by default
    public static double moveMouseOffScreenChance = 0.1; // 10% chance of moving the mouse off screen by default

    // reset method to reset all settings to default values
    public static void reset() {
        actionCooldownActive = false;
        microBreakActive = false;
        antibanEnabled = true;
        usePlayStyle = false;
        randomIntervals = false;
        simulateFatigue = false;
        simulateAttentionSpan = false;
        behavioralVariability = false;
        nonLinearIntervals = false;
        profileSwitching = false;
        timeOfDayAdjust = false;
        simulateMistakes = false;
        naturalMouse = false;
        moveMouseOffScreen = false;
        moveMouseRandomly = false;
        contextualVariability = false;
        dynamicIntensity = false;
        dynamicActivity = false;
        devDebug = false;
        takeMicroBreaks = false;
        playSchedule = false;
        universalAntiban = false;
        microBreakDurationLow = 3;
        microBreakDurationHigh = 15;
        actionCooldownChance = 0.1;
        microBreakChance = 0.1;
        moveMouseRandomlyChance = 0.1;
        moveMouseOffScreenChance = 0.1;
    }
}