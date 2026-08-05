package net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.PluginStopHelper;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;

/**
 * Overlay for Break Handler V2
 * Displays break status, timers, and profile information
 */
@Slf4j
public class BreakHandlerV2Overlay extends OverlayPanel {

    private final BreakHandlerV2Config config;
    private final BreakHandlerV2Script script;

    @Inject
    public BreakHandlerV2Overlay(BreakHandlerV2Config config, BreakHandlerV2Script script) {
        super();
        this.config = config;
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(300, 300));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            // Check if overlay should be hidden
            if (config.hideOverlay()) {
                return null;
            }


            panelComponent.getChildren().clear();

            // Title
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Break Handler V2")
                .color(Color.CYAN)
                .build());

            // Current state
            BreakHandlerV2State currentState = BreakHandlerV2State.getCurrentState();
            Color stateColor = getStateColor(currentState);

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(currentState.getDescription())
                .rightColor(stateColor)
                .build());

            // Version
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Version:")
                .right(BreakHandlerV2Script.version)
                .rightColor(Color.GRAY)
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Runtime:")
                .right(formatDuration(script.getScriptActiveSeconds()))
                .rightColor(Color.WHITE)
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Breaks:")
                .right(String.valueOf(script.getBreaksActivatedCount()))
                .rightColor(Color.WHITE)
                .build());

            // Show play schedule info if enabled
            if (config.usePlaySchedule()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Schedule:")
                    .right(config.playSchedule().name())
                    .rightColor(Color.CYAN)
                    .build());
            }

            // Time until break or break remaining
            if (currentState == BreakHandlerV2State.WAITING_FOR_BREAK) {
                long secondsUntilBreak = script.getTimeUntilBreak();
                if (secondsUntilBreak >= 0) {
                    String timeStr = formatDuration(secondsUntilBreak);
                    String label = config.usePlaySchedule() ? "Schedule ends:" : "Next break:";
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left(label)
                        .right(timeStr)
                        .rightColor(Color.GREEN)
                        .build());
                }
                long secondsUntilLongBreak = script.getTimeUntilLongBreak();
                if (config.enableLongBreaks() && secondsUntilLongBreak >= 0) {
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left("Long break:")
                        .right(formatDuration(secondsUntilLongBreak))
                        .rightColor(Color.ORANGE)
                        .build());
                }
            } else if (BreakHandlerV2State.isBreakActive()) {
                long secondsRemaining = script.getBreakTimeRemaining();
                if (secondsRemaining >= 0) {
                    String timeStr = formatDuration(secondsRemaining);
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left(script.isCurrentBreakLong() ? "Long break ends:" : "Break ends:")
                        .right(timeStr)
                        .rightColor(Color.ORANGE)
                        .build());
                }
            }

            // Show detailed info if enabled
            if (config.showDetailedInfo()) {
                // Profile information
                ConfigProfile profile = LoginManager.getActiveProfile();
                if (profile != null) {
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left("Profile:")
                        .right(profile.getName())
                        .rightColor(Color.WHITE)
                        .build());

                    panelComponent.getChildren().add(LineComponent.builder()
                        .left("Member:")
                        .right(profile.isMember() ? "Yes" : "No")
                        .rightColor(profile.isMember() ? Color.YELLOW : Color.GRAY)
                        .build());
                }

                // World selection mode
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Stay on current:")
                    .right(config.ignoreWorldSwitching() ? "Yes" : "No")
                    .rightColor(config.ignoreWorldSwitching() ? Color.GREEN : Color.LIGHT_GRAY)
                    .build());

                String stopConfigValue = config.pluginToStop();
                String stopDisplay = PluginStopHelper.resolveDisplayName(stopConfigValue, Microbot.getPluginManager());
                boolean hasStopTarget = !PluginStopHelper.isNone(PluginStopHelper.normalizeStoredValue(stopConfigValue, Microbot.getPluginManager()));

                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Stop plugin:")
                    .right(stopDisplay)
                    .rightColor(hasStopTarget ? Color.ORANGE : Color.LIGHT_GRAY)
                    .build());

                panelComponent.getChildren().add(LineComponent.builder()
                    .left("World mode:")
                    .right(config.worldSelectionMode().name())
                    .rightColor(Color.LIGHT_GRAY)
                    .build());

                // Region preference
                if (config.regionPreference() != null) {
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left("Region:")
                        .right(config.regionPreference().name())
                        .rightColor(Color.LIGHT_GRAY)
                        .build());
                }

                // Current world if logged in
                if (Microbot.isLoggedIn()) {
                    int currentWorld = Microbot.getClient().getWorld();
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left("Current world:")
                        .right(String.valueOf(currentWorld))
                        .rightColor(Color.GREEN)
                        .build());
                }

                // Break configuration
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Break type:")
                    .right((config.logoutOnBreak() ? "Logout" : "Stay logged in") + (config.enableLongBreaks() ? " + long" : ""))
                    .rightColor(Color.LIGHT_GRAY)
                    .build());

                // Auto-login status
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Auto-login:")
                    .right(config.autoLogin() ? "Enabled" : "Disabled")
                    .rightColor(config.autoLogin() ? Color.GREEN : Color.RED)
                    .build());

                // Discord notifications
                if (config.enableDiscordWebhook()) {
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left("Discord:")
                        .right("Enabled")
                        .rightColor(Color.CYAN)
                        .build());
                }
            }

        } catch (Exception ex) {
            log.error("[BreakHandlerV2Overlay] Error rendering overlay", ex);
        }

        return super.render(graphics);
    }

    /**
     * Get color for current state
     */
    private Color getStateColor(BreakHandlerV2State state) {
        switch (state) {
            case WAITING_FOR_BREAK:
                return Color.GREEN;
            case BREAK_REQUESTED:
            case INITIATING_BREAK:
                return Color.YELLOW;
            case LOGOUT_REQUESTED:
            case LOGGED_OUT:
                return Color.ORANGE;
            case LOGIN_REQUESTED:
            case LOGGING_IN:
                return Color.CYAN;
            case LOGIN_EXTENDED_SLEEP:
                return Color.RED;
            case BREAK_ENDING:
                return Color.LIGHT_GRAY;
            case PROFILE_SWITCHING:
                return Color.MAGENTA;
            default:
                return Color.WHITE;
        }
    }

    /**
     * Format duration in seconds to human-readable string
     */
    private String formatDuration(long seconds) {
        Duration duration = Duration.ofSeconds(seconds);

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long secs = duration.toSecondsPart();

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
}
