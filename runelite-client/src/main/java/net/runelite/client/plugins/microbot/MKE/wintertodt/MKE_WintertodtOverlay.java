package net.runelite.client.plugins.microbot.MKE.wintertodt;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.MKE.wintertodt.enums.State;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

/**
 * Overlay panel for displaying Wintertodt bot statistics and current status.
 * Shows real-time information about bot performance, actions taken, and current state.
 */
public class MKE_WintertodtOverlay extends OverlayPanel {
    private final MKE_WintertodtPlugin plugin;
    private final MKE_WintertodtConfig config;

    @Inject
    MKE_WintertodtOverlay(MKE_WintertodtPlugin plugin, MKE_WintertodtConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    /**
     * Renders the overlay with current bot statistics and status information.
     * Uses color coding to indicate different types of information.
     */
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            // Set panel dimensions and styling (increased for antiban info)
            panelComponent.setPreferredSize(new Dimension(220, 450));

            // Main title with version
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("MKE Wintertodt Bot v" + MKE_WintertodtScript.version)
                    .color(Color.CYAN)
                    .build());

            // Separator line
            addSeparator();

            // Runtime information
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(plugin.getTimeRunning())
                    .leftColor(Color.WHITE)
                    .rightColor(Color.GREEN)
                    .build());

            // Show startup phase if not completed
            if (!plugin.isStartupCompleted()) {
                String phase = plugin.getStartupPhase();
                Color phaseColor = Color.CYAN;
                
                // Use different colors for different phases
                if (phase.contains("game room")) {
                    phaseColor = Color.GREEN;
                } else if (phase.contains("gear")) {
                    phaseColor = Color.ORANGE;
                } else if (phase.contains("inventory")) {
                    phaseColor = Color.MAGENTA;
                }
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Startup Phase:")
                        .right(phase)
                        .leftColor(Color.WHITE)
                        .rightColor(phaseColor)
                        .build());
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status:")
                        .right(plugin.getStartupStatus())
                        .leftColor(Color.WHITE)
                        .rightColor(Color.YELLOW)
                        .build());
            } else {
                // Current state with color coding
                Color stateColor = getStateColor(MKE_WintertodtScript.state);
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Current State:")
                        .right(MKE_WintertodtScript.state.toString())
                        .leftColor(Color.WHITE)
                        .rightColor(stateColor)
                        .build());
            }

            addSeparator();

            // Game statistics
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Games Won:")
                    .right(String.valueOf(plugin.getWon()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Games Lost:")
                    .right(String.valueOf(plugin.getLost()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.RED)
                    .build());

            // Calculate win rate
            int totalGames = plugin.getWon() + plugin.getLost();
            double winRate = totalGames > 0 ? (double) plugin.getWon() / totalGames * 100 : 0;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Win Rate:")
                    .right(String.format("%.1f%%", winRate))
                    .leftColor(Color.WHITE)
                    .rightColor(winRate >= 80 ? Color.GREEN : winRate >= 60 ? Color.YELLOW : Color.RED)
                    .build());

            addSeparator();

            // Action statistics
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Logs Cut:")
                    .right(String.valueOf(plugin.getLogsCut()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.LIGHT_GRAY)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Logs Fletched:")
                    .right(String.valueOf(plugin.getLogsFletched()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.LIGHT_GRAY)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Braziers Fixed:")
                    .right(String.valueOf(plugin.getBraziersFixed()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Braziers Lit:")
                    .right(String.valueOf(plugin.getBraziersLit()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.ORANGE)
                    .build());

            addSeparator();

            // Resource management
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Food Consumed:")
                    .right(String.valueOf(plugin.getFoodConsumed()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.PINK)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Banking Trips:")
                    .right(String.valueOf(plugin.getTimesBanked()))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.CYAN)
                    .build());

            // Current bot status
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(Microbot.status)
                    .leftColor(Color.WHITE)
                    .rightColor(Color.YELLOW)
                    .build());

            addSeparator();

            // AI Decision Making Panel
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("AI Decision Process")
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Est. Time Left (Final):")
                    .right(String.format("%.1fs", MKE_WintertodtScript.estimatedSecondsLeft))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.YELLOW)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("  └─ From History:")
                    .right(String.format("%.1fs", MKE_WintertodtScript.historicalEstimateSecondsLeft))
                    .leftColor(Color.GRAY)
                    .rightColor(Color.ORANGE)
                    .build());

            /* ---------- Action-Plan summary (dynamic) -------------------- */
            StringBuilder plan = new StringBuilder();
            StringBuilder prog = new StringBuilder();

            if (MKE_WintertodtScript.rootsToChopGoal > 0) {
                plan.append("Ch ").append(MKE_WintertodtScript.rootsToChopGoal);
                prog.append(MKE_WintertodtScript.rootsChoppedThisRun)
                    .append('/').append(MKE_WintertodtScript.rootsToChopGoal);
            }
            if (MKE_WintertodtScript.fletchGoal > 0) {
                if (plan.length() > 0) { plan.append(" | "); prog.append(" | "); }
                plan.append("Fl ").append(MKE_WintertodtScript.fletchGoal);
                prog.append(MKE_WintertodtScript.fletchedThisRun)
                    .append('/').append(MKE_WintertodtScript.fletchGoal);
            }
            if (MKE_WintertodtScript.feedGoal > 0) {
                if (plan.length() > 0) { plan.append(" | "); prog.append(" | "); }
                plan.append("Fe ").append(MKE_WintertodtScript.feedGoal);
                prog.append(MKE_WintertodtScript.fedThisRun)
                    .append('/').append(MKE_WintertodtScript.feedGoal);
            }

            if (plan.length() > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Plan:")
                        .right(plan.toString())
                        .leftColor(Color.WHITE)
                        .rightColor(Color.CYAN)
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Progress:")
                        .right(prog.toString())
                        .leftColor(Color.WHITE)
                        .rightColor(Color.GREEN)
                        .build());
            }

            /* ---- Possible cycles left in the round ------------------- */
            if (MKE_WintertodtScript.cycleTimeSec > 0)
            {
                int cyclesLeft = (int) Math.floor(
                        Math.max(0,
                                (MKE_WintertodtScript.estimatedSecondsLeft - 10)   // keep 10 s safety
                                / MKE_WintertodtScript.cycleTimeSec));

                cyclesLeft += MKE_WintertodtScript.EXTRA_ROOTS_BUFFER;           // include the plan buffer

                if (cyclesLeft > 0)     // show only when meaningful
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Est. Feeds left:")
                            .right(String.valueOf(cyclesLeft))
                            .leftColor(Color.WHITE)
                            .rightColor(Color.CYAN)
                            .build());
                }
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Full Cycle Time:")
                    .right(String.format("%.2fs", MKE_WintertodtScript.cycleTimeSec))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.CYAN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Avg Chop:")
                    .right(String.format("%.2fs", MKE_WintertodtScript.avgChopMs / 1000.0))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.LIGHT_GRAY)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Avg Fletch:")
                    .right(String.format("%.2fs", MKE_WintertodtScript.avgFletchMs / 1000.0))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.LIGHT_GRAY)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Avg Feed:")
                    .right(String.format("%.2fs", MKE_WintertodtScript.avgFeedMs / 1000.0))
                    .leftColor(Color.WHITE)
                    .rightColor(Color.LIGHT_GRAY)
                    .build());

            addSeparator();

            // Break System Information
            if (config.enableCustomBreaks()) {
                panelComponent.getChildren().add(TitleComponent.builder()
                        .text("Custom Break System")
                        .color(Color.MAGENTA)
                        .build());

                if (WintertodtBreakManager.isBreakActive()) {
                    String breakType = WintertodtBreakManager.isAfkBreakActive() ? "AFK Break" : "Logout Break";
                    Color breakColor = WintertodtBreakManager.isAfkBreakActive() ? Color.ORANGE : Color.RED;
                    
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Break Active:")
                            .right(breakType)
                            .leftColor(Color.WHITE)
                            .rightColor(breakColor)
                            .build());

                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Time Remaining:")
                            .right(WintertodtBreakManager.getBreakTimeRemaining())
                            .leftColor(Color.WHITE)
                            .rightColor(Color.YELLOW)
                            .build());
                } else {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Next Break In:")
                            .right(WintertodtBreakManager.getTimeUntilNextBreak())
                            .leftColor(Color.WHITE)
                            .rightColor(Color.GREEN)
                            .build());
                }

                addSeparator();
            }

            // Add antiban overlay components if antiban is enabled and user wants to show it
            if (config.showAntibanOverlay() && Rs2AntibanSettings.antibanEnabled && Rs2Antiban.getActivity() != null) {
                Rs2Antiban.renderAntibanOverlayComponents(panelComponent);
            }

        } catch (Exception ex) {
            // Graceful error handling for overlay rendering
            System.err.println("Error rendering Wintertodt overlay: " + ex.getMessage());
            ex.printStackTrace();
        }
        return super.render(graphics);
    }

    /**
     * Adds a separator line to the overlay for better visual organization.
     */
    private void addSeparator() {
        panelComponent.getChildren().add(LineComponent.builder().build());
    }

    /**
     * Returns appropriate color for the current bot state.
     * @param state Current bot state
     * @return Color representing the state
     */
    private Color getStateColor(State state) {
        switch (state) {
            case BANKING:
                return Color.CYAN;
            case ENTER_ROOM:
                return Color.BLUE;
            case WAITING:
                return Color.YELLOW;
            case LIGHT_BRAZIER:
                return Color.ORANGE;
            case CHOP_ROOTS:
                return Color.GREEN;
            case FLETCH_LOGS:
                return Color.MAGENTA;
            case BURN_LOGS:
                return Color.RED;
            case GET_CONCOCTIONS:
                return Color.PINK;
            case GET_HERBS:
                return new Color(144, 238, 144); // Light green
            case MAKE_POTIONS:
                return new Color(255, 165, 0); // Orange
            default:
                return Color.WHITE;
        }
    }
}