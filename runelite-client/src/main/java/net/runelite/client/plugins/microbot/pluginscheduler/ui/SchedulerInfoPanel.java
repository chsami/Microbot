package net.runelite.client.plugins.microbot.pluginscheduler.ui;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerState;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.util.UIUtils;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.plugins.microbot.util.events.PluginPauseEvent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.util.List;
import java.util.stream.Collectors;
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
    private final JButton pauseResumePluginButton; // button for only pusing the currently running  plugin (PluginScheduleEntry), by SchedulerPlugin
    private final JButton pauseResumeSchedulerButton; // button for pausing the whole scheduler -> all condition progress is paused for all PluginScheduleEntry currently running managed by SchedulerPlugin by SchedulerPlugin
    // Added hard reset button to reset all user condition states for all scheduled plugins-> initial settings are applied again to all start and stop conditions for  the curre
    private final JButton hardResetButton;
    
    // Combined plugin information panel
    private final JPanel pluginInfoPanel;
    
    // Current plugin components
    private JLabel currentPluginNameLabel;
    private JLabel currentPluginRuntimeLabel;
    private JProgressBar stopConditionProgressBar;
    private JLabel stopConditionStatusLabel;
    private ZonedDateTime currentPluginStartTime;
    
    // Next plugin components
    private JLabel nextUpComingPluginNameLabel;
    private JLabel nextUpComingPluginTimeLabel;
    private JLabel nextUpComingPluginScheduleLabel;
    
    // Previous plugin components
    private JLabel prevPluginNameLabel;
    private JLabel prevPluginDurationLabel;
    private JTextArea prevPluginStatusLabel;
    private JLabel prevPluginStopTimeLabel;
    
    // Player status components
    private final JPanel playerStatusPanel;
    private final JLabel activityLabel;
    private final JLabel activityIntensityLabel;
    private final JLabel idleTimeLabel;
    private final JLabel loginTimeLabel;
    private final JLabel breakStatusLabel;
    private final JLabel nextBreakLabel;
    private final JLabel breakDurationLabel;    

    // State tracking for optimized updates
    private PluginScheduleEntry lastTrackedCurrentPlugin;
    private PluginScheduleEntry lastTrackedPreviousPlugin;
    private PluginScheduleEntry lastTrackedNextUpComingPlugin;
  
  
    public SchedulerInfoPanel(SchedulerPlugin plugin) {
        this.plugin = plugin;    
        // Use a box layout instead of BorderLayout
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(4, 4, 4, 4)); // Reduced padding from 8,8,8,8 for tighter layout
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Add panels with some vertical spacing
        JPanel statusPanel = UIUtils.createInfoPanel("Scheduler Status");
        GridBagConstraints gbc = UIUtils.createGbc(0, 0);
        
        statusPanel.add(new JLabel("Status:"), gbc);
        gbc.gridx++;
        statusLabel = UIUtils.createValueLabel("Not Running");
        statusPanel.add(statusLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        statusPanel.add(new JLabel("Runtime:"), gbc);
        gbc.gridx++;
        runtimeLabel = UIUtils.createValueLabel("00:00:00");
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
        pauseResumePluginButton = createCompactButton("Pause Plugin", new Color(0, 188, 212)); // Cyan color
        pauseResumePluginButton.setVisible(false); // Initially hidden
        pauseResumePluginButton.addActionListener(e -> {
            // Toggle the pause state
            
            
            // Update button text and color based on state
            if (!plugin.isCurrentPluginPaused()) {
                boolean pauseSuccess = plugin.pauseRunningPlugin();
                if (pauseSuccess){
                    pauseResumePluginButton.setText("Resume Plugin");
                    pauseResumePluginButton.setBackground(new Color(76, 175, 80)); // Green color
                }
            } else {
                if(plugin.isCurrentPluginPaused()){
                    plugin.resumeRunningPlugin();
                    pauseResumePluginButton.setText("Pause Plugin");
                    pauseResumePluginButton.setBackground(new Color(0, 188, 212)); // Cyan color
                }
            }
            updateCurrentPluginInfo();
            updateButtonStates();            
        });
        buttonPanel.add(pauseResumePluginButton);
        
        // Create pause/resume scheduler button
        pauseResumeSchedulerButton = createCompactButton("Pause Scheduler", new Color(255, 152, 0)); // Orange color
        pauseResumeSchedulerButton.addActionListener(e -> {
            // Toggle the pause state using our new methods
            if (plugin.isPaused() ) {
                // Currently paused, so resume
                plugin.resumeScheduler();
                pauseResumeSchedulerButton.setText("Pause Scheduler");
                pauseResumeSchedulerButton.setBackground(new Color(255, 152, 0)); // Orange color
            }else if(plugin.isOnBreak() && (plugin.getCurrentState() == SchedulerState.BREAK) || 
                    plugin.getCurrentState() == SchedulerState.PLAYSCHEDULE_BREAK){
                // If currently on break, resume the break
                plugin.resumeBreak();                                           
            }else {
                // Currently running, so pause
                plugin.pauseScheduler();
                pauseResumeSchedulerButton.setText("Resume Scheduler");
                pauseResumeSchedulerButton.setBackground(new Color(76, 175, 80)); // Green color
            }
            
            // Update UI
            updateCurrentPluginInfo();
            updateButtonStates();
        });
        buttonPanel.add(pauseResumeSchedulerButton);
        
        // Create hard reset button
        hardResetButton = createCompactButton("Hard Reset", new Color(156, 39, 176)); // Purple color
        hardResetButton.setToolTipText("Reset all user condition states for all scheduled plugins");
        hardResetButton.addActionListener(e -> showHardResetConfirmation());
        buttonPanel.add(hardResetButton);
        
        statusPanel.add(buttonPanel, gbc);
        
        add(statusPanel);
        add(Box.createRigidArea(new Dimension(0, 10))); // Add spacing
         
        // Create the player status panel
        playerStatusPanel = UIUtils.createInfoPanel("Player Status");
        gbc = UIUtils.createGbc(0, 0);

        playerStatusPanel.add(new JLabel("Activity:"), gbc);
        gbc.gridx++;
        activityLabel = UIUtils.createValueLabel("None");
        playerStatusPanel.add(activityLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        playerStatusPanel.add(new JLabel("Intensity:"), gbc);
        gbc.gridx++;
        activityIntensityLabel = UIUtils.createValueLabel("None");
        playerStatusPanel.add(activityIntensityLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        playerStatusPanel.add(new JLabel("Idle Time:"), gbc);
        gbc.gridx++;
        idleTimeLabel = UIUtils.createValueLabel("0 ticks");
        playerStatusPanel.add(idleTimeLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        playerStatusPanel.add(new JLabel("Login Duration:"), gbc);
        gbc.gridx++;
        loginTimeLabel = UIUtils.createValueLabel("Not logged in");
        playerStatusPanel.add(loginTimeLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        playerStatusPanel.add(new JLabel("Break Status:"), gbc);
        gbc.gridx++;
        breakStatusLabel = UIUtils.createValueLabel("Not on break");
        playerStatusPanel.add(breakStatusLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        playerStatusPanel.add(new JLabel("Next Break:"), gbc);
        gbc.gridx++;
        nextBreakLabel = UIUtils.createValueLabel("--:--:--");
        playerStatusPanel.add(nextBreakLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        playerStatusPanel.add(new JLabel("Current Break:"), gbc);
        gbc.gridx++;
        breakDurationLabel = UIUtils.createValueLabel("00:00:00");
        playerStatusPanel.add(breakDurationLabel, gbc);

        add(playerStatusPanel, BorderLayout.CENTER);

        // Create compact plugin information panel
        pluginInfoPanel = createDynamicPluginInfoPanel();
        add(pluginInfoPanel);
        // Removed rigid area spacing for tighter layout
       
        // Initial refresh
        refresh();
    }
    
    /**
     * Creates a dynamic, responsive plugin info panel that adapts to content and window size
     * This layout automatically adjusts based on text length and available space
     * Now uses the modular UIUtils for better maintainability
     */
    private JPanel createDynamicPluginInfoPanel() {
        // Create sections using utility methods
        JPanel prevSection = UIUtils.createAdaptiveSection("Previous");
        JPanel currentSection = UIUtils.createAdaptiveSection("Current");
        JPanel nextSection = UIUtils.createAdaptiveSection("Next");

        // Add content to sections using utility methods
        addPreviousPluginContentWithUtils(prevSection);
        addCurrentPluginContentWithUtils(currentSection);
        addNextPluginContentWithUtils(nextSection);

        // Create bottom panel for progress and stop reason
        JPanel bottomPanel = createDynamicBottomPanelWithUtils();

        // Create the main panel using utility
        JPanel[] sections = {prevSection, currentSection, nextSection};
        return UIUtils.createDynamicInfoPanel("Plugin Information", sections, bottomPanel);
    }

    /**
     * Adds content to the previous plugin section using utility methods
     */
    private void addPreviousPluginContentWithUtils(JPanel section) {
        prevPluginNameLabel = UIUtils.createAdaptiveValueLabel("None");
        prevPluginDurationLabel = UIUtils.createAdaptiveValueLabel("00:00:00");
        prevPluginStopTimeLabel = UIUtils.createAdaptiveValueLabel("--:--:--");

        UIUtils.LabelValuePair[] rows = {
            new UIUtils.LabelValuePair("Name:", prevPluginNameLabel),
            new UIUtils.LabelValuePair("Duration:", prevPluginDurationLabel),
            new UIUtils.LabelValuePair("Stop Time:", prevPluginStopTimeLabel)
        };

        UIUtils.addContentToSection(section, rows);
    }

    /**
     * Adds content to the current plugin section using utility methods
     */
    private void addCurrentPluginContentWithUtils(JPanel section) {
        currentPluginNameLabel = UIUtils.createAdaptiveValueLabel("None");
        currentPluginRuntimeLabel = UIUtils.createAdaptiveValueLabel("00:00:00");
        stopConditionStatusLabel = UIUtils.createAdaptiveValueLabel("None");
        stopConditionStatusLabel.setToolTipText("Detailed stop condition information");

        UIUtils.LabelValuePair[] rows = {
            new UIUtils.LabelValuePair("Name:", currentPluginNameLabel),
            new UIUtils.LabelValuePair("Runtime:", currentPluginRuntimeLabel),
            new UIUtils.LabelValuePair("Conditions:", stopConditionStatusLabel)
        };

        UIUtils.addContentToSection(section, rows);
    }

    /**
     * Adds content to the next plugin section using utility methods
     */
    private void addNextPluginContentWithUtils(JPanel section) {
        nextUpComingPluginNameLabel = UIUtils.createAdaptiveValueLabel("None");
        nextUpComingPluginTimeLabel = UIUtils.createAdaptiveValueLabel("--:--");
        nextUpComingPluginScheduleLabel = UIUtils.createAdaptiveValueLabel("None");

        UIUtils.LabelValuePair[] rows = {
            new UIUtils.LabelValuePair("Name:", nextUpComingPluginNameLabel),
            new UIUtils.LabelValuePair("Time:", nextUpComingPluginTimeLabel),
            new UIUtils.LabelValuePair("Type:", nextUpComingPluginScheduleLabel)
        };

        UIUtils.addContentToSection(section, rows);
    }

    /**
     * Creates the dynamic bottom panel using utility methods
     */
    private JPanel createDynamicBottomPanelWithUtils() {
        // Create progress bar
        stopConditionProgressBar = new JProgressBar(0, 100);
        stopConditionProgressBar.setStringPainted(true);
        stopConditionProgressBar.setString("No conditions");
        stopConditionProgressBar.setForeground(new Color(76, 175, 80));
        stopConditionProgressBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        stopConditionProgressBar.setPreferredSize(new Dimension(0, 16));

        // Create status text area
        prevPluginStatusLabel = UIUtils.createAdaptiveTextArea("None");
        
        return UIUtils.createDynamicBottomPanel(stopConditionProgressBar, prevPluginStatusLabel);
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
     * Helper method to create a text area for multi-line text display
     * @param text Initial text for the text area
     * @return A configured JTextArea
     */
    private JTextArea createMultiLineTextArea(String text) {
        JTextArea textArea = new JTextArea(text);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        textArea.setForeground(Color.WHITE);
        textArea.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        textArea.setFont(FontManager.getRunescapeFont());
        return textArea;
    }

    /**
     * Refreshes all displayed information with selective updates based on plugin state changes
     */
    public void refresh() {
        // Always update scheduler status and buttons for real-time feedback
        updateSchedulerStatus();
        updateButtonStates();
        
        // Always update player status as it changes frequently
        updatePlayerStatusInfo();
        
        // Get current plugin states
        PluginScheduleEntry currentPlugin = plugin.getCurrentPlugin();
        PluginScheduleEntry previousPlugin = plugin.getLastPlugin();
        PluginScheduleEntry nextUpComingPlugin = plugin.getUpComingPlugin();
        
        // Update current plugin info if it changed or is running (for runtime updates)
        if (currentPlugin != lastTrackedCurrentPlugin) {
            updateCurrentPluginInfo();
            lastTrackedCurrentPlugin = currentPlugin;
        } else if (currentPlugin != null && currentPlugin.isRunning()) {
            // Always update runtime for running plugins even if plugin object hasn't changed
            updateCurrentPluginRuntimeOnly();
        }
        
        // Update previous plugin info only if it changed
        if (previousPlugin != lastTrackedPreviousPlugin) {
            updatePreviousPluginInfo();
            lastTrackedPreviousPlugin = previousPlugin;
        }
        
        // Update next plugin info if it changed
        if (nextUpComingPlugin != lastTrackedNextUpComingPlugin) {
            updateNextUpComingPluginInfo();
            lastTrackedNextUpComingPlugin = nextUpComingPlugin;
        } else if (nextUpComingPlugin != null) {
            // Always update time display for next plugin since countdown changes every second
            updateNextUpComingPluginTimeDisplay(nextUpComingPlugin);
        }
    }
    
    /**
     * Forces an immediate update of all plugin information.
     * Useful when plugin states change and immediate UI refresh is needed.
     */
    public void forcePluginInfoUpdate() {
        lastTrackedCurrentPlugin = null;
        lastTrackedPreviousPlugin = null;
        lastTrackedNextUpComingPlugin = null;
        updateCurrentPluginInfo();
        updatePreviousPluginInfo(); 
        updateNextUpComingPluginInfo();
    }
    
    /**
     * Updates the button states based on scheduler state
     */
    private void updateButtonStates() {
        SchedulerState state = plugin.getCurrentState();
        boolean isActive = plugin.getCurrentState().isSchedulerActive();
        
        // Only enable run button if we're in READY or HOLD state
        runSchedulerButton.setEnabled((!isActive && (state == SchedulerState.READY || state == SchedulerState.HOLD)) && !state.isPaused());
        
        runSchedulerButton.setToolTipText(
            !runSchedulerButton.isEnabled() ? 
            "Scheduler cannot be started in " + state.getDisplayName() + " state" :
            "Start running the scheduler");
        
        // Only enable stop button if scheduler is active
        stopSchedulerButton.setEnabled(isActive);
        stopSchedulerButton.setToolTipText(
            isActive ? "Stop the scheduler" : "Scheduler is not running");
            
        // Login button is only enreportFinishedabled when not actively running and not waiting for login
        loginButton.setEnabled((!isActive || 
            (state != SchedulerState.WAITING_FOR_LOGIN && 
             state != SchedulerState.LOGIN)) && !Microbot.isLoggedIn());
        loginButton.setToolTipText("Log in to the game");
        
       
        
        // Update pause/resume scheduler button state
        pauseResumeSchedulerButton.setEnabled(isActive || plugin.getCurrentState()==  SchedulerState.SCHEDULER_PAUSED || plugin.getCurrentState() == SchedulerState.BREAK);
        
        boolean isSchedulerPaused = plugin.isPaused();
        
        if (isSchedulerPaused || plugin.isOnBreak()) {
            if (plugin.isOnBreak()){
                pauseResumeSchedulerButton.setText("Resume Break");
                pauseResumeSchedulerButton.setBackground(new Color(76, 175, 80)); // Green color
                pauseResumeSchedulerButton.setToolTipText("Resume the break");
                pauseResumePluginButton.setEnabled(false); // Disable plugin pause/resume while scheduler is paused
            }else{
                pauseResumeSchedulerButton.setText("Resume Scheduler");
                pauseResumeSchedulerButton.setBackground(new Color(76, 175, 80)); // Green color
                pauseResumeSchedulerButton.setToolTipText("Resume the paused scheduler");
                pauseResumePluginButton.setEnabled(false); // Disable plugin pause/resume while scheduler is paused
            }
        } else {
            pauseResumeSchedulerButton.setText("Pause Scheduler");
            pauseResumeSchedulerButton.setBackground(new Color(255, 152, 0)); // Orange color
            pauseResumeSchedulerButton.setToolTipText("Pause the scheduler without stopping it");
            pauseResumePluginButton.setEnabled(true); // Enable plugin pause/resume while scheduler is running
        }
        if ( state.isBreaking()){
            pauseResumeSchedulerButton.setEnabled(false);
        }
        boolean currentRunningPluginPaused = plugin.isCurrentPluginPaused();        
        
         // Only show the pause button when a plugin is actively running
        pauseResumePluginButton.setVisible(state == SchedulerState.RUNNING_PLUGIN || 
                                    state == SchedulerState.RUNNING_PLUGIN_PAUSED);
        
        // If Scheulder PLugin is not Running any Plugin at the moment -> detect changed state and we're no longer running, ensure pause for scripts and plugin is reset
        
        if (state == SchedulerState.RUNNING_PLUGIN && !(state == SchedulerState.RUNNING_PLUGIN_PAUSED ||  
                state == SchedulerState.SCHEDULER_PAUSED) && PluginPauseEvent.isPaused()) {            
            PluginPauseEvent.setPaused(false); 
            pauseResumePluginButton.setText("Pause Plugin");
            pauseResumePluginButton.setBackground(new Color(255, 152, 0));
        }
        if(state == SchedulerState.RUNNING_PLUGIN ||
           state == SchedulerState.RUNNING_PLUGIN_PAUSED){
            pauseResumePluginButton.setEnabled(true);
            // Update pause/resume plugin button state
            if (currentRunningPluginPaused) {
                pauseResumePluginButton.setText("Resume Plugin");
                pauseResumePluginButton.setBackground(new Color(76, 175, 80)); // Green color
                pauseResumePluginButton.setToolTipText("Resume the currently paused plugin");
                pauseResumeSchedulerButton.setEnabled(false); // Disable scheduler pause/resume while a plugin is paused
            } else {
                pauseResumePluginButton.setText("Pause Plugin");
                pauseResumePluginButton.setBackground(new Color(0, 188, 212)); // Cyan color
                pauseResumePluginButton.setToolTipText("Pause the currently running plugin");
                pauseResumeSchedulerButton.setEnabled(true); // Disable scheduler pause/resume while a plugin is paused
            }
        }else {
            // If the scheduler is paused, disable the pause/resume plugin button
            pauseResumePluginButton.setEnabled(false);
            pauseResumePluginButton.setToolTipText("Cannot pause/resume plugin when scheduler is not running");
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
            // Update name with pause indicator if needed
            String displayName = currentPlugin.getCleanName();
            String pauseTooltip = null;
            
            if (PluginPauseEvent.isPaused()) {
                displayName += " [PAUSED]";
                currentPluginNameLabel.setForeground(new Color(255, 152, 0)); // Orange
                
                // Create detailed pause tooltip
                pauseTooltip = createPauseTooltipForCurrentPlugin(currentPlugin);
            } else {
                currentPluginNameLabel.setForeground(Color.WHITE);
                
                // Check if any other plugins are paused and create tooltip
                pauseTooltip = createPauseTooltipForAllPlugins();
            }
            
            currentPluginNameLabel.setText(displayName);
            currentPluginNameLabel.setToolTipText(pauseTooltip);
            
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
            // No current plugin - check for paused plugins and show in tooltip
            String noneText = "None";
            String noneTooltip = null;
            
            if (plugin.anyPluginEntryPaused()) {
                List<PluginScheduleEntry> pausedPlugins = plugin.getScheduledPlugins().stream()
                    .filter(PluginScheduleEntry::isPaused)
                    .collect(Collectors.toList());
                    
                if (!pausedPlugins.isEmpty()) {
                    noneText = "None (" + pausedPlugins.size() + " paused)";
                    noneTooltip = createPauseTooltipForAllPlugins();
                }
            }
            
            // Reset all fields with pause information
            currentPluginNameLabel.setText(noneText);
            currentPluginNameLabel.setToolTipText(noneTooltip);
            currentPluginNameLabel.setForeground(plugin.anyPluginEntryPaused() ? new Color(255, 152, 0) : Color.WHITE);
            currentPluginRuntimeLabel.setText("00:00:00");
            stopConditionStatusLabel.setText("None");
            stopConditionProgressBar.setValue(0);
            stopConditionProgressBar.setString("No conditions");
            currentPluginStartTime = null;
        }
    }
    
    /**
     * Updates information about the next scheduled plugin
     */
    private void updateNextUpComingPluginInfo() {
        PluginScheduleEntry nextUpComingPlugin = plugin.getUpComingPlugin();
        
        if (nextUpComingPlugin != null) {                        
            // Update name
            nextUpComingPluginNameLabel.setText(nextUpComingPlugin.getCleanName());
            
            // Set the next run time display (already handles various condition types)
            nextUpComingPluginTimeLabel.setText(nextUpComingPlugin.getNextRunDisplay());
            
            // Create an enhanced schedule description
            StringBuilder scheduleDesc = new StringBuilder(nextUpComingPlugin.getIntervalDisplay());
            
            // Add information about one-time schedules
            if (nextUpComingPlugin.hasAnyOneTimeStartConditions()) {
                if (nextUpComingPlugin.hasTriggeredOneTimeStartConditions() && !nextUpComingPlugin.canStartTriggerAgain()) {
                    scheduleDesc.append(" (Completed)");
                } else {
                    scheduleDesc.append(" (One-time)");
                }
            }
            
            nextUpComingPluginScheduleLabel.setText(scheduleDesc.toString());
        } else {
            // Reset all fields
            nextUpComingPluginNameLabel.setText("None");
            nextUpComingPluginTimeLabel.setText("--:--");
            nextUpComingPluginScheduleLabel.setText("None");
        }
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
                // Define colors for different states
                Color successColor = new Color(76, 175, 80); // Green for success
                Color unsuccessfulColor = new Color(255, 152, 0); // Orange for unsuccessful
                Color errorColor = new Color(244, 67, 54); // Red for error
                Color defaultColor = Color.WHITE; // Default color
                
                // Determine message color based on stop reason type and success state
                Color messageColor = defaultColor;
                
                if (stopReasonType != null) {
                    switch (stopReasonType) {
                        case PLUGIN_FINISHED:
                            messageColor = lastPlugin.isLastRunSuccessful() ? successColor : unsuccessfulColor;
                            break;
                        case ERROR:
                            messageColor = errorColor;
                            break;
                        case INTERRUPTED:
                            messageColor = unsuccessfulColor;
                            break;
                        default:
                            messageColor = defaultColor;
                            break;
                    }
                }
                
                // Set the text and color for the status text area
                prevPluginStatusLabel.setText(stopReason);
                prevPluginStatusLabel.setForeground(messageColor);
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
            prevPluginStatusLabel.setForeground(Color.WHITE);
            prevPluginStopTimeLabel.setText("--:--:--");
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
    
    /**
     * Updates only the time display for the next plugin without full refresh.
     * This is used for regular time updates when the plugin hasn't changed.
     * 
     * @param nextUpComingPlugin The next scheduled plugin
     */
    private void updateNextUpComingPluginTimeDisplay(PluginScheduleEntry nextUpComingPlugin) {
        if (nextUpComingPlugin != null) {
            // Only update the time display, keep other fields unchanged            
            nextUpComingPluginTimeLabel.setText(nextUpComingPlugin.getNextRunDisplay());
        }
    }
    
    /**
     * Updates only the runtime display for the current plugin without refreshing other info.
     * This is used for regular runtime updates when the plugin hasn't changed.
     */
    private void updateCurrentPluginRuntimeOnly() {
        PluginScheduleEntry currentPlugin = plugin.getCurrentPlugin();
        
        if (currentPlugin != null && currentPlugin.isRunning()) {
            // Update runtime
            if (currentPluginStartTime != null) {
                Duration runtime = Duration.between(currentPluginStartTime, ZonedDateTime.now());
                long totalSeconds = runtime.getSeconds();
                long hours = totalSeconds / 3600;
                long minutes = (totalSeconds % 3600) / 60;
                long seconds = totalSeconds % 60;
                
                currentPluginRuntimeLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
            }
            
            // Update stop condition progress (in case conditions have progressed)
            if (currentPlugin.hasAnyStopConditions()) {
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
            }
        }
    }
    
    /**
     * Creates an alternative tabbed layout for extremely small spaces
     * This method provides a fallback when the regular layout doesn't fit
     * @deprecated This method is no longer used. Use UIUtils methods instead.
     */
    @Deprecated
    @SuppressWarnings("unused")
    private JPanel createTabbedPluginInfoPanelDeprecated() {
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
            ),
            "Plugin Information",
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            FontManager.getRunescapeBoldFont(),
            Color.WHITE
        ));
        wrapperPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Create tabbed pane for very small spaces
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tabbedPane.setForeground(Color.WHITE);
        tabbedPane.setFont(FontManager.getRunescapeSmallFont());

        // Previous Plugin Tab
        JPanel prevTab = new JPanel(new GridBagLayout());
        prevTab.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        GridBagConstraints gbc = UIUtils.createGbc(0, 0);
        
        prevTab.add(new JLabel("Name:"), gbc);
        gbc.gridx++;
        prevPluginNameLabel = UIUtils.createCompactValueLabel("None");
        prevTab.add(prevPluginNameLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy++;
        prevTab.add(new JLabel("Duration:"), gbc);
        gbc.gridx++;
        prevPluginDurationLabel = UIUtils.createCompactValueLabel("00:00:00");
        prevTab.add(prevPluginDurationLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy++;
        prevTab.add(new JLabel("Stop Time:"), gbc);
        gbc.gridx++;
        prevPluginStopTimeLabel = UIUtils.createCompactValueLabel("--:--:--");
        prevTab.add(prevPluginStopTimeLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy++;
        gbc.gridwidth = 2;
        prevPluginStatusLabel = createMultiLineTextArea("None");
        prevPluginStatusLabel.setPreferredSize(new Dimension(0, 40));
        prevTab.add(prevPluginStatusLabel, gbc);

        // Current Plugin Tab
        JPanel currentTab = new JPanel(new GridBagLayout());
        currentTab.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        gbc = UIUtils.createGbc(0, 0);
        
        currentTab.add(new JLabel("Name:"), gbc);
        gbc.gridx++;
        currentPluginNameLabel = UIUtils.createCompactValueLabel("None");
        currentTab.add(currentPluginNameLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy++;
        currentTab.add(new JLabel("Runtime:"), gbc);
        gbc.gridx++;
        currentPluginRuntimeLabel = UIUtils.createCompactValueLabel("00:00:00");
        currentTab.add(currentPluginRuntimeLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy++;
        currentTab.add(new JLabel("Conditions:"), gbc);
        gbc.gridx++;
        stopConditionStatusLabel = UIUtils.createCompactValueLabel("None");
        currentTab.add(stopConditionStatusLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy++;
        gbc.gridwidth = 2;
        stopConditionProgressBar = new JProgressBar(0, 100);
        stopConditionProgressBar.setStringPainted(true);
        stopConditionProgressBar.setString("No conditions");
        stopConditionProgressBar.setForeground(new Color(76, 175, 80));
        stopConditionProgressBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        stopConditionProgressBar.setPreferredSize(new Dimension(0, 8));
        currentTab.add(stopConditionProgressBar, gbc);

        // Next Plugin Tab
        JPanel nextTab = new JPanel(new GridBagLayout());
        nextTab.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        gbc = UIUtils.createGbc(0, 0);
        
        nextTab.add(new JLabel("Name:"), gbc);
        gbc.gridx++;
        nextUpComingPluginNameLabel = UIUtils.createCompactValueLabel("None");
        nextTab.add(nextUpComingPluginNameLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy++;
        nextTab.add(new JLabel("Time:"), gbc);
        gbc.gridx++;
        nextUpComingPluginTimeLabel = UIUtils.createCompactValueLabel("--:--");
        nextTab.add(nextUpComingPluginTimeLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy++;
        nextTab.add(new JLabel("Type:"), gbc);
        gbc.gridx++;
        nextUpComingPluginScheduleLabel = UIUtils.createCompactValueLabel("None");
        nextTab.add(nextUpComingPluginScheduleLabel, gbc);

        tabbedPane.addTab("Prev", prevTab);
        tabbedPane.addTab("Current", currentTab);
        tabbedPane.addTab("Next", nextTab);

        wrapperPanel.add(tabbedPane, BorderLayout.CENTER);

        return wrapperPanel;
    }
    
    /**
     * Creates a structured plugin info panel using BoxLayout for better vertical control
     * This is an alternative to the FlowLayout approach if height issues persist
     * @deprecated This method is no longer used. Use UIUtils methods instead.
     */
    @Deprecated
    @SuppressWarnings("unused")
    private JPanel createStructuredPluginInfoPanelDeprecated() {
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ),
            "Plugin Information",
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            FontManager.getRunescapeBoldFont(),
            Color.WHITE
        ));
        wrapperPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Create main content panel with BoxLayout for better vertical control
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Create sections panel using a more structured approach
        JPanel sectionsPanel = new JPanel(new BorderLayout());
        sectionsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Create individual section panels with fixed heights
        JPanel prevSection = createStructuredSection("Previous");
        JPanel currentSection = createStructuredSection("Current");
        JPanel nextSection = createStructuredSection("Next");

        // Add content to sections
        addPreviousPluginContent(prevSection);
        addCurrentPluginContent(currentSection);
        addNextPluginContent(nextSection);

        // Use a horizontal layout with equal weights
        sectionsPanel.add(prevSection, BorderLayout.WEST);
        sectionsPanel.add(currentSection, BorderLayout.CENTER);
        sectionsPanel.add(nextSection, BorderLayout.EAST);

        contentPanel.add(sectionsPanel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Add progress panel
        JPanel progressPanel = createProgressPanel();
        contentPanel.add(progressPanel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Add stop reason panel
        JPanel stopReasonPanel = createStopReasonPanel();
        contentPanel.add(stopReasonPanel);

        // Wrap in scroll pane
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);

        wrapperPanel.add(scrollPane, BorderLayout.CENTER);
        return wrapperPanel;
    }

    /**
     * Creates a structured section with fixed height and proper spacing
     */
    private JPanel createStructuredSection(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(8, 6, 8, 6)
            ),
            title,
            TitledBorder.CENTER,
            TitledBorder.TOP,
            FontManager.getRunescapeSmallFont(),
            Color.LIGHT_GRAY
        ));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setPreferredSize(new Dimension(140, 180)); // Fixed height
        panel.setMinimumSize(new Dimension(120, 160));
        panel.setMaximumSize(new Dimension(160, 200));
        return panel;
    }

    /**
     * Adds content to the previous plugin section
     */
    private void addPreviousPluginContent(JPanel section) {
        section.add(UIUtils.createLabelValueRow("Name:", prevPluginNameLabel = UIUtils.createCompactValueLabel("None")));
        section.add(Box.createRigidArea(new Dimension(0, 5)));
        section.add(UIUtils.createLabelValueRow("Duration:", prevPluginDurationLabel = UIUtils.createCompactValueLabel("00:00:00")));
        section.add(Box.createRigidArea(new Dimension(0, 5)));
        section.add(UIUtils.createLabelValueRow("Stop Time:", prevPluginStopTimeLabel = UIUtils.createCompactValueLabel("--:--:--")));
        section.add(Box.createVerticalGlue());
    }

    /**
     * Adds content to the current plugin section
     */
    private void addCurrentPluginContent(JPanel section) {
        section.add(UIUtils.createLabelValueRow("Name:", currentPluginNameLabel = UIUtils.createCompactValueLabel("None")));
        section.add(Box.createRigidArea(new Dimension(0, 5)));
        section.add(UIUtils.createLabelValueRow("Runtime:", currentPluginRuntimeLabel = UIUtils.createCompactValueLabel("00:00:00")));
        section.add(Box.createRigidArea(new Dimension(0, 5)));
        section.add(UIUtils.createLabelValueRow("Conditions:", stopConditionStatusLabel = UIUtils.createCompactValueLabel("None")));
        section.add(Box.createVerticalGlue());
    }

    /**
     * Adds content to the next plugin section
     */
    private void addNextPluginContent(JPanel section) {
        section.add(UIUtils.createLabelValueRow("Name:", nextUpComingPluginNameLabel = UIUtils.createCompactValueLabel("None")));
        section.add(Box.createRigidArea(new Dimension(0, 5)));
        section.add(UIUtils.createLabelValueRow("Time:", nextUpComingPluginTimeLabel = UIUtils.createCompactValueLabel("--:--")));
        section.add(Box.createRigidArea(new Dimension(0, 5)));
        section.add(UIUtils.createLabelValueRow("Type:", nextUpComingPluginScheduleLabel = UIUtils.createCompactValueLabel("None")));
        section.add(Box.createVerticalGlue());
    }

    /**
     * Creates the progress panel
     */
    private JPanel createProgressPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(2, 0, 2, 0));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        
        JLabel label = new JLabel("Progress:");
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeSmallFont());
        
        stopConditionProgressBar = new JProgressBar(0, 100);
        stopConditionProgressBar.setStringPainted(true);
        stopConditionProgressBar.setString("No conditions");
        stopConditionProgressBar.setForeground(new Color(76, 175, 80));
        stopConditionProgressBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        stopConditionProgressBar.setPreferredSize(new Dimension(0, 12));
        
        panel.add(label, BorderLayout.WEST);
        panel.add(stopConditionProgressBar, BorderLayout.CENTER);
        
        return panel;
    }

    /**
     * Creates the stop reason panel
     */
    private JPanel createStopReasonPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(2, 0, 0, 0));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        
        JLabel label = new JLabel("Stop Reason:");
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeSmallFont());
        
        prevPluginStatusLabel = createMultiLineTextArea("None");
        prevPluginStatusLabel.setPreferredSize(new Dimension(0, 40));
        
        panel.add(label, BorderLayout.WEST);
        panel.add(prevPluginStatusLabel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Creates a detailed tooltip for the current plugin pause status
     */
    private String createPauseTooltipForCurrentPlugin(PluginScheduleEntry currentPlugin) {
        StringBuilder tooltip = new StringBuilder("<html>");
        tooltip.append("<b>Current Plugin Paused:</b><br/>");
        tooltip.append("Name: ").append(currentPlugin.getName()).append("<br/>");
        tooltip.append("Priority: ").append(currentPlugin.getPriority()).append("<br/>");
        tooltip.append("Enabled: ").append(currentPlugin.isEnabled() ? "Yes" : "No").append("<br/>");
        tooltip.append("Running: ").append(currentPlugin.isRunning() ? "Yes" : "No").append("<br/>");
        
        if (currentPlugin.getLastRunStartTime() != null) {
            Duration runtime = Duration.between(currentPlugin.getLastRunStartTime(), ZonedDateTime.now());
            tooltip.append("Runtime: ").append(formatDurationForTooltip(runtime)).append("<br/>");
        }
        
        // Check for other paused plugins
        List<PluginScheduleEntry> pausedPlugins = plugin.getScheduledPlugins().stream()
                .filter(p -> p.isPaused() && !p.equals(currentPlugin))
                .collect(Collectors.toList());
                
        if (!pausedPlugins.isEmpty()) {
            tooltip.append("<br/><b>Other Paused Plugins:</b><br/>");
            for (PluginScheduleEntry pausedPlugin : pausedPlugins) {
                tooltip.append("• ").append(pausedPlugin.getName())
                        .append(" (Priority: ").append(pausedPlugin.getPriority()).append(")<br/>");
            }
        }
        
        tooltip.append("</html>");
        return tooltip.toString();
    }
    
    /**
     * Creates a tooltip showing all paused plugins when no plugin is currently running paused
     */
    private String createPauseTooltipForAllPlugins() {
        List<PluginScheduleEntry> pausedPlugins = plugin.getScheduledPlugins().stream()
                .filter(PluginScheduleEntry::isPaused)
                .collect(Collectors.toList());
                
        if (pausedPlugins.isEmpty()) {
            return null; // No tooltip needed
        }
        
        StringBuilder tooltip = new StringBuilder("<html>");
        
        if (plugin.isPaused()) {
            tooltip.append("<b>Scheduler Paused</b><br/>");
        }
        
        if (plugin.anyPluginEntryPaused()) {
            tooltip.append("<b>Paused Plugins (").append(pausedPlugins.size()).append("):</b><br/>");
            for (PluginScheduleEntry pausedPlugin : pausedPlugins) {
                tooltip.append("• ").append(pausedPlugin.getName())
                        .append(" (Priority: ").append(pausedPlugin.getPriority())
                        .append(", Enabled: ").append(pausedPlugin.isEnabled() ? "Yes" : "No")
                        .append(")<br/>");
            }
        }
        
        tooltip.append("</html>");
        return tooltip.toString();
    }
    
    /**
     * Formats duration for tooltip display
     */
    private String formatDurationForTooltip(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "00:00:00";
        }
        
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}