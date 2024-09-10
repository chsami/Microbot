package net.runelite.client.plugins.microbot.util.antiban;

import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.Category;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.components.*;
import net.runelite.client.util.ColorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Set;

import static net.runelite.api.AnimationID.*;

/**
 * The {@code Rs2Antiban} class provides a comprehensive anti-ban system that simulates human-like behavior
 * during various in-game activities. This system includes features such as mouse fatigue, random intervals,
 * micro-breaks, action cooldowns, and contextually aware mouse movements, all aimed at reducing the risk
 * of detection by anti-cheat systems.
 *
 * <p>
 * The class uses configurations set in {@code Rs2AntibanSettings} to determine the behavior of the bot
 * during activities like woodcutting, mining, cooking, and more. It leverages various methods to simulate
 * natural player behaviors, such as random mouse movements and taking breaks. It also integrates with
 * specific activity configurations like play style, activity intensity, and categories, which are adjusted
 * based on the activity being performed.
 * </p>
 *
 * <h3>Main Features:</h3>
 * <ul>
 *   <li>Human-Like Behavior Simulation: Simulates actions such as moving the mouse randomly, taking micro-breaks,
 *       and varying the intervals between actions to mimic natural gameplay.</li>
 *   <li>Activity-Based Configurations: Allows setting activity-specific antiban configurations through
 *       the {@code setActivity()} and {@code setActivityIntensity()} methods, ensuring that the antiban
 *       behavior is appropriate for the current task.</li>
 *   <li>Mouse Fatigue Simulation: Integrates with a mouse fatigue system to simulate the effects of fatigue
 *       on mouse movement over time.</li>
 *   <li>Overlay Rendering: Provides methods to render an overlay that displays current antiban status,
 *       including activity, play style, and action cooldown progress.</li>
 *   <li>Micro-Breaks and Cooldowns: Supports taking breaks based on a random chance or specific intervals
 *       to simulate a player taking short pauses during gameplay.</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <p>
 * The methods provided in this class are designed to be called during in-game activities to ensure the antiban
 * system is engaged and functioning according to the activity being performed. The configurations can be customized
 * through {@code Rs2AntibanSettings} to adjust behaviors such as action cooldowns, break chances, and mouse movements.
 * </p>
 *
 * <h3>Example:</h3>
 * <pre>
 * // Setting the antiban activity to woodcutting
 * Rs2Antiban.setActivity(Activity.GENERAL_WOODCUTTING);
 *
 * // Triggering an action cooldown based on current settings
 * Rs2Antiban.actionCooldown();
 *
 * // Rendering the antiban overlay in a panel
 * Rs2Antiban.renderAntibanOverlayComponents(panelComponent);
 * </pre>
 *
 * <h3>Available Methods:</h3>
 * <ul>
 *   <li><code>setActivity(Activity activity)</code>: Sets the current activity and adjusts antiban settings based on the activity type.</li>
 *   <li><code>setActivityIntensity(ActivityIntensity intensity)</code>: Sets the intensity level of the current activity.</li>
 *   <li><code>actionCooldown()</code>: Triggers an action cooldown, potentially adjusting play style and performing random mouse movements.</li>
 *   <li><code>takeMicroBreakByChance()</code>: Attempts to trigger a micro-break based on a random chance.</li>
 *   <li><code>isWoodcutting()</code>: Checks if the player is currently performing a woodcutting animation.</li>
 *   <li><code>isMining()</code>: Checks if the player is currently performing a mining animation.</li>
 *   <li><code>isIdle()</code>: Checks if the player is currently idle (not performing any animation).</li>
 *   <li><code>renderAntibanOverlayComponents(PanelComponent panelComponent)</code>: Renders an overlay showing the current antiban status and action cooldown progress.</li>
 *   <li><code>moveMouseOffScreen()</code>: Moves the mouse off-screen to simulate taking a break.</li>
 *   <li><code>moveMouseRandomly()</code>: Moves the mouse randomly to simulate natural behavior during gameplay.</li>
 *   <li><code>activateAntiban()</code>: Activates the antiban system.</li>
 *   <li><code>deactivateAntiban()</code>: Deactivates the antiban system.</li>
 *   <li><code>resetAntibanSettings()</code>: Resets all antiban settings to their default values.</li>
 * </ul>
 */

