package net.runelite.client.plugins.microbot.pluginscheduler;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.util.Optional;

public class SchedulerInfoOverlay extends OverlayPanel {
    private final SchedulerPlugin plugin;

    @Inject
    SchedulerInfoOverlay(SchedulerPlugin plugin, SchedulerConfig config) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            if (shouldHideOverlay()) {
                return null;
            }
            panelComponent.setPreferredSize(new Dimension(200, 120));
            
            // Title with icon
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("📅 Plugin Scheduler")
                    .color(Color.CYAN)                    
                    .build());

            // Current state
            SchedulerState currentState = plugin.getCurrentState();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(getStateWithIcon(currentState))
                    .rightColor(currentState.getColor())                    
                    .build());
                    // Hide overlay when important interfaces are open
          


            // Current plugin info
            PluginScheduleEntry currentPlugin = plugin.getCurrentPlugin();
            if (currentPlugin != null) {
                addCurrentPluginInfo(currentPlugin, currentState);
            } else {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Current:")
                        .right("None")
                        .rightColor(Color.GRAY)
                        .build());
            }

            // Next plugin info
            PluginScheduleEntry nextPlugin = plugin.getUpComingPlugin();
            if (nextPlugin != null) {
                addNextPluginInfo(nextPlugin);
            } else {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Next:")
                        .right("None scheduled")
                        .rightColor(Color.GRAY)
                        .build());
            }

            // Break information (conditionally shown)
            addBreakInformation(currentState);

            // Version
            panelComponent.getChildren().add(LineComponent.builder().build()); // spacer
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Version:")
                    .right(SchedulerPlugin.VERSION)
                    .rightColor(Color.GRAY)
                    .build());

        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    /**
     * Adds current plugin information including progress and time estimates
     */
    private void addCurrentPluginInfo(PluginScheduleEntry currentPlugin, SchedulerState currentState) {
        String pluginName = currentPlugin.getName();
        if (pluginName.length() > 20) {
            pluginName = pluginName.substring(0, 17) + "...";
        }
        
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Current:")
                .right(pluginName)
                .rightColor(currentState == SchedulerState.RUNNING_PLUGIN ? Color.GREEN : Color.YELLOW)
                .build());

        // Add runtime if running
        if (currentPlugin.isRunning()) {
            // Calculate current runtime by using lastRunStartTime
            Duration runtime = currentPlugin.getLastRunStartTime() != null 
                ? Duration.between(currentPlugin.getLastRunStartTime(), java.time.ZonedDateTime.now())
                : Duration.ZERO;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(formatDuration(runtime))
                    .rightColor(Color.WHITE)
                    .build());

            // Add stop time estimate if available
            Optional<Duration> stopEstimate = currentPlugin.getEstimatedStopTimeWhenIsSatisfied();
            if (stopEstimate.isPresent()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Est. Stop:")
                        .right(formatDuration(stopEstimate.get()))
                        .rightColor(Color.ORANGE)
                        .build());
            }

            // Add progress percentage if conditions are trackable
            double progress = currentPlugin.getStopConditionProgress();
            if (progress > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Progress:")
                        .right(String.format("%.1f%%", progress))
                        .rightColor(getProgressColor(progress))
                        .build());
            }
        }
    }

    /**
     * Determines if the overlay should be hidden to avoid interfering with important game interfaces
     */
    private boolean shouldHideOverlay() {
        try {
            return Rs2Bank.isOpen() || Rs2Shop.isOpen() || Rs2GrandExchange.isOpen()|| Rs2Widget.isDepositBoxWidgetOpen() || !Rs2Widget.isHidden(InterfaceID.BankpinKeypad.UNIVERSE);
                   
        } catch (Exception e) {
            // Fallback - don't hide if there's an issue checking
            return false;
        }
    }
    /**
     * Adds next plugin information including time estimates
     */
    private void addNextPluginInfo(PluginScheduleEntry nextPlugin) {
        String pluginName = nextPlugin.getName();
        if (pluginName.length() > 20) {
            pluginName = pluginName.substring(0, 17) + "...";
        }
        
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Next:")
                .right(pluginName)
                .rightColor(Color.CYAN)
                .build());

        // Add next run time estimate
        Optional<Duration> nextRunEstimate = plugin.getUpComingEstimatedScheduleTime();
        if (nextRunEstimate.isPresent()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Est. Start:")
                    .right(formatDuration(nextRunEstimate.get()))
                    .rightColor(Color.LIGHT_GRAY)
                    .build());
        } else {
            // Fallback to plugin's own estimate
            Optional<Duration> pluginEstimate = nextPlugin.getEstimatedStartTimeWhenIsSatisfied();
            if (pluginEstimate.isPresent()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Est. Start:")
                        .right(formatDuration(pluginEstimate.get()))
                        .rightColor(Color.LIGHT_GRAY)
                        .build());
            }
        }
    }

    /**
     * Adds break information, but only shows break countdown if not in micro-breaks-only mode
     */
    private void addBreakInformation(SchedulerState currentState) {
        // Check if we should show break information
        boolean onlyMicroBreaks = getOnlyMicroBreaksConfig();
        boolean showBreakIn = !onlyMicroBreaks || !Rs2AntibanSettings.takeMicroBreaks;

        if (currentState.isBreaking()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Break Status:")
                    .right("On Break")
                    .rightColor(Color.YELLOW)
                    .build());
            
            if (BreakHandlerScript.breakDuration > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Break Time:")
                        .right(formatDuration(Duration.ofSeconds(BreakHandlerScript.breakDuration)))
                        .rightColor(Color.WHITE)
                        .build());
            }
        } else {
            // Only show break countdown if not in micro-breaks-only mode
            if (showBreakIn && BreakHandlerScript.breakIn > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Break In:")
                        .right(formatDuration(Duration.ofSeconds(BreakHandlerScript.breakIn)))
                        .rightColor(Color.WHITE)
                        .build());
            } else if (onlyMicroBreaks && Rs2AntibanSettings.takeMicroBreaks) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Break Mode:")
                        .right("Micro Breaks Only")
                        .rightColor(Color.BLUE)
                        .build());
            }
        }
    }

    /**
     * Gets the onlyMicroBreaks configuration value from the BreakHandler config
     */
    private boolean getOnlyMicroBreaksConfig() {
        try {
            Boolean onlyMicroBreaks = Microbot.getConfigManager().getConfiguration(
                    "break-handler", "OnlyMicroBreaks", Boolean.class);
            return onlyMicroBreaks != null && onlyMicroBreaks;
        } catch (Exception e) {
            // Fallback to false if config cannot be retrieved
            return false;
        }
    }

    /**
     * Adds appropriate icon to state display based on current state
     */
    private String getStateWithIcon(SchedulerState state) {
        String icon;
        switch (state) {
            case READY:
                icon = "✅";
                break;
            case SCHEDULING:
                icon = "⚙️";
                break;
            case STARTING_PLUGIN:
                icon = "🚀";
                break;
            case RUNNING_PLUGIN:
                icon = "▶️";
                break;
            case RUNNING_PLUGIN_PAUSED:
            case SCHEDULER_PAUSED:
                icon = "⏸️";
                break;
            case HARD_STOPPING_PLUGIN:
            case SOFT_STOPPING_PLUGIN:
                icon = "⏹️";
                break;
            case HOLD:
                icon = "⏳";
                break;
            case BREAK:
            case PLAYSCHEDULE_BREAK:
                icon = "☕";
                break;
            case WAITING_FOR_SCHEDULE:
                icon = "⌛";
                break;
            case WAITING_FOR_LOGIN:
                icon = "�";
                break;
            case LOGIN:
                icon = "�";
                break;
            case ERROR:
                icon = "❌";
                break;
            case INITIALIZING:
                icon = "🔄";
                break;
            case UNINITIALIZED:
                icon = "⚪";
                break;
            case WAITING_FOR_STOP_CONDITION:
                icon = "⏱️";
                break;
            default:
                icon = "❓";
                break;
        }
        return icon + " " + state.getDisplayName();
    }

    /**
     * Gets color based on progress percentage
     */
    private Color getProgressColor(double progress) {
        if (progress < 0.3) {
            return Color.RED;
        } else if (progress < 0.7) {
            return Color.YELLOW;
        } else {
            return Color.GREEN;
        }
    }

    /**
     * Formats duration in a human-readable format
     */
    private String formatDuration(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "00:00:00";
        }
        
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}
