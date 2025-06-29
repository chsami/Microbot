package net.runelite.client.plugins.microbot.pluginscheduler.ui;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerState;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.plugins.microbot.util.events.ScriptPauseEvent;

import javax.swing.*;

import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;


/**
 * Displays real-time information about the scheduler status and plugins
 */
@Slf4j
public class SchedulerInfoPanel extends JPanel {
    private final SchedulerPlugin plugin;    
    // Scheduler status components
    private final JLabel statusLabel;
    private final JLabel runtimeLabel;
    private ZonedDateTime schedulerStartTime;
    
    // Control buttons
    private final JButton runSchedulerButton;
    private final JButton stopSchedulerButton;
    private final JButton loginButton;
    private final JButton pauseResumeButton;
    private final JButton hardResetButton; // Added hard reset button
    
    // Current plugin components
    private final JPanel currentPluginPanel;
    private final JLabel currentPluginNameLabel;
    private final JLabel currentPluginRuntimeLabel;
    private final JProgressBar stopConditionProgressBar;
    private final JLabel stopConditionStatusLabel;
    private ZonedDateTime currentPluginStartTime;
    
    // Next plugin components
    private final JPanel nextPluginPanel;
    private final JLabel nextPluginNameLabel;
    private final JLabel nextPluginTimeLabel;
    private final JLabel nextPluginScheduleLabel;
    
    // Player status components
    private final JPanel playerStatusPanel;
    private final JLabel activityLabel;
    private final JLabel activityIntensityLabel;
    private final JLabel idleTimeLabel;
    private final JLabel loginTimeLabel;
    private final JLabel breakStatusLabel;
    private final JLabel nextBreakLabel;
    private final JLabel breakDurationLabel;    
    