@Getter
@Setter
public class Rs2Antiban {
    public static final ImmutableSet<Integer> MINING_ANIMATION_IDS = ImmutableSet.of(
            MINING_BRONZE_PICKAXE, MINING_MOTHERLODE_BRONZE, MINING_CRASHEDSTAR_BRONZE,
            MINING_IRON_PICKAXE, MINING_MOTHERLODE_IRON, MINING_CRASHEDSTAR_IRON,
            MINING_STEEL_PICKAXE, MINING_MOTHERLODE_STEEL, MINING_CRASHEDSTAR_STEEL,
            MINING_BLACK_PICKAXE, MINING_MOTHERLODE_BLACK, MINING_CRASHEDSTAR_BLACK,
            MINING_MITHRIL_PICKAXE, MINING_MOTHERLODE_MITHRIL, MINING_CRASHEDSTAR_MITHRIL,
            MINING_ADAMANT_PICKAXE, MINING_MOTHERLODE_ADAMANT, MINING_CRASHEDSTAR_ADAMANT,
            MINING_RUNE_PICKAXE, MINING_MOTHERLODE_RUNE, MINING_CRASHEDSTAR_RUNE,
            MINING_GILDED_PICKAXE, MINING_MOTHERLODE_GILDED, MINING_CRASHEDSTAR_GILDED,
            MINING_DRAGON_PICKAXE, MINING_MOTHERLODE_DRAGON, MINING_CRASHEDSTAR_DRAGON,
            MINING_DRAGON_PICKAXE_OR, MINING_MOTHERLODE_DRAGON_OR, MINING_CRASHEDSTAR_DRAGON_OR,
            MINING_DRAGON_PICKAXE_OR_TRAILBLAZER, MINING_MOTHERLODE_DRAGON_OR_TRAILBLAZER, MINING_CRASHEDSTAR_DRAGON_OR_TRAILBLAZER,
            MINING_DRAGON_PICKAXE_UPGRADED, MINING_MOTHERLODE_DRAGON_UPGRADED, MINING_CRASHEDSTAR_DRAGON_UPGRADED,
            MINING_INFERNAL_PICKAXE, MINING_MOTHERLODE_INFERNAL, MINING_CRASHEDSTAR_INFERNAL,
            MINING_3A_PICKAXE, MINING_MOTHERLODE_3A, MINING_CRASHEDSTAR_3A,
            MINING_CRYSTAL_PICKAXE, MINING_MOTHERLODE_CRYSTAL, MINING_CRASHEDSTAR_CRYSTAL,
            MINING_TRAILBLAZER_PICKAXE, MINING_TRAILBLAZER_PICKAXE_2, MINING_TRAILBLAZER_PICKAXE_3, MINING_MOTHERLODE_TRAILBLAZER
    );
    private static final Set<Integer> WOODCUTTING_ANIMS = ImmutableSet.of(
            WOODCUTTING_BRONZE, WOODCUTTING_IRON, WOODCUTTING_STEEL, WOODCUTTING_BLACK, WOODCUTTING_MITHRIL,
            WOODCUTTING_ADAMANT, WOODCUTTING_RUNE, WOODCUTTING_GILDED, WOODCUTTING_DRAGON, WOODCUTTING_DRAGON_OR,
            WOODCUTTING_INFERNAL, WOODCUTTING_3A_AXE, WOODCUTTING_CRYSTAL, WOODCUTTING_TRAILBLAZER,
            WOODCUTTING_2H_BRONZE, WOODCUTTING_2H_IRON, WOODCUTTING_2H_STEEL, WOODCUTTING_2H_BLACK,
            WOODCUTTING_2H_MITHRIL, WOODCUTTING_2H_ADAMANT, WOODCUTTING_2H_RUNE, WOODCUTTING_2H_DRAGON,
            WOODCUTTING_2H_CRYSTAL, WOODCUTTING_2H_CRYSTAL_INACTIVE, WOODCUTTING_2H_3A
    );

