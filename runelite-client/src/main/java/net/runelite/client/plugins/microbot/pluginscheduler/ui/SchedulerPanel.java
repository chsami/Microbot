package net.runelite.client.plugins.microbot.pluginscheduler.ui;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerState;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.util.events.PluginPauseEvent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SchedulerPanel extends PluginPanel {
    private final SchedulerPlugin plugin;

    // Current plugin section
    private final JLabel currentPluginLabel;
    private final JLabel runtimeLabel;

    // Previous plugin section
    private final JLabel prevPluginNameLabel;
    private final JLabel prevPluginDurationLabel;
    private final JLabel prevPluginStopReasonLabel;
    private final JLabel prevPluginStopTimeLabel;

    // Next plugin section
    private final JLabel nextUpComingPluginNameLabel;
    private final JLabel nextUpComingPluginTimeLabel;
    private final JLabel nextUpComingPluginScheduleLabel;

    // Scheduler status section
    private final JLabel schedulerStatusLabel;
    // Control buttons
    private final JButton configButton;
    private final JButton runButton;
    private final JButton stopButton;
    private final JButton pauseSchedulerButton; 
    private final JButton pauseResumePluginButton; 
    private final JButton antibanButton; 

    // State tracking for optimized updates
    private PluginScheduleEntry lastTrackedCurrentPlugin;
    private PluginScheduleEntry lastTrackedNextUpComingPlugin;
    private SchedulerState lastTrackedState; 


    public SchedulerPanel(SchedulerPlugin plugin) {
        super(false);
        this.plugin = plugin;

        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Create main panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Current plugin info panel
        JPanel infoPanel = createInfoPanel("Current Plugin");

        JLabel pluginLabel = new JLabel("Plugin:");
        pluginLabel.setForeground(Color.WHITE);
        pluginLabel.setFont(FontManager.getRunescapeFont());
        infoPanel.add(pluginLabel, createGbc(0, 0));

        currentPluginLabel = createValueLabel("None");
        infoPanel.add(currentPluginLabel, createGbc(1, 0));

        JLabel runtimeTitleLabel = new JLabel("Runtime:");
        runtimeTitleLabel.setForeground(Color.WHITE);
        runtimeTitleLabel.setFont(FontManager.getRunescapeFont());
        infoPanel.add(runtimeTitleLabel, createGbc(0, 1));

        runtimeLabel = createValueLabel("00:00:00");
        infoPanel.add(runtimeLabel, createGbc(1, 1));

        // Previous plugin info panel
        JPanel prevPluginPanel = createInfoPanel("Previous Plugin");
        JLabel prevPluginTitleLabel = new JLabel("Plugin:");
        prevPluginTitleLabel.setForeground(Color.WHITE);
        prevPluginTitleLabel.setFont(FontManager.getRunescapeFont());
        prevPluginPanel.add(prevPluginTitleLabel, createGbc(0, 0));

        prevPluginNameLabel = createValueLabel("None");
        prevPluginPanel.add(prevPluginNameLabel, createGbc(1, 0));

        JLabel prevDurationLabel = new JLabel("Duration:");
        prevDurationLabel.setForeground(Color.WHITE);
        prevDurationLabel.setFont(FontManager.getRunescapeFont());
        prevPluginPanel.add(prevDurationLabel, createGbc(0, 1));

        prevPluginDurationLabel = createValueLabel("00:00:00");
        prevPluginPanel.add(prevPluginDurationLabel, createGbc(1, 1));

        JLabel prevStopReasonLabel = new JLabel("Stop Reason:");
        prevStopReasonLabel.setForeground(Color.WHITE);
        prevStopReasonLabel.setFont(FontManager.getRunescapeFont());
        prevPluginPanel.add(prevStopReasonLabel, createGbc(0, 2));

        prevPluginStopReasonLabel = createValueLabel("None");
        prevPluginPanel.add(prevPluginStopReasonLabel, createGbc(1, 2));

        JLabel prevStopTimeLabel = new JLabel("Stop Time:");
        prevStopTimeLabel.setForeground(Color.WHITE);
        prevStopTimeLabel.setFont(FontManager.getRunescapeFont());
        prevPluginPanel.add(prevStopTimeLabel, createGbc(0, 3));

        prevPluginStopTimeLabel = createValueLabel("--:--");
        prevPluginPanel.add(prevPluginStopTimeLabel, createGbc(1, 3));

        // Next plugin info panel
        JPanel nextUpComingPluginPanel = createInfoPanel("Next Scheduled Plugin");
        JLabel nextUpComingPluginTitleLabel = new JLabel("Plugin:");
        nextUpComingPluginTitleLabel.setForeground(Color.WHITE);
        nextUpComingPluginTitleLabel.setFont(FontManager.getRunescapeFont());
        nextUpComingPluginPanel.add(nextUpComingPluginTitleLabel, createGbc(0, 0));

        nextUpComingPluginNameLabel = createValueLabel("None");
        nextUpComingPluginPanel.add(nextUpComingPluginNameLabel, createGbc(1, 0));

        JLabel nextRunLabel = new JLabel("Next Run:");
        nextRunLabel.setForeground(Color.WHITE);
        nextRunLabel.setFont(FontManager.getRunescapeFont());
        nextUpComingPluginPanel.add(nextRunLabel, createGbc(0, 1));

        nextUpComingPluginTimeLabel = createValueLabel("--:--");
        nextUpComingPluginPanel.add(nextUpComingPluginTimeLabel, createGbc(1, 1));

        JLabel scheduleLabel = new JLabel("Schedule:");
        scheduleLabel.setForeground(Color.WHITE);
        scheduleLabel.setFont(FontManager.getRunescapeFont());
        nextUpComingPluginPanel.add(scheduleLabel, createGbc(0, 2));

        nextUpComingPluginScheduleLabel = createValueLabel("None");
        nextUpComingPluginPanel.add(nextUpComingPluginScheduleLabel, createGbc(1, 2));

        // Scheduler status panel
        JPanel statusPanel = createInfoPanel("Scheduler Status");
        JLabel statusLabel = new JLabel("Status:");
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(FontManager.getRunescapeFont());
        statusPanel.add(statusLabel, createGbc(0, 0));

        schedulerStatusLabel = createValueLabel("Inactive");
        schedulerStatusLabel.setForeground(Color.YELLOW);
        statusPanel.add(schedulerStatusLabel, createGbc(1, 0));

        // Button panel - vertical layout (one button per row)
        JPanel buttonPanel = new JPanel(new GridLayout(6, 1, 0, 5)); // Changed to 6 rows for all buttons
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Add config button
        JButton configButton = createButton("Open Scheduler");
        configButton.addActionListener(this::onOpenConfigButtonClicked);
        this.configButton = configButton;
        
        // Control buttons
        Color greenColor = new Color(76, 175, 80);
        JButton runButton = createButton("Run Scheduler", greenColor);
        runButton.addActionListener(e -> {
            plugin.startScheduler();
            refresh();
        });
        this.runButton = runButton;

        Color redColor = new Color(244, 67, 54);
        JButton stopButton = createButton("Stop Scheduler", redColor);
        stopButton.addActionListener(e -> {
            plugin.stopScheduler();
            refresh();
        });
        this.stopButton = stopButton;

        // Add Antiban button - uses a distinct purple color
        Color purpleColor = new Color(156, 39, 176);
        JButton antibanButton = createButton("Antiban Settings", purpleColor);
        antibanButton.addActionListener(this::onAntibanButtonClicked);
        antibanButton.setToolTipText("Open Antiban settings in a separate window");
        this.antibanButton = antibanButton;
 

        // Add pause/resume button - uses orange color
        Color orangeColor = new Color(255, 152, 0);
        JButton pauseSchedulerButton = createButton("Pause Scheduler", orangeColor);
        pauseSchedulerButton.addActionListener(e -> {
            if (plugin.isPaused()) {
                plugin.resumeScheduler();
                pauseSchedulerButton.setText("Pause Scheduler");
                pauseSchedulerButton.setBackground(orangeColor);
            } else {
                plugin.pauseScheduler();
                pauseSchedulerButton.setText("Resume Scheduler");
                pauseSchedulerButton.setBackground(greenColor);
            }
            refresh();
        });
        pauseSchedulerButton.setToolTipText("Pause or resume the scheduler without stopping it");
        this.pauseSchedulerButton = pauseSchedulerButton;

        // Add pause/resume button for the currently running plugin - use cyan color
        Color cyanColor = new Color(0, 188, 212); // Material design cyan color
        JButton pauseResumePluginButton = createButton("Pause Plugin", cyanColor);
        pauseResumePluginButton.addActionListener(e -> {
            // Toggle the pause state
            boolean newPauseState = !PluginPauseEvent.isPaused();
            PluginPauseEvent.setPaused(newPauseState);
            
            // Update button text and color based on state
            if (newPauseState) {
                plugin.pauseRunningPlugin();
                pauseResumePluginButton.setText("Resume Plugin");
                pauseResumePluginButton.setBackground(greenColor); // Change to green for resume
            } else {
                plugin.resumeRunningPlugin();
                pauseResumePluginButton.setText("Pause Plugin");
                pauseResumePluginButton.setBackground(cyanColor); // Change back to cyan for pause
            }
            refresh();
        });
        pauseResumePluginButton.setToolTipText("Pause or resume the currently running plugin");
        this.pauseResumePluginButton = pauseResumePluginButton;

        buttonPanel.add(configButton);
        buttonPanel.add(runButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(pauseSchedulerButton); 
        buttonPanel.add(pauseResumePluginButton); 
        buttonPanel.add(antibanButton);
        

        // Add components to main panel
        mainPanel.add(infoPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        mainPanel.add(prevPluginPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        mainPanel.add(nextUpComingPluginPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        mainPanel.add(statusPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        mainPanel.add(buttonPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        // Wrap main panel in scroll pane for better fit in different sidebar sizes
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Set the preferred width to maintain proper sidebar display
        scrollPane.setPreferredSize(new Dimension(200, 400));
        
        add(scrollPane, BorderLayout.CENTER); // Changed from NORTH to CENTER for proper filling
        refresh();
    }

    private GridBagConstraints createGbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = 1;
        gbc.gridheight = 1;
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.anchor = (x == 0) ? GridBagConstraints.WEST : GridBagConstraints.EAST;
        gbc.fill = (x == 0) ? GridBagConstraints.BOTH
                : GridBagConstraints.HORIZONTAL;

        gbc.weightx = (x == 0) ? 0.1 : 1.0;
        gbc.weighty = 1.0;
        return gbc;
    }

    private JPanel createInfoPanel(String title) {
        JPanel panel = new JPanel(new GridBagLayout());

        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.MEDIUM_GRAY_COLOR),
                        BorderFactory.createEmptyBorder(5, 5, 5, 5)
                ),
                title,
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                FontManager.getRunescapeBoldFont(),
                Color.WHITE
        ));
        return panel;
    }

    private JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeFont());
        return label;
    }

    private JButton createButton(String text) {
        return createButton(text, ColorScheme.BRAND_ORANGE);
    }

    private JButton createButton(String text, Color backgroundColor) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(backgroundColor);
        button.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(backgroundColor.darker(), 1),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));

        // Add hover effect that maintains the button's color theme
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(backgroundColor.brighter());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(backgroundColor);
            }
        });

        return button;
    }

    public void refresh() {
        // Get current state information
        SchedulerState currentState = plugin.getCurrentState();
        PluginScheduleEntry currentPlugin = plugin.getCurrentPlugin();
        PluginScheduleEntry nextUpComingPlugin = plugin.getUpComingPlugin();
        
        // Update current plugin info if it changed
        if (currentPlugin != lastTrackedCurrentPlugin) {
            updatePluginInfo();
            lastTrackedCurrentPlugin = currentPlugin;
        } else if (currentPlugin != null && currentPlugin.isRunning()) {
            // Update only the runtime display without full refresh when plugin is running
            updateCurrentPluginRuntimeOnly();
        }
        
        // Update next plugin info if it changed
        if (nextUpComingPlugin != lastTrackedNextUpComingPlugin) {
            updateNextPluginInfo();
            lastTrackedNextUpComingPlugin = nextUpComingPlugin;
        } else if (nextUpComingPlugin != null && nextUpComingPlugin.isEnabled()) {
            // Update only the time display without full refresh for real-time countdown
            updateNextPluginTimeDisplayOnly(nextUpComingPlugin);
        }
        
        // Update scheduler status and buttons if state changed
        if (currentState != lastTrackedState) {
            updateButtonStates();
            
            // Update scheduler status with pause information
            String statusText = currentState.getDisplayName();
            String statusTooltip = currentState.getDescription();
            
            // Add pause indicator to status if any plugins are paused
            if (plugin.anyPluginEntryPaused()) {
                List<PluginScheduleEntry> pausedPlugins = plugin.getScheduledPlugins().stream()
                    .filter(PluginScheduleEntry::isPaused)
                    .collect(Collectors.toList());
                    
                if (!pausedPlugins.isEmpty()) {
                    statusText += " (" + pausedPlugins.size() + " paused)";
                    statusTooltip = createAllPausedPluginsTooltip();
                }
            }
            
            schedulerStatusLabel.setText(statusText);
            schedulerStatusLabel.setForeground(currentState.getColor());
            schedulerStatusLabel.setToolTipText(statusTooltip);
            
            lastTrackedState = currentState;
        }
    }
      
    /**
     * Updates button states based on plugin initialization status
     */
    private void updateButtonStates() {
        
        
        SchedulerState state = plugin.getCurrentState();
        boolean schedulerActive = plugin.getCurrentState().isSchedulerActive();
        boolean pluginRunning = plugin.getCurrentState().isPluginRunning();
        configButton.setEnabled(state != SchedulerState.UNINITIALIZED || state != SchedulerState.ERROR || state != SchedulerState.INITIALIZING);
        
        // Only enable run button if we're in READY or HOLD state
        runButton.setEnabled(!schedulerActive && (state != SchedulerState.UNINITIALIZED || state != SchedulerState.ERROR || state != SchedulerState.INITIALIZING));
        
        // Only enable stop button in certain states
        stopButton.setEnabled(schedulerActive);
        
        // Update pause scheduler button state
        pauseSchedulerButton.setEnabled(schedulerActive|| state == SchedulerState.SCHEDULER_PAUSED);
        if (plugin.isPaused()) {
            pauseSchedulerButton.setText("Resume Scheduler");
            pauseSchedulerButton.setBackground(new Color(76, 175, 80)); // Green color
            pauseSchedulerButton.setToolTipText("Resume the paused scheduler");
        } else {
            pauseSchedulerButton.setText("Pause Scheduler");
            pauseSchedulerButton.setBackground(new Color(255, 152, 0)); // Orange color
            pauseSchedulerButton.setToolTipText("Pause the scheduler without stopping it");
        }
        
        // Update pause plugin button state - only visible and enabled when a plugin is running
        boolean pluginCanBePaused = state == SchedulerState.RUNNING_PLUGIN || 
                                    state == SchedulerState.RUNNING_PLUGIN_PAUSED;
        pauseResumePluginButton.setVisible(pluginCanBePaused);
        pauseResumePluginButton.setEnabled(pluginCanBePaused);
        
        // Update button text and color based on plugin pause state
        if (PluginPauseEvent.isPaused()) {
            pauseResumePluginButton.setText("Resume Plugin");
            pauseResumePluginButton.setBackground(new Color(76, 175, 80)); // Green color
            pauseResumePluginButton.setToolTipText("Resume the paused plugin");
        } else {
            pauseResumePluginButton.setText("Pause Plugin");
            pauseResumePluginButton.setBackground(new Color(0, 188, 212)); // Cyan color
            pauseResumePluginButton.setToolTipText("Pause the currently running plugin");
        }
        
        // Only enable antiban button when no plugin is running
        antibanButton.setEnabled(!pluginRunning);
    
        
        
        // Add tooltips
        if (state == SchedulerState.UNINITIALIZED || state == SchedulerState.ERROR || state == SchedulerState.INITIALIZING) {
            configButton.setToolTipText("Plugin not initialized yet");
            runButton.setToolTipText("Plugin not initialized yet");
            stopButton.setToolTipText("Plugin not initialized yet");
            pauseSchedulerButton.setToolTipText("Plugin not initialized yet");
        } else {
            configButton.setToolTipText("Open scheduler configuration");
            runButton.setToolTipText(!runButton.isEnabled() ? 
                "Cannot start scheduler in " + state.getDisplayName() + " state" : 
                "Start the scheduler");
            stopButton.setToolTipText(!stopButton.isEnabled() ? 
                "Cannot stop scheduler: not running" : 
                "Stop the scheduler");
        }
        

    }

    void updatePluginInfo() {
        PluginScheduleEntry currentPlugin = plugin.getCurrentPlugin();

        if (currentPlugin != null) {
            // Get start time for runtime calculation
            ZonedDateTime startTimeZdt = currentPlugin.getLastRunStartTime();
            String pluginName = currentPlugin.getCleanName();
            
            // Add pause indicator if current plugin is paused
            if (currentPlugin.isRunning() && PluginPauseEvent.isPaused()) {
                pluginName += " [PAUSED]";
                currentPluginLabel.setForeground(new Color(255, 152, 0)); // Orange
            } else {
                currentPluginLabel.setForeground(Color.WHITE);
            }
            
            // Add the stop reason indicator to the plugin name if available
            if (currentPlugin.getLastStopReasonType() != null && 
                currentPlugin.getLastStopReasonType() != PluginScheduleEntry.StopReason.NONE) {
                String stopReason = formatStopReason(currentPlugin.getLastStopReasonType());
                pluginName += " (" + stopReason + ")";
            }
            
            currentPluginLabel.setText(pluginName);
            
            // Create and set tooltip with pause information
            String pauseTooltip = createPauseTooltipForCurrentPlugin(currentPlugin);
            currentPluginLabel.setToolTipText(pauseTooltip);
            
            // Show runtime - either current or last run duration
            if (currentPlugin.isRunning()) {
                // Calculate and display current runtime for active plugin
                if (startTimeZdt != null) {
                    long startTimeMillis = startTimeZdt.toInstant().toEpochMilli();
                    long runtimeMillis = System.currentTimeMillis() - startTimeMillis;
                    runtimeLabel.setText(formatDuration(runtimeMillis));
                } else {
                    runtimeLabel.setText("Running");
                }
            } else if (currentPlugin.getLastRunDuration() != null && !currentPlugin.getLastRunDuration().isZero()) {
                // Show the stored last run duration for completed plugins
                runtimeLabel.setText(formatDuration(currentPlugin.getLastRunDuration().toMillis()));
            } else {
                runtimeLabel.setText("Not started");
            }
            
            // Update previous plugin information if it has run at least once
            updatePreviousPluginInfo(currentPlugin);
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
                    noneTooltip = createAllPausedPluginsTooltip();
                }
            }
            
            currentPluginLabel.setText(noneText);
            currentPluginLabel.setToolTipText(noneTooltip);
            currentPluginLabel.setForeground(plugin.anyPluginEntryPaused() ? new Color(255, 152, 0) : Color.WHITE);
            runtimeLabel.setText("00:00:00");
            
            // Clear previous plugin info when there's no current plugin
            prevPluginNameLabel.setText("None");
            prevPluginDurationLabel.setText("00:00:00");
            prevPluginStopReasonLabel.setText("None");
            prevPluginStopTimeLabel.setText("--:--");
        }
    }
    
    /**
     * Updates information about the previously run plugin
     */
    private void updatePreviousPluginInfo(PluginScheduleEntry plugin) {
        if (plugin == null) return;
        
        // Only show previous plugin info if the plugin has been run at least once
        if (plugin.getLastRunEndTime() != null && plugin.getLastRunDuration() != null) {
            // Set name
            prevPluginNameLabel.setText(plugin.getCleanName());
            
            // Set duration
            long durationMillis = plugin.getLastRunDuration().toMillis();
            prevPluginDurationLabel.setText(formatDuration(durationMillis));
            
            // Set stop reason
            String stopReason = "None";
            if (plugin.getLastStopReasonType() != null && 
                plugin.getLastStopReasonType() != PluginScheduleEntry.StopReason.NONE) {
                stopReason = formatStopReason(plugin.getLastStopReasonType());
            }
            prevPluginStopReasonLabel.setText(stopReason);
            
            // Set stop time
            ZonedDateTime endTime = plugin.getLastRunEndTime();
            if (endTime != null) {
                prevPluginStopTimeLabel.setText(
                    endTime.format(PluginScheduleEntry.TIME_FORMATTER)
                );
            } else {
                prevPluginStopTimeLabel.setText("--:--");
            }
        }
    }
    
    /**
     * Formats a duration in milliseconds as HH:MM:SS
     */
    private String formatDuration(long durationMillis) {
        long hours = TimeUnit.MILLISECONDS.toHours(durationMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    /**
     * Returns a formatted stop reason
     */
    private String formatStopReason(PluginScheduleEntry.StopReason stopReason) {
        // Use the description from the enum if available
        if (stopReason != null) {
            switch (stopReason) {
                case MANUAL_STOP:
                    return "Stopped";
                case PLUGIN_FINISHED:
                    return "Completed";
                case ERROR:
                    return "Error";
                case SCHEDULED_STOP:
                    return "Timed out";
                case INTERRUPTED:
                    return "Interrupted";
                case HARD_STOP:
                    return "Force stopped";
                default:
                    return stopReason.getDescription();
            }
        }
        return "";
    }
    
    void updateNextPluginInfo() {
        PluginScheduleEntry nextUpComingPlugin = plugin.getUpComingPlugin();

        if (nextUpComingPlugin != null) {
            // Set the plugin name
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
            
            // Add condition status information if available
            if (nextUpComingPlugin.hasAnyStartConditions()) {
                int total = nextUpComingPlugin.getStartConditionManager().getConditions().size();
                long satisfied = nextUpComingPlugin.getStartConditionManager().getConditions().stream()
                        .filter(condition -> condition.isSatisfied())
                        .count();
                
                if (total > 1) {
                    scheduleDesc.append(String.format(" [%d/%d conditions met]", satisfied, total));
                }
            }
            
            // Set the updated schedule description
            nextUpComingPluginScheduleLabel.setText(scheduleDesc.toString());
        } else {
            // No next plugin scheduled
            nextUpComingPluginNameLabel.setText("None");
            nextUpComingPluginTimeLabel.setText("--:--");
            nextUpComingPluginScheduleLabel.setText("None");
        }
    }

    /**
     * Updates only the runtime display for the current plugin without refreshing other info.
     * This is used for regular runtime updates when the plugin hasn't changed.
     */
    private void updateCurrentPluginRuntimeOnly() {
        PluginScheduleEntry currentPlugin = plugin.getCurrentPlugin();
        if (currentPlugin != null && currentPlugin.isRunning()) {
            // Calculate and display current runtime for active plugin
            ZonedDateTime startTimeZdt = currentPlugin.getLastRunStartTime();
            if (startTimeZdt != null) {
                long startTimeMillis = startTimeZdt.toInstant().toEpochMilli();
                long runtimeMillis = System.currentTimeMillis() - startTimeMillis;
                runtimeLabel.setText(formatDuration(runtimeMillis));
            } else {
                runtimeLabel.setText("Running");
            }
        }
    }
    
    /**
     * Updates only the time display for the next plugin without full refresh.
     * This is used for regular time updates when the plugin hasn't changed.
     * 
     * @param nextUpComingPlugin The next scheduled plugin
     */
    private void updateNextPluginTimeDisplayOnly(PluginScheduleEntry nextUpComingPlugin) {
        if (nextUpComingPlugin != null && nextUpComingPlugin.isEnabled()) {
            // Update only the time display - this calls getCurrentStartTriggerTime() for real-time accuracy
            nextUpComingPluginTimeLabel.setText(nextUpComingPlugin.getNextRunDisplay());
        }
    }

    private void onOpenConfigButtonClicked(ActionEvent e) {
        plugin.openSchedulerWindow();
    }

    private void onAntibanButtonClicked(ActionEvent e) {
        plugin.openAntibanSettings();
    }

    /**
     * Creates a tooltip showing pause status for the current plugin
     * @param currentPlugin The current plugin (can be null)
     * @return HTML formatted tooltip string
     */
    private String createPauseTooltipForCurrentPlugin(PluginScheduleEntry currentPlugin) {
        StringBuilder tooltip = new StringBuilder("<html>");
        
        if (currentPlugin == null) {
            tooltip.append("No current plugin");
            tooltip.append("</html>");
            return tooltip.toString();
        }
        
        // Current plugin info
        tooltip.append("<b>Current Plugin:</b> ").append(currentPlugin.getName()).append("<br>");
        tooltip.append("<b>Priority:</b> ").append(currentPlugin.getPriority()).append("<br>");
        tooltip.append("<b>Enabled:</b> ").append(currentPlugin.isEnabled() ? "Yes" : "No").append("<br>");
        tooltip.append("<b>Running:</b> ").append(currentPlugin.isRunning() ? "Yes" : "No").append("<br>");
        
        // Pause status
        if (currentPlugin.isPaused()) {
            tooltip.append("<b><font color='orange'>Status: PAUSED</font></b><br>");
        } else if (currentPlugin.isRunning()) {
            tooltip.append("<b><font color='green'>Status: RUNNING</font></b><br>");
        } else {
            tooltip.append("<b>Status: STOPPED</b><br>");
        }
        
        // Runtime info if available
        if (currentPlugin.isRunning() && currentPlugin.getLastRunStartTime() != null) {
            Duration runtime = Duration.between(currentPlugin.getLastRunStartTime(), ZonedDateTime.now());
            tooltip.append("<b>Runtime:</b> ").append(formatDurationForTooltip(runtime)).append("<br>");
        }
        
        // Add pause info for all plugins if any are paused
        if (plugin.anyPluginEntryPaused()) {
            tooltip.append("<br><b>Other Paused Plugins:</b><br>");
            List<PluginScheduleEntry> pausedPlugins = plugin.getScheduledPlugins().stream()
                .filter(p -> p.isPaused() && !p.equals(currentPlugin))
                .collect(Collectors.toList());
                
            if (pausedPlugins.isEmpty()) {
                tooltip.append("None");
            } else {
                for (PluginScheduleEntry pausedPlugin : pausedPlugins) {
                    tooltip.append("• ").append(pausedPlugin.getName())
                        .append(" (Priority: ").append(pausedPlugin.getPriority()).append(")<br>");
                }
            }
        }
        
        tooltip.append("</html>");
        return tooltip.toString();
    }
    
    /**
     * Creates a tooltip showing all paused plugins
     * @return HTML formatted tooltip string
     */
    private String createAllPausedPluginsTooltip() {
        StringBuilder tooltip = new StringBuilder("<html><b>Paused Plugins:</b><br>");
        
        List<PluginScheduleEntry> pausedPlugins = plugin.getScheduledPlugins().stream()
            .filter(PluginScheduleEntry::isPaused)
            .collect(Collectors.toList());
            
        if (pausedPlugins.isEmpty()) {
            tooltip.append("No plugins are currently paused");
        } else {
            for (PluginScheduleEntry pausedPlugin : pausedPlugins) {
                tooltip.append("• <b>").append(pausedPlugin.getName()).append("</b><br>");
                tooltip.append("  Priority: ").append(pausedPlugin.getPriority()).append("<br>");
                tooltip.append("  Enabled: ").append(pausedPlugin.isEnabled() ? "Yes" : "No").append("<br>");
                tooltip.append("  Running: ").append(pausedPlugin.isRunning() ? "Yes" : "No").append("<br>");
                
                if (pausedPlugin.isRunning() && pausedPlugin.getLastRunStartTime() != null) {
                    Duration runtime = Duration.between(pausedPlugin.getLastRunStartTime(), ZonedDateTime.now());
                    tooltip.append("  Runtime: ").append(formatDurationForTooltip(runtime)).append("<br>");
                }
                tooltip.append("<br>");
            }
        }
        
        tooltip.append("</html>");
        return tooltip.toString();
    }
    
    /**
     * Formats a duration for tooltip display
     * @param duration The duration to format
     * @return Formatted duration string (HH:MM:SS)
     */
    private String formatDurationForTooltip(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
