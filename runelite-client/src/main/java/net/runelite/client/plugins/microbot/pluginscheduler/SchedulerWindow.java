package net.runelite.client.plugins.microbot.pluginscheduler;

import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.type.ScheduledPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.ConditionConfigPanel;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.ScheduleFormPanel;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.ScheduleTablePanel;

import net.runelite.client.ui.ColorScheme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import lombok.extern.slf4j.Slf4j;

import java.awt.*;

@Slf4j
public class SchedulerWindow extends JFrame {
    private final SchedulerPlugin plugin;
    private final JTabbedPane tabbedPane;
    private final ScheduleTablePanel tablePanel;
    private final ScheduleFormPanel formPanel;
    private final ConditionConfigPanel stopConditionPanel;
    private JButton runSchedulerButton;
    private JButton stopSchedulerButton;

    public SchedulerWindow(SchedulerPlugin plugin) {
        super("Plugin Scheduler");
        this.plugin = plugin;

        setSize(700, 550);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Create main components
        tablePanel = new ScheduleTablePanel(plugin);
        formPanel = new ScheduleFormPanel(plugin);
        stopConditionPanel = new ConditionConfigPanel(plugin,true);

        // Set up form panel actions
        formPanel.setAddButtonAction(e -> onAddPlugin());
        formPanel.setUpdateButtonAction(e -> onUpdatePlugin());
        formPanel.setRemoveButtonAction(e -> onRemovePlugin());
        formPanel.setEditMode(false);

        // Set up condition panel callback
        stopConditionPanel.setUserConditionUpdateCallback(userCondition -> {
            ScheduledPlugin selected = tablePanel.getSelectedPlugin();
            if (selected != null) {             
                plugin.saveScheduledPlugins();
                tablePanel.refreshTable(); // Refresh to show updated conditions
            }
        });

        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        
        // Schedule tab
        JPanel scheduleTab = new JPanel(new BorderLayout());
        scheduleTab.add(tablePanel, BorderLayout.CENTER);
        scheduleTab.add(formPanel, BorderLayout.SOUTH);
        
        // Conditions tab
        JPanel conditionsTab = new JPanel(new BorderLayout());
        JLabel instructionLabel = new JLabel("<html>Configure stop conditions for the selected plugin</html>");
        instructionLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        instructionLabel.setForeground(Color.WHITE);
        conditionsTab.add(instructionLabel, BorderLayout.NORTH);
        conditionsTab.add(stopConditionPanel, BorderLayout.CENTER);
        
        // Add control buttons panel
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        controlPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Create run scheduler button
        runSchedulerButton = new JButton("Run Scheduler");
        runSchedulerButton.setBackground(new Color(76, 175, 80));
        runSchedulerButton.setForeground(Color.WHITE);
        runSchedulerButton.setFocusPainted(false);
        runSchedulerButton.addActionListener(e -> {
            plugin.startScheduler();
            updateButtonState();
        });
        
        // Create stop scheduler button
        stopSchedulerButton = new JButton("Stop Scheduler");
        stopSchedulerButton.setBackground(new Color(244, 67, 54));
        stopSchedulerButton.setForeground(Color.WHITE);
        stopSchedulerButton.setFocusPainted(false);
        stopSchedulerButton.addActionListener(e -> {
            plugin.stopScheduler();
            updateButtonState();
        });
        
        controlPanel.add(runSchedulerButton);
        controlPanel.add(stopSchedulerButton);
        
        // Add control panel to the top of the window
        add(controlPanel, BorderLayout.NORTH);
        
        // Add tabs
        tabbedPane.addTab("Schedule", scheduleTab);
        tabbedPane.addTab("Stop Conditions", conditionsTab);
        
        // Add tab change listener to sync selection
        tabbedPane.addChangeListener(e -> {
            // When switching to Conditions tab, ensure the condition panel shows the currently selected plugin
            if (tabbedPane.getSelectedIndex() == 1) { // Conditions tab
                ScheduledPlugin selected = tablePanel.getSelectedPlugin();                                
                stopConditionPanel.setSelectScheduledPlugin(selected);                                                
            }
        });
        
        // Add table selection listener
        tablePanel.addSelectionListener(this::onPluginSelected);

        // Add tabbed pane to frame
        add(tabbedPane, BorderLayout.CENTER);
        
        // Initialize with data
                refresh();
        updateButtonState();
    }

    // Update the updateButtonState method
    private void updateButtonState() {
        SchedulerState state = plugin.getCurrentState();
        boolean isActive = plugin.isSchedulerActive();
        
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
        
        // Add a status indication
        this.setTitle("Plugin Scheduler - " + state.getDisplayName());
    }

    void refresh() {
        tablePanel.refreshTable();
        if (formPanel != null) {
            formPanel.updateControlButton();
        }
        updateButtonState();
        if (stopConditionPanel != null) {
            stopConditionPanel.refreshConditions();            
        }
        
        ScheduledPlugin selectedInTable = tablePanel.getSelectedPlugin();        
    }