    // Mouse fatigue class
    public static MouseFatigue mouseFatigue = new MouseFatigue();

    // Antiban setup class
    public static AntibanSetupTemplates antibanSetupTemplates = new AntibanSetupTemplates();

    @Getter
    @Setter
    public static int TIMEOUT = 0;
    @Getter
    private static Activity activity;
    @Getter
    private static ActivityIntensity activityIntensity;
    @Getter
    @Setter
    private static Category category;
    @Getter
    @Setter
    private static PlayStyle playStyle;


    public static void setActivity(@NotNull Activity activity) {
        Rs2Antiban.activity = activity;
        Rs2Antiban.category = activity.getCategory();
        Rs2Antiban.activityIntensity = activity.getActivityIntensity();

        if (Rs2AntibanSettings.simulateAttentionSpan) {
            Rs2Antiban.playStyle = PlayStyle.EXTREME_AGGRESSIVE;
            //Rs2Antiban.playStyle = activityIntensity.getPlayStyle();
        } else {
            if (Rs2Antiban.playStyle == null)
                Rs2Antiban.playStyle = activityIntensity.getPlayStyle();
        }

        if (Rs2AntibanSettings.randomIntervals) {
            Rs2Antiban.playStyle = PlayStyle.RANDOM;
        }
        playStyle.frequency = activityIntensity.getFrequency();
        playStyle.amplitude = activityIntensity.getAmplitude();
        Rs2Antiban.playStyle.resetPlayStyle();


    }

    public static void setActivityIntensity(ActivityIntensity activityIntensity) {
        Rs2AntibanSettings.dynamicIntensity = false;
        Rs2Antiban.activityIntensity = activityIntensity;
    }


    public static boolean checkForCookingEvent(ChatMessage event) {
        if (event.getType() != ChatMessageType.SPAM) {
            return false;
        }
        final String message = event.getMessage();
        return message.startsWith("You successfully cook")
                || message.startsWith("You successfully bake")
                || message.startsWith("You successfully fry")
                || message.startsWith("You manage to cook")
                || message.startsWith("You roast a")
                || message.startsWith("You spit-roast")
                || message.startsWith("You cook")
                || message.startsWith("Eventually the Jubbly")
                || message.startsWith("You half-cook")
                || message.startsWith("The undead meat is now cooked")
                || message.startsWith("The undead chicken is now cooked")
                || message.startsWith("You successfully scramble")
                || message.startsWith("You dry a piece of meat")
                || message.startsWith("You accidentally burn")
                || message.equals("You burn the mushroom in the fire.")
                || message.startsWith("Unfortunately the Jubbly")
                || message.startsWith("You accidentally spoil");
    }

    /**
     * Checks if the player is currently performing a woodcutting animation.
     *
     * @return true if the player is performing a woodcutting animation, false otherwise.
     */
    public static boolean isWoodcutting() {
        return WOODCUTTING_ANIMS.contains(Rs2Player.getAnimation());
    }

    /**
     * Checks if the player is currently performing a mining animation.
     *
     * @return true if the player is performing a mining animation, false otherwise.
     */
    public static boolean isMining() {
        return MINING_ANIMATION_IDS.contains(Rs2Player.getAnimation());
    }

    /**
     * Checks if the player is currently idle.
     *
     * @return true if the player is idle, false otherwise.
     */
    public static boolean isIdle() {
        return AntibanPlugin.isIdle();
    }

