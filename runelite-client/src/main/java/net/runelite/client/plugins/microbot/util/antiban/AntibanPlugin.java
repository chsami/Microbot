package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.api.AnimationID;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.CombatSkills;
import net.runelite.client.plugins.microbot.util.antiban.ui.MasterPanel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The AntibanPlugin is responsible for managing anti-ban behaviors during bot operation.
 *
 * <p>
 * This plugin ensures that the bot behaves in a more human-like manner to avoid detection by using various
 * anti-ban strategies. These strategies include simulating breaks, adjusting activity levels, and mimicking
 * attention span variations. The plugin tracks user activity and game state to dynamically adjust bot behavior.
 * </p>
 *
 * <h3>Main Features:</h3>
 * <ul>
 *   <li>Simulates action cooldowns and micro-breaks based on the bot's current activities.</li>
 *   <li>Dynamically adjusts activity intensity and behavior depending on the bot's in-game actions, such as mining or cooking.</li>
 *   <li>Tracks user skill changes and updates the anti-ban settings accordingly.</li>
 *   <li>Supports attention span simulation, profile switching, and periodic breaks to ensure realistic play styles.</li>
 *   <li>Automatically enables the BreakHandlerPlugin when needed for managing breaks.</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <p>
 * The <code>AntibanPlugin</code> works silently in the background to adjust the bot's behavior during runtime.
 * Users do not need to manually interact with this plugin, as it is automatically integrated into the bot framework.
 * </p>
 *
 * <p>
 * The plugin monitors in-game actions, such as cooking, mining, and skill changes, to adjust its anti-ban strategy
 * accordingly. It also manages the simulation of breaks, cooldowns, and play style variations to mimic human behavior
 * and avoid detection.
 * </p>
 *
 * <h3>Additional Details:</h3>
 * <ul>
 *   <li>Automatic tracking of idle time to determine if the bot should take a break.</li>
 *   <li>Real-time updates to anti-ban settings based on player activities and game state changes.</li>
 *   <li>Hidden from the user interface to avoid unnecessary distractions, while always being active in the background.</li>
 * </ul>
 */

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + "Antiban",
        description = "Antiban for microbot",
        tags = {"main", "microbot", "antiban parent"},
        alwaysOn = true,
        hidden = true
)


public class AntibanPlugin extends Plugin {

    private static final int COOK_TIMEOUT = 3;
    private static final int MINING_TIMEOUT = 3;
    private static final int IDLE_TIMEOUT = 1;
    public static int ticksSinceLogin;
    private static Instant lastCookingAction = Instant.MIN;
    private static Instant lastMiningAction = Instant.MIN;
    private static int idleTicks = 0;
    private final Map<Skill, Integer> skillExp = new EnumMap<>(Skill.class);
    private boolean ready;
    private Skill lastSkillChanged;
    private NavigationButton navButton;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ClientToolbar clientToolbar;

    public static boolean isCooking() {
        return Rs2Player.getAnimation() == AnimationID.COOKING_FIRE
                || Rs2Player.getAnimation() == AnimationID.COOKING_RANGE
                || Duration.between(lastCookingAction, Instant.now()).getSeconds() < COOK_TIMEOUT;
    }

    public static boolean isMining() {
        return Rs2Antiban.isMining()
                || Duration.between(lastMiningAction, Instant.now()).getSeconds() < MINING_TIMEOUT;
    }

    public static boolean isIdle() {
        return idleTicks > IDLE_TIMEOUT;
    }

    private static void updateIdleTicks() {
        idleTicks++;
    }

    private static void updateLastCookingAction() {
        lastCookingAction = Instant.now();
    }

    private static void updateLastMiningAction() {
        lastMiningAction = Instant.now();
    }

    public static void performActionBreak() {
        if (Rs2AntibanSettings.actionCooldownActive) {
            if (Rs2Antiban.getTIMEOUT() > 0) {
                if (!Rs2Antiban.getCategory().isBusy()) {
                    Rs2Antiban.TIMEOUT--;
                }
            } else {
                Rs2AntibanSettings.actionCooldownActive = false;
                if (Rs2AntibanSettings.universalAntiban && !Rs2AntibanSettings.microBreakActive)
                    Microbot.pauseAllScripts = false;
            }
        }
    }

