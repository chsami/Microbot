package net.runelite.client.plugins.microbot.pluginscheduler.tasks.ui;

import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.AbstractPrePostScheduleTasks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.state.TaskExecutionState;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * A comprehensive UI component for displaying pre/post schedule task information for a plugin.
 * This panel shows both the execution state and requirements information.
 */
public class PrePostScheduleTasksInfoPanel extends JPanel {
    
    private TaskExecutionStatePanel preTaskStatePanel;
    private TaskExecutionStatePanel postTaskStatePanel;
    private RequirementsStatusPanel requirementsPanel;
    private JLabel pluginNameLabel;
    private JLabel tasksEnabledLabel;
    
    // State tracking
    private SchedulablePlugin lastTrackedPlugin;
    private AbstractPrePostScheduleTasks lastTrackedTasks;
    
    public PrePostScheduleTasksInfoPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE, 2),
                "Pre/Post Schedule Tasks",
                TitledBorder.CENTER,
                TitledBorder.TOP,
                FontManager.getRunescapeBoldFont(),
                Color.WHITE
            ),
            new EmptyBorder(8, 8, 8, 8)
        ));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setOpaque(true);
        
        // Header panel with plugin info
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);
        
        // Main content with task states
        JPanel contentPanel = createContentPanel();
        add(contentPanel, BorderLayout.CENTER);
        
        // Initially hidden until a plugin with tasks is set
        setVisible(false);
    }
    
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setOpaque(true);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Plugin name
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0.0;
        JLabel pluginTitle = new JLabel("Plugin:");
        pluginTitle.setFont(FontManager.getRunescapeSmallFont());
        pluginTitle.setForeground(Color.LIGHT_GRAY);
        panel.add(pluginTitle, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        pluginNameLabel = new JLabel("None");
        pluginNameLabel.setFont(FontManager.getRunescapeBoldFont());
        pluginNameLabel.setForeground(Color.WHITE);
        panel.add(pluginNameLabel, gbc);
        
        // Tasks enabled status
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel enabledTitle = new JLabel("Tasks:");
        enabledTitle.setFont(FontManager.getRunescapeSmallFont());
        enabledTitle.setForeground(Color.LIGHT_GRAY);
        panel.add(enabledTitle, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        tasksEnabledLabel = new JLabel("Disabled");
        tasksEnabledLabel.setFont(FontManager.getRunescapeSmallFont());
        tasksEnabledLabel.setForeground(Color.RED);
        panel.add(tasksEnabledLabel, gbc);
        
        return panel;
    }
    
    private JPanel createContentPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setOpaque(true);
        
        // Create task state panels
        preTaskStatePanel = new TaskExecutionStatePanel("Pre-Schedule Tasks");
        postTaskStatePanel = new TaskExecutionStatePanel("Post-Schedule Tasks");
        
        // Create requirements status panel
        requirementsPanel = new RequirementsStatusPanel();
        
        // Add components with spacing
        panel.add(preTaskStatePanel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(postTaskStatePanel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(requirementsPanel);
        
        return panel;
    }
    
    /**
     * Updates the panel with information from the specified plugin
     */
    public void updatePlugin(SchedulablePlugin plugin) {
        if (plugin == null) {
            setVisible(false);
            return;
        }
        
        // Check if this is a different plugin or if tasks changed
        AbstractPrePostScheduleTasks tasks = plugin.getPrePostScheduleTasks();
        boolean pluginChanged = plugin != lastTrackedPlugin;
        boolean tasksChanged = tasks != lastTrackedTasks;
        
        if (pluginChanged || tasksChanged) {
            updatePluginHeader(plugin, tasks);
            lastTrackedPlugin = plugin;
            lastTrackedTasks = tasks;
        }
        
        // Update task execution states
        if (tasks != null) {
            TaskExecutionState executionState = tasks.getExecutionState();
            TaskExecutionState.ExecutionPhase currentPhase = executionState.getCurrentPhase();
            
            // Update appropriate panel based on current phase
            if (currentPhase == TaskExecutionState.ExecutionPhase.PRE_SCHEDULE) {
                preTaskStatePanel.updateState(executionState);
                preTaskStatePanel.setVisible(true);
                postTaskStatePanel.setVisible(false);
                setVisible(true);
            } else if (currentPhase == TaskExecutionState.ExecutionPhase.POST_SCHEDULE) {
                postTaskStatePanel.updateState(executionState);
                postTaskStatePanel.setVisible(true);
                preTaskStatePanel.setVisible(false);
                setVisible(true);
            } else {
                // Idle or main execution - hide both task panels and the whole panel if no active tasks
                preTaskStatePanel.setVisible(false);
                postTaskStatePanel.setVisible(false);
                
                // Show the panel if tasks are available (even if not executing) to show requirements
                // Hide completely only if in IDLE phase and no interesting state to show
                if (currentPhase == TaskExecutionState.ExecutionPhase.IDLE) {
                    setVisible(false);
                } else {
                    setVisible(true);
                }
            }
            
            // Update requirements panel with execution state for enhanced progress tracking
            PrePostScheduleRequirements requirements = tasks.getRequirements();
            requirementsPanel.updateRequirements(requirements, executionState);
            
        } else {
            // No tasks available at all - hide everything
            preTaskStatePanel.setVisible(false);
            postTaskStatePanel.setVisible(false);
            setVisible(false);
        }
    }
    
    private void updatePluginHeader(SchedulablePlugin plugin, AbstractPrePostScheduleTasks tasks) {
        // Update plugin name
        String pluginName = "Unknown";
        if (plugin instanceof net.runelite.client.plugins.Plugin) {
            net.runelite.client.plugins.Plugin p = (net.runelite.client.plugins.Plugin) plugin;
            net.runelite.client.plugins.PluginDescriptor descriptor = p.getClass().getAnnotation(net.runelite.client.plugins.PluginDescriptor.class);
            if (descriptor != null) {
                pluginName = descriptor.name();
            } else {
                pluginName = p.getClass().getSimpleName();
            }
        }
        pluginNameLabel.setText(pluginName);
        
        // Update tasks enabled status
        if (tasks != null) {
            tasksEnabledLabel.setText("Enabled");
            tasksEnabledLabel.setForeground(Color.GREEN);
        } else {
            tasksEnabledLabel.setText("Disabled");
            tasksEnabledLabel.setForeground(Color.RED);
        }
    }
    
    /**
     * Clears the panel and hides it
     */
    public void clear() {
        pluginNameLabel.setText("None");
        tasksEnabledLabel.setText("Disabled");
        tasksEnabledLabel.setForeground(Color.RED);
        
        preTaskStatePanel.reset();
        postTaskStatePanel.reset();
        requirementsPanel.clear();
        
        lastTrackedPlugin = null;
        lastTrackedTasks = null;
        
        setVisible(false);
    }
    
    /**
     * Forces a refresh of the task states - useful when task execution begins or ends
     */
    public void refresh() {
        if (lastTrackedTasks != null) {
            TaskExecutionState executionState = lastTrackedTasks.getExecutionState();
            
            // Reset visibility for both panels
            preTaskStatePanel.setVisible(false);
            postTaskStatePanel.setVisible(false);
            
            // Show appropriate panel based on current phase
            TaskExecutionState.ExecutionPhase phase = executionState.getCurrentPhase();
            if (phase == TaskExecutionState.ExecutionPhase.PRE_SCHEDULE) {
                preTaskStatePanel.updateState(executionState);
                preTaskStatePanel.setVisible(true);
            } else if (phase == TaskExecutionState.ExecutionPhase.POST_SCHEDULE) {
                postTaskStatePanel.updateState(executionState);
                postTaskStatePanel.setVisible(true);
            }
            
            // Update requirements panel with execution state
            requirementsPanel.updateRequirements(lastTrackedTasks.getRequirements(), executionState);
            
            repaint();
        }
    }
}