    /**
     * <h2>Handles the Execution of an Action Cooldown Based on Anti-Ban Behaviors</h2>
     * <p>
     * This method controls the flow for activating the cooldown either with certainty or based on a chance.
     * It includes logic to adjust behaviors such as non-linear intervals, behavioral variability, and random mouse movements
     * to simulate more human-like actions.
     * </p>
     * <p>
     * Execute this method at any point in your script where you want to trigger an action cooldown.
     * </p>
     * <p>
     * The cooldown can be triggered directly if <code>actionCooldownChance</code> is 1.00 (100%),
     * or by chance if <code>actionCooldownChance</code> is less than 1.00 (100%). Several features like universal antiban,
     * non-linear intervals, and play style evolution are configurable through <code>Rs2AntibanSettings</code>.
     * </p>
     *
     * <h3>Primary Actions Handled:</h3>
     * <ul>
     *   <li>Pausing all scripts if the universal antiban is enabled.</li>
     *   <li>Evolving play style if non-linear intervals are enabled.</li>
     *   <li>Setting a timeout based on behavioral variability settings.</li>
     *   <li>Optionally moving the mouse randomly or off-screen based on respective settings.</li>
     * </ul>
     *
     * <h3>Preconditions:</h3>
     * <ul>
     *   <li>If <code>Rs2AntibanSettings.usePlayStyle</code> is disabled, the cooldown will not be performed.</li>
     * </ul>
     *
     * <h3>Main Flow:</h3>
     * <ul>
     *   <li>If <code>actionCooldownChance</code> &lt; 1.00 (100%), the cooldown is triggered based on the result of a random dice roll.</li>
     *   <li>If <code>actionCooldownChance</code> is 1.00 (100%) or greater, the cooldown is triggered unconditionally.</li>
     * </ul>
     *
     * <h3>Helper Methods:</h3>
     * <p>
     * <code>performActionCooldown()</code> encapsulates the shared logic for performing the cooldown,
     * adjusting the play style, and invoking other anti-ban actions like moving the mouse randomly or off-screen.
     * </p>
     */

    public static void actionCooldown() {
        if (!Rs2AntibanSettings.usePlayStyle) {
            logDebug("PlayStyle not enabled, cannot perform action cooldown");
            return;
        }
        if (Rs2AntibanSettings.actionCooldownChance == 1.0) {
            performActionCooldown();
            return;
        }
        if (Rs2AntibanSettings.actionCooldownChance <= 0.0) {
            logDebug("Action cooldown chance is 0%, cannot perform action cooldown");
            return;
        }
        if (Rs2AntibanSettings.actionCooldownChance <= 0.99 && Rs2Random.diceFractional(Rs2AntibanSettings.actionCooldownChance)) {
            performActionCooldown();

        }

    }

    private static void logDebug(String message) {
        if (Rs2AntibanSettings.devDebug) {
            Microbot.log("<col=f44336>" + message + "</col>");
        }
    }

    private static void performActionCooldown() {
        if (Rs2AntibanSettings.universalAntiban)
            Microbot.pauseAllScripts = true;

        if (Rs2AntibanSettings.nonLinearIntervals)
            playStyle.evolvePlayStyle();

        if (Rs2AntibanSettings.behavioralVariability)
            TIMEOUT = playStyle.getRandomTickInterval();
        else
            TIMEOUT = playStyle.getPrimaryTickInterval();

        Rs2AntibanSettings.actionCooldownActive = true;

        if (Rs2AntibanSettings.moveMouseRandomly && Rs2Random.diceFractional(Rs2AntibanSettings.moveMouseRandomlyChance)) {
            Rs2Random.wait(100, 200);
            moveMouseRandomly();
        }

        if (Rs2AntibanSettings.moveMouseOffScreen)
            moveMouseOffScreen();
    }