    @Override
    protected void startUp() throws AWTException {
        final MasterPanel panel = injector.getInstance(MasterPanel.class);
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "antiban.png");
        navButton = NavigationButton.builder()
                .tooltip("Antiban")
                .icon(icon)
                .priority(1)
                .panel(panel)
                .build();

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(panel::loadSettings);
            }
        }, 0, 600);

        clientToolbar.addNavigation(navButton);
        overlayManager.add(new AntibanOverlay());
    }

    @Override
    protected void shutDown() {
        overlayManager.removeIf(overlay -> overlay instanceof AntibanOverlay);
        clientToolbar.removeNavigation(navButton);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (Rs2Antiban.checkForCookingEvent(event)) {
            updateLastCookingAction();
        }
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged event) {
        Rs2Antiban.resetAntibanSettings();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        GameState state = event.getGameState();

        switch (state) {
            case LOGGING_IN:
            case HOPPING:
                ready = true;
                break;
            case LOGGED_IN:
                if (ready) {
                    ticksSinceLogin = 0;
                    ready = false;
                }
                break;
        }
    }

    // method to check if we have been idle for too long, indicating some issue with the script, use this to reset or reinitialize your script
    public static boolean isIdleTooLong(int timeout) {
        return idleTicks > timeout && !Rs2AntibanSettings.actionCooldownActive && !Rs2AntibanSettings.takeMicroBreaks;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        ticksSinceLogin++;

        if (!Rs2AntibanSettings.antibanEnabled) {
            return;
        }

        if (!Rs2Player.isAnimating()) {
            updateIdleTicks();
        } else {
            if (Rs2AntibanSettings.simulateFatigue) {
                ticksSinceLogin -= idleTicks;
            }
            idleTicks = 0;
        }

        if (Rs2AntibanSettings.takeMicroBreaks && !Microbot.isPluginEnabled(BreakHandlerPlugin.class)) {
            if (Rs2AntibanSettings.devDebug)
                Microbot.showMessage("Micro breaks depend on the BreakHandlerPlugin, enabling it now.");

            Microbot.log("BreakHandlerPlugin not enabled, enabling it now.");
            String name = BreakHandlerPlugin.class.getName();
            Plugin breakHandlerPlugin = Microbot.getPluginManager().getPlugins().stream()
                    .filter(x -> x.getClass().getName().equals(name))
                    .findFirst()
                    .orElse(null);
            Microbot.startPlugin(breakHandlerPlugin);
        }

        if (Rs2Antiban.isMining()) {
            updateLastMiningAction();
        }

        if (Rs2AntibanSettings.actionCooldownActive) {
            performActionBreak();
        }

        if (Rs2AntibanSettings.usePlayStyle) {
            if (Rs2Antiban.getPlayStyle() == null)
                return;
            if (Rs2AntibanSettings.simulateAttentionSpan && Rs2AntibanSettings.profileSwitching &&
                    Rs2Antiban.getPlayStyle().shouldSwitchProfileBasedOnAttention()) {
                Rs2Antiban.setPlayStyle(Rs2Antiban.getPlayStyle().switchProfile());
                Rs2Antiban.getPlayStyle().resetPlayStyle();
            }
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        if (!Rs2AntibanSettings.antibanEnabled) {
            return;
        }

        final Skill skill = statChanged.getSkill();
        final int exp = statChanged.getXp();
        final Integer previous = skillExp.put(skill, exp);

        if (lastSkillChanged != null && (lastSkillChanged.equals(skill) || (CombatSkills.isCombatSkill(lastSkillChanged) && CombatSkills.isCombatSkill(skill)))) {
            if (Rs2AntibanSettings.universalAntiban && !Rs2AntibanSettings.actionCooldownActive && Rs2Antiban.getActivity() != null) {
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
            if (Rs2Antiban.getActivity() == null)
                updateAntibanSettings(skill);

            return;
        }

        lastSkillChanged = skill;

        if (previous == null || previous >= exp) {
            return;
        }

        updateAntibanSettings(skill);
    }

    private void updateAntibanSettings(Skill skill) {
        final ActivityIntensity activityIntensity = ActivityIntensity.fromSkill(skill);
        final Activity activity = Activity.fromSkill(skill);

        if (activity != null && Rs2AntibanSettings.dynamicActivity) {
            Rs2Antiban.setActivity(activity);
            Microbot.log("Activity changed, new activity: " + activity);
            if (Rs2AntibanSettings.universalAntiban) {
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        }

        if (activityIntensity != null && Rs2AntibanSettings.dynamicIntensity) {
            Rs2Antiban.setActivityIntensity(activityIntensity);
            Microbot.log("Activity changed, new activity intensity: " + activityIntensity);
        }
    }

}