    // Previous plugin panel
    private final JPanel prevPluginPanel;
    private final JLabel prevPluginNameLabel;
    private final JLabel prevPluginDurationLabel;
    private final JLabel prevPluginStatusLabel;
    private final JLabel prevPluginStopTimeLabel;
  
  
    public SchedulerInfoPanel(SchedulerPlugin plugin) {
        this.plugin = plugin;    
        // Use a box layout instead of BorderLayout
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Add panels with some vertical spacing
        JPanel statusPanel = createInfoPanel("Scheduler Status");
        GridBagConstraints gbc = createGbc(0, 0);
        
        statusPanel.add(new JLabel("Status:"), gbc);
        gbc.gridx++;
        statusLabel = createValueLabel("Not Running");
        statusPanel.add(statusLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        statusPanel.add(new JLabel("Runtime:"), gbc);
        gbc.gridx++;
        runtimeLabel = createValueLabel("00:00:00");
        statusPanel.add(runtimeLabel, gbc);
        
        // Create control buttons panel
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        // Use GridLayout with 3 rows instead of 2x2 to properly fit all 5 buttons
        JPanel buttonPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Create run scheduler button
        runSchedulerButton = createCompactButton("Run Scheduler", new Color(76, 175, 80));
        runSchedulerButton.addActionListener(e -> {
            plugin.startScheduler();
            updateButtonStates();
        });
        buttonPanel.add(runSchedulerButton);
        
        // Create stop scheduler button
        stopSchedulerButton = createCompactButton("Stop Scheduler", new Color(244, 67, 54));
        stopSchedulerButton.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> {
                plugin.stopScheduler();
                updateButtonStates();
            });
        });
        buttonPanel.add(stopSchedulerButton);
        
        // Create login button
        loginButton = createCompactButton("Login", new Color(33, 150, 243)); // Blue
        loginButton.addActionListener(e -> {            
            // Attempt login
            SwingUtilities.invokeLater(() -> {
                plugin.startLoginMonitoringThread();                                
            });
        });
        buttonPanel.add(loginButton);
        
        // Create pause/resume button
        pauseResumeButton = createCompactButton("Pause Plugin", new Color(255, 152, 0)); // Orange color
        pauseResumeButton.setVisible(false); // Initially hidden
        pauseResumeButton.addActionListener(e -> {
            // Toggle the pause state
            boolean newPauseState = !ScriptPauseEvent.isPaused();
            ScriptPauseEvent.setPaused(newPauseState);
            
            // Update button text and color based on state
            if (newPauseState) {
                pauseResumeButton.setText("Resume Plugin");
                pauseResumeButton.setBackground(new Color(76, 175, 80)); // Green color
            } else {
                pauseResumeButton.setText("Pause Plugin");
                pauseResumeButton.setBackground(new Color(255, 152, 0)); // Orange color
            }
            
            // Update UI immediately
            updateCurrentPluginInfo();
        });
        buttonPanel.add(pauseResumeButton);
        
        // Create hard reset button
        hardResetButton = createCompactButton("Hard Reset", new Color(156, 39, 176)); // Purple color
        hardResetButton.setToolTipText("Reset all user condition states for all scheduled plugins");
        hardResetButton.addActionListener(e -> showHardResetConfirmation());
        buttonPanel.add(hardResetButton);
        
        statusPanel.add(buttonPanel, gbc);
        
        add(statusPanel);
        add(Box.createRigidArea(new Dimension(0, 10))); // Add spacing
         
        // Create the player status panel
        playerStatusPanel = createInfoPanel("Player Status");
        gbc = createGbc(0, 0);

        playerStatusPanel.add(new JLabel("Activity:"), gbc);
        gbc.gridx++;
        activityLabel = createValueLabel("None");
        playerStatusPanel.add(activityLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        playerStatusPanel.add(new JLabel("Intensity:"), gbc);
        gbc.gridx++;
        activityIntensityLabel = createValueLabel("None");
        playerStatusPanel.add(activityIntensityLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        playerStatusPanel.add(new JLabel("Idle Time:"), gbc);
        gbc.gridx++;
        idleTimeLabel = createValueLabel("0 ticks");
        playerStatusPanel.add(idleTimeLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        playerStatusPanel.add(new JLabel("Login Duration:"), gbc);
        gbc.gridx++;
        loginTimeLabel = createValueLabel("Not logged in");
        playerStatusPanel.add(loginTimeLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        playerStatusPanel.add(new JLabel("Break Status:"), gbc);
        gbc.gridx++;
        breakStatusLabel = createValueLabel("Not on break");
        playerStatusPanel.add(breakStatusLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        playerStatusPanel.add(new JLabel("Next Break:"), gbc);
        gbc.gridx++;
        nextBreakLabel = createValueLabel("--:--:--");
        playerStatusPanel.add(nextBreakLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        playerStatusPanel.add(new JLabel("Current Break:"), gbc);
        gbc.gridx++;
        breakDurationLabel = createValueLabel("00:00:00");
        playerStatusPanel.add(breakDurationLabel, gbc);

        add(playerStatusPanel, BorderLayout.CENTER);


        currentPluginPanel = createInfoPanel("Current Plugin");
        gbc = createGbc(0, 0);
        
        currentPluginPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx++;
        currentPluginNameLabel = createValueLabel("None");
        currentPluginPanel.add(currentPluginNameLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        currentPluginPanel.add(new JLabel("Runtime:"), gbc);
        gbc.gridx++;
        currentPluginRuntimeLabel = createValueLabel("00:00:00");
        currentPluginPanel.add(currentPluginRuntimeLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        currentPluginPanel.add(new JLabel("Stop Conditions:"), gbc);
        gbc.gridx++;
        stopConditionStatusLabel = createValueLabel("None");
        stopConditionStatusLabel.setToolTipText("Detailed stop condition information");
        currentPluginPanel.add(stopConditionStatusLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        stopConditionProgressBar = new JProgressBar(0, 100);
        stopConditionProgressBar.setStringPainted(true);
        stopConditionProgressBar.setString("0%");
        stopConditionProgressBar.setForeground(new Color(76, 175, 80));
        stopConditionProgressBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        stopConditionProgressBar.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
        currentPluginPanel.add(stopConditionProgressBar, gbc);
        
        add(currentPluginPanel);
        add(Box.createRigidArea(new Dimension(0, 10))); // Add spacing
        
        nextPluginPanel = createInfoPanel("Next Scheduled Plugin");
        gbc = createGbc(0, 0);
        
        nextPluginPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx++;
        nextPluginNameLabel = createValueLabel("None");
        nextPluginPanel.add(nextPluginNameLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        nextPluginPanel.add(new JLabel("Scheduled Time:"), gbc);
        gbc.gridx++;
        nextPluginTimeLabel = createValueLabel("--:--");
        nextPluginPanel.add(nextPluginTimeLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        nextPluginPanel.add(new JLabel("Schedule Type:"), gbc);
        gbc.gridx++;
        nextPluginScheduleLabel = createValueLabel("None");
        nextPluginPanel.add(nextPluginScheduleLabel, gbc);
        
        add(nextPluginPanel);
        add(Box.createRigidArea(new Dimension(0, 10))); // Add spacing
        
        // Create Previous Plugin Panel
        prevPluginPanel = createInfoPanel("Previous Plugin");
        gbc = createGbc(0, 0);
        
        prevPluginPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx++;
        prevPluginNameLabel = createValueLabel("None");
        prevPluginPanel.add(prevPluginNameLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        prevPluginPanel.add(new JLabel("Duration:"), gbc);
        gbc.gridx++;
        prevPluginDurationLabel = createValueLabel("00:00:00");
        prevPluginPanel.add(prevPluginDurationLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        prevPluginPanel.add(new JLabel("Stop Reason:"), gbc);
        gbc.gridx++;
        prevPluginStatusLabel = createValueLabel("None");
        prevPluginPanel.add(prevPluginStatusLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        prevPluginPanel.add(new JLabel("Stop Time:"), gbc);
        gbc.gridx++;
        prevPluginStopTimeLabel = createValueLabel("--:--:--");
        prevPluginPanel.add(prevPluginStopTimeLabel, gbc);
        
        add(prevPluginPanel);
       
        // Initial refresh
        refresh();
    }
    
    private JPanel createInfoPanel(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ),
            title,
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            FontManager.getRunescapeBoldFont(),
            Color.WHITE
        ));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        return panel;
    }
    
    private GridBagConstraints createGbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = 1;
        gbc.gridheight = 1;
        gbc.weightx = (x == 0) ? 0.0 : 1.0; // Labels (x=0) don't expand, values (x=1) do
        gbc.weighty = 1.0;
        gbc.anchor = (x == 0) ? GridBagConstraints.WEST : GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);
        return gbc;
    }
    
    private JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }
    
    /**
     * Helper method to create and style a compact button
     * @param text Button text
     * @param bgColor Background color
     * @return Styled JButton
     */
    private JButton createCompactButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        // Make buttons more compact
        button.setFont(button.getFont().deriveFont(11f)); // Smaller font
        button.setMargin(new Insets(2, 4, 2, 4)); // Smaller margins
        return button;
    }
    
    /**
     * Refreshes all displayed information
     */
    public void refresh() {
        updateSchedulerStatus();
        updateCurrentPluginInfo();
        updateNextPluginInfo();
        updatePlayerStatusInfo();
        updatePreviousPluginInfo();
        updateButtonStates();
    }
    
    /**
     * Updates the button states based on scheduler state
     */
    private void updateButtonStates() {
        SchedulerState state = plugin.getCurrentState();
        boolean isActive = plugin.getCurrentState().isSchedulerActive();
        
        // Only enable run button if we're in READY or HOLD state
        runSchedulerButton.setEnabled(!isActive && (state == SchedulerState.READY || state == SchedulerState.HOLD));
        
        runSchedulerButton.setToolTipText(
            !runSchedulerButton.isEnabled() ? 
            "Scheduler cannot be started in " + state.getDisplayName() + " state" :
            "Start running the scheduler");
        
        // Only enable stop button if scheduler is active
        stopSchedulerButton.setEnabled(isActive);
        stopSchedulerButton.setToolTipText(
            isActive ? "Stop the scheduler" : "Scheduler is not running");
            
        // Login button is only enabled when not actively running and not waiting for login
        loginButton.setEnabled((!isActive || 
            (state != SchedulerState.WAITING_FOR_LOGIN && 
             state != SchedulerState.LOGIN)) && !Microbot.isLoggedIn());
        loginButton.setToolTipText("Log in to the game");
        
        // Only show the pause button when a plugin is actively running
        pauseResumeButton.setVisible(state == SchedulerState.RUNNING_PLUGIN);
        
        // If state changed and we're no longer running, ensure pause is reset
        if (state != SchedulerState.RUNNING_PLUGIN && ScriptPauseEvent.isPaused()) {
            ScriptPauseEvent.setPaused(false);
            pauseResumeButton.setText("Pause Plugin");
            pauseResumeButton.setBackground(new Color(255, 152, 0));
        }
        
        // Hard reset button is always enabled if there are plugins scheduled
        boolean hasScheduledPlugins = !plugin.getScheduledPlugins().isEmpty();
        hardResetButton.setEnabled(hasScheduledPlugins);
        hardResetButton.setToolTipText(
            hasScheduledPlugins ? 
            "Hard reset all user condition states for scheduled plugins" : 
            "No plugins scheduled to reset");
    }
    
    /**
     * Updates the scheduler status information
     */
    private void updateSchedulerStatus() {
        SchedulerState state = plugin.getCurrentState();
         // Update state information if available
        String stateInfo = state.getStateInformation();
        if (stateInfo != null && !stateInfo.isEmpty()) {
            statusLabel.setToolTipText(stateInfo);
        } else {
            statusLabel.setToolTipText(null);
        }
        // Update state display
        statusLabel.setText(state.getDisplayName());
        statusLabel.setForeground(state.getColor());
        
        // Update runtime if active
        if (plugin.getCurrentState().isSchedulerActive()) {
            if (schedulerStartTime == null) {
                schedulerStartTime = ZonedDateTime.now();
            }
            
            Duration runtime = Duration.between(schedulerStartTime, ZonedDateTime.now());
            long totalSeconds = runtime.getSeconds();
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            
            runtimeLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        } else {
            schedulerStartTime = null;
            runtimeLabel.setText("00:00:00");
        }
    }
    
    /**
     * Updates information about the currently running plugin
     */
    private void updateCurrentPluginInfo() {
        PluginScheduleEntry currentPlugin = plugin.getCurrentPlugin();
        
        if (currentPlugin != null && currentPlugin.isRunning()) {
            // Set visibility
            updatePanelVisibility(currentPluginPanel, true);
            
            // Update name with pause indicator if needed
            if (ScriptPauseEvent.isPaused()) {
                currentPluginNameLabel.setText(currentPlugin.getCleanName() + " [PAUSED]");
                currentPluginNameLabel.setForeground(new Color(255, 152, 0)); // Orange
            } else {
                currentPluginNameLabel.setText(currentPlugin.getCleanName());
                currentPluginNameLabel.setForeground(Color.WHITE);
            }
            
            // Update runtime
            if (currentPluginStartTime == null) {
                currentPluginStartTime = ZonedDateTime.now();
            }
            
            Duration runtime = Duration.between(currentPluginStartTime, ZonedDateTime.now());
            long totalSeconds = runtime.getSeconds();
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            
            currentPluginRuntimeLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
            
            // Update stop condition status
            if (currentPlugin.hasAnyStopConditions()) {
                int total = currentPlugin.getTotalStopConditionCount();
                int satisfied = currentPlugin.getSatisfiedStopConditionCount();
                
                // Updates the stop condition status text
                stopConditionStatusLabel.setText(String.format("%d/%d conditions met", satisfied, total));
                stopConditionStatusLabel.setToolTipText(currentPlugin.getDetailedStopConditionsStatus());
                
                // Update progress bar
                double progress = currentPlugin.getStopConditionProgress();
                stopConditionProgressBar.setValue((int) progress);
                stopConditionProgressBar.setString(String.format("%.1f%%", progress));
                
                // Color the progress bar based on progress
                if (progress > 80) {
                    stopConditionProgressBar.setForeground(new Color(76, 175, 80)); // Green
                } else if (progress > 50) {
                    stopConditionProgressBar.setForeground(new Color(255, 193, 7)); // Amber
                } else {
                    stopConditionProgressBar.setForeground(new Color(33, 150, 243)); // Blue
                }
                
                stopConditionProgressBar.setVisible(true);
            } else {
                stopConditionStatusLabel.setText("None");
                stopConditionStatusLabel.setToolTipText("No stop conditions defined");
                stopConditionProgressBar.setVisible(false);
            }
        } else {
            // Reset all fields
            currentPluginNameLabel.setText("None");
            currentPluginRuntimeLabel.setText("00:00:00");
            stopConditionStatusLabel.setText("None");
            stopConditionProgressBar.setValue(0);
            stopConditionProgressBar.setString("0%");
            currentPluginStartTime = null;
            updatePanelVisibility(currentPluginPanel, false);
        }
    }
    
    /**
     * Updates information about the next scheduled plugin
     */
    private void updateNextPluginInfo() {

        PluginScheduleEntry nextPlugin = plugin.getNextScheduledPlugin();
        
        if (nextPlugin != null) {
            // Set visibility
            updatePanelVisibility(nextPluginPanel, true);
            
            // Update name
            nextPluginNameLabel.setText(nextPlugin.getCleanName());
            
            // Set the next run time display (already handles various condition types)
            nextPluginTimeLabel.setText(nextPlugin.getNextRunDisplay());
            
            // Create an enhanced schedule description
            StringBuilder scheduleDesc = new StringBuilder(nextPlugin.getIntervalDisplay());
            
            // Add information about one-time schedules
            if (nextPlugin.hasAnyOneTimeStartConditions()) {
                if (nextPlugin.hasTriggeredOneTimeStartConditions() && !nextPlugin.canStartTriggerAgain()) {
                    scheduleDesc.append(" (Completed)");
                } else {
                    scheduleDesc.append(" (One-time)");
                }
            }
            
            nextPluginScheduleLabel.setText(scheduleDesc.toString());
        } else {
            // Reset all fields
            nextPluginNameLabel.setText("None");
            nextPluginTimeLabel.setText("--:--");
            nextPluginScheduleLabel.setText("None");
        }
        updatePanelVisibility(nextPluginPanel, nextPlugin != null);
    
    }
    
    /**
     * Updates information about the player's status
     */
    private void updatePlayerStatusInfo() {
        // Update activity info
        Activity activity = plugin.getCurrentActivity();
        if (activity != null) {
            activityLabel.setText(activity.toString());
            activityLabel.setForeground(Color.WHITE);
        } else {
            activityLabel.setText("None");
            activityLabel.setForeground(Color.GRAY);
        }
        
        // Update activity intensity
        ActivityIntensity intensity = plugin.getCurrentIntensity();
        if (intensity != null) {
            activityIntensityLabel.setText(intensity.getName());
            activityIntensityLabel.setForeground(Color.WHITE);
        } else {
            activityIntensityLabel.setText("None");
            activityIntensityLabel.setForeground(Color.GRAY);
        }
        
        // Update idle time
        int idleTime = plugin.getIdleTime();
        if (idleTime >= 0) {
            idleTimeLabel.setText(idleTime + " ticks");
            // Change color based on idle time
            if (idleTime > 100) {
                idleTimeLabel.setForeground(new Color(255, 106, 0)); // Orange for long idle
            } else if (idleTime > 50) {
                idleTimeLabel.setForeground(new Color(255, 193, 7)); // Yellow for medium idle
            } else {
                idleTimeLabel.setForeground(Color.WHITE); // Normal color
            }
        } else {
            idleTimeLabel.setText("0 ticks");
            idleTimeLabel.setForeground(Color.WHITE);
        }
        
        // Update login duration
        Duration loginDuration = Microbot.getLoginTime();
        if (loginDuration.getSeconds() > 0 && Microbot.isLoggedIn()) {
            long hours = loginDuration.toHours();
            long minutes = (loginDuration.toMinutes() % 60);
            long seconds = (loginDuration.getSeconds() % 60);
            loginTimeLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
            loginTimeLabel.setForeground(Color.WHITE);
        } else {
            loginTimeLabel.setText("Not logged in");
            loginTimeLabel.setForeground(Color.GRAY);
        }
        
        // Update break status
        boolean onBreak = plugin.isOnBreak();
        if (onBreak) {
            breakStatusLabel.setText("On Break");
            breakStatusLabel.setForeground(new Color(255, 193, 7)); // Amber color
        } else {
            breakStatusLabel.setText("Not on break");
            breakStatusLabel.setForeground(Color.WHITE);
        }
        
        // Update next break time
        Duration timeUntilBreak = plugin.getTimeUntilNextBreak();
        if (timeUntilBreak.getSeconds() > 0) {
            long hours = timeUntilBreak.toHours();
            long minutes = (timeUntilBreak.toMinutes() % 60);
            long seconds = (timeUntilBreak.getSeconds() % 60);
            nextBreakLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
            nextBreakLabel.setForeground(Color.WHITE);
        } else {
            nextBreakLabel.setText("--:--:--");
            nextBreakLabel.setForeground(Color.GRAY);
        }
        
        // Update current break duration
        Duration breakDuration = plugin.getCurrentBreakDuration();
        if (breakDuration.getSeconds() > 0) {
            long hours = breakDuration.toHours();
            long minutes = (breakDuration.toMinutes() % 60);
            long seconds = (breakDuration.getSeconds() % 60);
            breakDurationLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
            breakDurationLabel.setForeground(onBreak ? new Color(255, 193, 7) : Color.WHITE); // Amber if on break
        } else {
            breakDurationLabel.setText("00:00:00");
            breakDurationLabel.setForeground(Color.GRAY);
        }
    }

    /**
     * Updates information about the previously run plugin
     */
    private void updatePreviousPluginInfo() {
        PluginScheduleEntry lastPlugin = plugin.getLastPlugin();
        
        if (lastPlugin != null) {
            // Set visibility
            updatePanelVisibility(prevPluginPanel, true);
            
            // Update name
            prevPluginNameLabel.setText(lastPlugin.getCleanName());
            
            // Update duration if available
            if (lastPlugin.getLastRunDuration() != null && !lastPlugin.getLastRunDuration().isZero()) {
                Duration duration = lastPlugin.getLastRunDuration();
                long hours = duration.toHours();
                long minutes = (duration.toMinutes() % 60);
                long seconds = (duration.getSeconds() % 60);
                prevPluginDurationLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
            } else {
                prevPluginDurationLabel.setText("Unknown");
            }
            
            // Update stop reason
            String stopReason = lastPlugin.getLastStopReason();
            PluginScheduleEntry.StopReason stopReasonType = lastPlugin.getLastStopReasonType();
            
            if (stopReason != null && !stopReason.isEmpty()) {
                prevPluginStatusLabel.setText(stopReason);
                
                // Set color based on stop reason type
                if (stopReasonType != null) {
                    switch (stopReasonType) {
                        case PLUGIN_FINISHED:
                            prevPluginStatusLabel.setForeground(new Color(76, 175, 80)); // Green
                            break;
                        case ERROR:
                            prevPluginStatusLabel.setForeground(new Color(244, 67, 54)); // Red
                            break;
                        case INTERRUPTED:
                            prevPluginStatusLabel.setForeground(new Color(255, 152, 0)); // Orange
                            break;
                        default:
                            prevPluginStatusLabel.setForeground(Color.WHITE);
                            break;
                    }
                }
            } else {
                prevPluginStatusLabel.setText("Unknown");
                prevPluginStatusLabel.setForeground(Color.WHITE);
            }
            
            // Update stop time
            if (lastPlugin.getLastRunEndTime() != null) {
                ZonedDateTime stopTime = lastPlugin.getLastRunEndTime();
                prevPluginStopTimeLabel.setText(stopTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            } else {
                prevPluginStopTimeLabel.setText("Unknown");
            }
        } else {
            // Reset all fields
            prevPluginNameLabel.setText("None");
            prevPluginDurationLabel.setText("00:00:00");
            prevPluginStatusLabel.setText("N/A");
            prevPluginStopTimeLabel.setText("--:--:--");
            updatePanelVisibility(prevPluginPanel, false);
        }
    }

    /**
     * Updates panel visibility and ensures proper resizing
     * @param panel The panel to update
     * @param visible Whether the panel should be visible
     */
    private void updatePanelVisibility(JPanel panel, boolean visible) {
        boolean wasVisible = panel.isVisible();
        panel.setVisible(visible);
        
        // Only revalidate if visibility changed
        if (wasVisible != visible) {
            // Trigger complete layout recalculation
            SwingUtilities.invokeLater(() -> {
                Container parent = getParent();
                while (parent != null) {
                    parent.invalidate();
                    parent = parent.getParent();
                }
                revalidate();
                repaint();
            });
        }
    }
    
    /**
     * Shows a confirmation dialog for hard resetting all user conditions
     */
    private void showHardResetConfirmation() {
        String message = 
            "<html><body width='400'>" +
            "<h2>Hard Reset All User Conditions</h2>" +
            "<p>This will perform a complete reset of all user conditions for all scheduled plugins.</p>" +
            "<p><b>This will reset:</b></p>" +
            "<ul>" +
            "<li>All accumulated state tracking variables</li>" +
            "<li>Maximum trigger counters</li>" +
            "<li>Daily/periodic usage limits</li>" +
            "<li>Historical tracking data</li>" +
            "<li>Time-based condition states</li>" +
            "</ul>" +
            "<p><b>Are you sure you want to continue?</b></p>" +
            "</body></html>";
        
        int result = JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(this),
            message,
            "Hard Reset Confirmation",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            hardResetAllUserConditions();
        }
    }
    
    /**
     * Performs a hard reset on all user conditions for all scheduled plugins
     */
    private void hardResetAllUserConditions() {
        try {
            // Delegate the hard reset operation to the SchedulerPlugin
            List<String> resetPlugins = plugin.hardResetAllUserConditions();
            
            // Show success message with details
            String resultMessage = String.format(
                "<html><body width='400'>" +
                "<h2>Hard Reset Complete</h2>" +
                "<p>Successfully reset %d user condition states.</p>",
                resetPlugins.size());
            
            if (!resetPlugins.isEmpty()) {
                resultMessage += "<p><b>Reset conditions for:</b></p><ul>";
                for (String pluginName : resetPlugins) {
                    resultMessage += "<li>" + pluginName + "</li>";
                }
                resultMessage += "</ul>";
            }
            
            resultMessage += "</body></html>";
            
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                resultMessage,
                "Hard Reset Complete",
                JOptionPane.INFORMATION_MESSAGE
            );
            
            log.info("Hard reset completed for {} user condition states", resetPlugins.size());
            
        } catch (Exception e) {
            // Show error message
            JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                "An error occurred while resetting user conditions: " + e.getMessage(),
                "Hard Reset Error",
                JOptionPane.ERROR_MESSAGE
            );
            
            log.error("Error during hard reset of user conditions", e);
        }
    }
}