    /**
     * Attempts to trigger a micro-break based on a random chance, as configured in Rs2AntibanSettings.
     *
     * <p>
     * This method simulates human-like pauses in the bot's behavior by invoking a micro-break if a randomly generated
     * value is less than the configured <code>microBreakChance</code>. When triggered, the break duration is determined
     * randomly within a specified range and the mouse may be optionally moved off-screen.
     * </p>
     *
     * <h3>Behavior:</h3>
     * <ul>
     *   <li>If a random value is less than <code>Rs2AntibanSettings.microBreakChance</code>, the micro-break is activated.</li>
     *   <li>The break duration is randomly set between <code>Rs2AntibanSettings.microBreakDurationLow</code> and
     *   <code>Rs2AntibanSettings.microBreakDurationHigh</code>, in seconds.</li>
     *   <li>If <code>Rs2AntibanSettings.moveMouseOffScreen</code> is enabled, the mouse is moved off-screen during the break.</li>
     * </ul>
     *
     * <h3>Preconditions:</h3>
     * <ul>
     *   <li>The configuration in <code>Rs2AntibanSettings</code> must define valid break chance and duration values.</li>
     * </ul>
     *
     * <h3>Postconditions:</h3>
     * <ul>
     *   <li><code>Rs2AntibanSettings.microBreakActive</code> is set to <code>true</code> if the break is triggered.</li>
     *   <li><code>BreakHandlerScript.breakDuration</code> is set to a randomly determined value in seconds.</li>
     * </ul>
     *
     * @return true if a micro-break is triggered, false otherwise.
     */

    public static boolean takeMicroBreakByChance() {
        if (!Rs2AntibanSettings.takeMicroBreaks && Rs2AntibanSettings.microBreakChance > 0.0) {
            logDebug("MICRO BREAKS ARE DISABLED, cannot take micro break");
            return false;
        }
        if (Math.random() < Rs2AntibanSettings.microBreakChance) {
            Rs2AntibanSettings.microBreakActive = true;
            BreakHandlerScript.breakDuration = Random.random(Rs2AntibanSettings.microBreakDurationLow * 60, Rs2AntibanSettings.microBreakDurationHigh * 60);
            if (Rs2AntibanSettings.moveMouseOffScreen)
                moveMouseOffScreen();
            return true;

        }
        return false;
    }


    /**
     * Renders an overlay component that displays various anti-ban settings and information within a panel.
     *
     * <p>
     * This method populates a <code>PanelComponent</code> with details regarding the current anti-ban system's state,
     * activity levels, play styles, and other related information. It is intended for use in providing a visual representation
     * of the anti-ban system's status during runtime, with debug information shown when enabled.
     * </p>
     *
     * <h3>Overlay Components:</h3>
     * <ul>
     *   <li>A title component labeled "🦆 Humanizer 🦆" with orange coloring.</li>
     *   <li>Details about the current activity, including method name, category, and intensity.</li>
     *   <li>If <code>Rs2AntibanSettings.devDebug</code> is enabled, several debug lines will show key anti-ban settings,
     *       such as action cooldown, random intervals, and behavioral variability.</li>
     *   <li>If a play style is active, the panel displays the current play style name and the time remaining until the next switch
     *       if attention span simulation is enabled.</li>
     *   <li>A progress bar representing the current action cooldown based on a tick interval, providing a visual cue for
     *       the remaining time.</li>
     *   <li>Status updates on whether the bot is busy or idle, indicating potential upcoming breaks.</li>
     * </ul>
     *
     * <h3>Behavior:</h3>
     * <ul>
     *   <li>The method dynamically updates the panel with current information based on settings in <code>Rs2AntibanSettings</code>
     *       and <code>playStyle</code>.</li>
     *   <li>If debug mode is enabled, additional lines provide detailed state information, such as whether action cooldown,
     *       fatigue simulation, and natural mouse movements are active.</li>
     *   <li>The progress bar visually indicates the current state of the action cooldown timer.</li>
     * </ul>
     *
     * <h3>Preconditions:</h3>
     * <ul>
     *   <li><code>playStyle</code> and <code>Rs2AntibanSettings</code> must be properly initialized.</li>
     *   <li>The <code>panelComponent</code> must be passed as a valid and non-null component to receive overlay data.</li>
     * </ul>
     *
     * <h3>Where to Use:</h3>
     * <p>
     * This method should be used within overlay rendering methods, typically in custom overlay classes that extend
     * <code>OverlayPanel</code>. For example, in the <code>MotherloadMineOverlay</code> class, this method is used to
     * display anti-ban information in the mining overlay. It is invoked within the <code>render(Graphics2D graphics)</code>
     * method to ensure that the anti-ban status is updated every time the overlay is drawn.
     * </p>
     *
     * <p>
     * To integrate this method into a custom overlay:
     * </p>
     * <ol>
     *   <li>Ensure that your overlay class extends <code>OverlayPanel</code> or a similar class that supports adding components.</li>
     *   <li>Invoke <code>Rs2Antiban.renderAntibanOverlayComponents(panelComponent);</code> within the overlay's
     *       <code>render</code> method, before or after other components are added, depending on the desired layout.</li>
     *   <li>Ensure that the appropriate <code>Rs2AntibanSettings</code> are configured before invoking the method.</li>
     * </ol>
     *
     * <h3>Example Usage:</h3>
     * <pre>
     * {@code
     * @Override
     * public Dimension render(Graphics2D graphics) {
     *     try {
     *         panelComponent.setPreferredSize(new Dimension(275, 900));
     *         panelComponent.getChildren().add(TitleComponent.builder()
     *                 .text("\uD83E\uDD86 Motherlode Mine \uD83E\uDD86")
     *                 .color(Color.ORANGE)
     *                 .build());
     *
     *         Rs2Antiban.renderAntibanOverlayComponents(panelComponent);
     *         addEmptyLine();
     *
     *         panelComponent.getChildren().add(LineComponent.builder()
     *                 .left("Mining Location: " + MotherloadMineScript.miningSpot.name())
     *                 .build());
     *
     *         addEmptyLine();
     *
     *         panelComponent.getChildren().add(LineComponent.builder()
     *                 .left(status.toString())
     *                 .right("Version: " + MotherloadMineScript.version)
     *                 .build());
     *     } catch (Exception ex) {
     *         System.out.println(ex.getMessage());
     *     }
     *     return super.render(graphics);
     * }
     * }
     * </pre>
     */