    private void onPluginSelected(ScheduledPlugin plugin) {
        ScheduledPlugin selected = tablePanel.getSelectedPlugin();                
        if (selected != plugin && selected != null) {            
            formPanel.loadPlugin(plugin);
            formPanel.setEditMode(true);    
            stopConditionPanel.setSelectScheduledPlugin(selected);

        } else {                                        
            //formPanel.clearForm();
            //formPanel.setEditMode(false);
            //stopConditionPanel.setSelectScheduledPlugin(plugin);
            
        }
    }

    private void onAddPlugin() {
        ScheduledPlugin scheduledPlugin = formPanel.getPluginFromForm();
        if (scheduledPlugin == null) return;
        
        LogicalCondition pluginCond= scheduledPlugin.getStopConditionManager().getPluginCondition();
        if (pluginCond != null) {
            log.info("Plugin condition: " + pluginCond.getDescription());
        }else {
            log.info("No plugin condition set.");
        }

        scheduledPlugin.logConditionInfo("onAddPlugin: " + scheduledPlugin.getName(), true);
        // Check if the plugin has stop conditions
        if (scheduledPlugin.getStopConditionManager().getConditions().isEmpty()) {
            // No stop conditions set, show warning and switch to conditions tab
            int result = JOptionPane.showConfirmDialog(this,
                    "No stop conditions are set. The plugin will run until manually stopped.\n" +
                    "Would you like to configure stop conditions now?",
                    "No Stop Conditions",
                    JOptionPane.YES_NO_CANCEL_OPTION);
                    
            if (result == JOptionPane.YES_OPTION) {
                // Add the plugin first so we can set conditions on it
                scheduledPlugin.setEnabled(false); // Set to disabled by default
                plugin.addScheduledPlugin(scheduledPlugin);
                plugin.saveScheduledPlugins();
                refresh();
                
                // Select the newly added plugin
                tablePanel.selectPlugin(scheduledPlugin);
                
                // Switch to conditions tab
                tabbedPane.setSelectedIndex(1);                
                return;
            } else if (result == JOptionPane.CANCEL_OPTION) {
                return; // Cancel the operation
            }
            // If NO, continue with adding plugin without conditions
        }
        
        scheduledPlugin.setEnabled(false); // Set to disabled by default
        plugin.addScheduledPlugin(scheduledPlugin);
        plugin.saveScheduledPlugins();
        
        refresh();
        formPanel.clearForm();
    }

    private void onUpdatePlugin() {
        ScheduledPlugin selectedPlugin = tablePanel.getSelectedPlugin();
        if (selectedPlugin == null) return;
        
        ScheduledPlugin updatedPlugin = formPanel.getPluginFromForm();
        if (updatedPlugin == null) return;
        
        // Check if the plugin h/*  */as stop conditions
        if (updatedPlugin.getStopConditionManager().getConditions().isEmpty() && 
            selectedPlugin.getStopConditionManager().getConditions().isEmpty()) {
            // No stop conditions set, show warning and switch to conditions tab
            int result = JOptionPane.showConfirmDialog(this,
                    "No stop conditions are set. The plugin will run until manually stopped.\n" +
                    "Would you like to configure stop conditions now?",
                    "No Stop Conditions",
                    JOptionPane.YES_NO_CANCEL_OPTION);
                    
            if (result == JOptionPane.YES_OPTION) {
                // Switch to conditions tab
                tabbedPane.setSelectedIndex(1);
                //stopConditionPanel.setSelectScheduledPlugin(selectedPlugin); // Use the existing plugin
                return; // Don't update the plugin yet
            } else if (result == JOptionPane.CANCEL_OPTION) {
                return; // Cancel the operation
            }
            // If NO, continue with updating plugin without conditions
        }
        
        // Keep the existing conditions when updating
        updatedPlugin.getStopConditionManager().getConditions().clear();
        for (Condition condition : selectedPlugin.getStopConditionManager().getConditions()) {
            updatedPlugin.addCondition(condition);
        }
        
        // Transfer other properties
        updatedPlugin.setEnabled(selectedPlugin.isEnabled());

        // Don't use getId/setId since they don't exist
        
        // Use the updateScheduledPlugin method instead of updatePlugin
        plugin.updateScheduledPlugin(selectedPlugin, updatedPlugin);
        plugin.saveScheduledPlugins();
        
        refresh();
        formPanel.clearForm();
    }

    private void onRemovePlugin() {
        ScheduledPlugin _plugin = tablePanel.getSelectedPlugin();
        if (_plugin != null) {
            plugin.removeScheduledPlugin(_plugin);
            tablePanel.refreshTable();
            formPanel.clearForm();
            formPanel.setEditMode(false);
            log.info("onRemovePlugin: " + _plugin.getName());
            stopConditionPanel.setSelectScheduledPlugin(null);
        }
    }
}