    public static void renderAntibanOverlayComponents(PanelComponent panelComponent) {
        final ProgressBarComponent progressBarComponent = new ProgressBarComponent();
        progressBarComponent.setBackgroundColor(Color.DARK_GRAY);
        progressBarComponent.setForegroundColor(ColorUtil.fromHex("#cc8400"));
        progressBarComponent.setMaximum(0);
        progressBarComponent.setMaximum(playStyle.getSecondaryTickInterval());
        progressBarComponent.setValue(TIMEOUT);
        progressBarComponent.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
        progressBarComponent.setLeftLabel("0");
        progressBarComponent.setRightLabel(String.valueOf(playStyle.getSecondaryTickInterval()));
        progressBarComponent.setCenterLabel(String.valueOf(TIMEOUT));

        panelComponent.getChildren().add(TitleComponent.builder().text("\uD83E\uDD86 Humanizer \uD83E\uDD86")
                .color(Color.ORANGE).build());
        panelComponent.getChildren().add(LineComponent.builder().build());
        panelComponent.getChildren().add(SplitComponent.builder()
                .first(LineComponent.builder().left("Activity: " + activity.getMethod()).right(Rs2AntibanSettings.devDebug ? "Dynamic: " + (Rs2AntibanSettings.dynamicActivity ? "✔" : "❌") : "").build())
                .second(LineComponent.builder().left("Category: " + category.getName()).build()).build());
        panelComponent.getChildren().add(LineComponent.builder().build());
        panelComponent.getChildren().add(LineComponent.builder().left("Activity Intensity: " + activityIntensity.getName()).right(Rs2AntibanSettings.devDebug ? "Dynamic: " + (Rs2AntibanSettings.dynamicIntensity ? "✔" : "❌") : "").build());
        if (Rs2AntibanSettings.devDebug) {
            panelComponent.getChildren().add(LineComponent.builder().left("isActionCooldownActive: " + (Rs2AntibanSettings.actionCooldownActive ? "✔" : "❌")).build());
            panelComponent.getChildren().add(LineComponent.builder().left("isEnabled: " + (Rs2AntibanSettings.antibanEnabled ? "✔" : "❌")).build());
            panelComponent.getChildren().add(LineComponent.builder().left("useRandomIntervals: " + (Rs2AntibanSettings.randomIntervals ? "✔" : "❌")).build());
            panelComponent.getChildren().add(LineComponent.builder().left("simulateFatigue: " + (Rs2AntibanSettings.simulateFatigue ? "✔" : "❌")).build());
            panelComponent.getChildren().add(LineComponent.builder().left("simulateAttentionSpan: " + (Rs2AntibanSettings.simulateAttentionSpan ? "✔" : "❌")).build());
            panelComponent.getChildren().add(LineComponent.builder().left("useBehavioralVariability: " + (Rs2AntibanSettings.behavioralVariability ? "✔" : "❌")).build());
            panelComponent.getChildren().add(LineComponent.builder().left("useNonLinearIntervals: " + (Rs2AntibanSettings.nonLinearIntervals ? "✔" : "❌")).build());
            panelComponent.getChildren().add(LineComponent.builder().left("enableProfileSwitching: " + (Rs2AntibanSettings.profileSwitching ? "✔" : "❌")).build());
            panelComponent.getChildren().add(LineComponent.builder().left("adjustForTimeOfDay: " + (Rs2AntibanSettings.timeOfDayAdjust ? "✔" : "❌")).build());
            panelComponent.getChildren().add(LineComponent.builder().left("simulateMistakes: " + (Rs2AntibanSettings.simulateMistakes ? "✔" : "❌")).build());
            panelComponent.getChildren().add(LineComponent.builder().left("useNaturalMouse: " + (Rs2AntibanSettings.naturalMouse ? "✔" : "❌")).build());
            panelComponent.getChildren().add(LineComponent.builder().left("useContextualVariability: " + (Rs2AntibanSettings.contextualVariability ? "✔" : "❌")).build());
        }
        if (playStyle != null) {
            panelComponent.getChildren().add(LineComponent.builder().left("Play Style: " + playStyle.getName()).build());
            if (Rs2AntibanSettings.simulateAttentionSpan) {
                panelComponent.getChildren().add(LineComponent.builder().left("Play style change in: " + playStyle.getTimeLeftUntilNextSwitch()).build());
            }
            panelComponent.getChildren().add(LineComponent.builder().build());
            if (Rs2Antiban.getCategory().isBusy()) {
                panelComponent.getChildren().add(LineComponent.builder().left("We are busy").build());
            } else {
                panelComponent.getChildren().add(LineComponent.builder().left("Not busy anymore, breaking").build());
            }
            panelComponent.getChildren().add(LineComponent.builder().build());
            panelComponent.getChildren().add(TitleComponent.builder().text("Action cooldown(Tick)").color(Color.WHITE).build());
            panelComponent.getChildren().add(progressBarComponent);
        }

    }

    /**
     * <h1>Move Mouse Off Screen</h1>
     * This method moves the mouse off the screen with a 1/4 chance to trigger.
     * This is used to simulate a user moving the mouse off the screen to take a break.
     */
    public static void moveMouseOffScreen() {
        Microbot.naturalMouse.moveOffScreen();
    }

    /**
     * <h1>Move Mouse Randomly</h1>
     * This method moves the mouse randomly based on the given chance in settings.
     * This is used to simulate a user moving the mouse randomly to take a break.
     */
    public static void moveMouseRandomly() {
        Microbot.naturalMouse.moveRandom();
    }

    public static void activateAntiban() {
        Rs2AntibanSettings.antibanEnabled = true;
    }

    public static void deactivateAntiban() {
        Rs2AntibanSettings.antibanEnabled = false;
    }

    public static boolean isIdleTooLong(int timeoutTicks) {
        return AntibanPlugin.isIdleTooLong(timeoutTicks);
    }

    // reset all the variables
    public static void resetAntibanSettings() {
        Rs2AntibanSettings.reset();
        Rs2Antiban.playStyle = null;
        Rs2Antiban.activity = null;
        Rs2Antiban.activityIntensity = null;
        Rs2Antiban.category = null;
    }

}
