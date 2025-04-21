package net.runelite.client.plugins.microbot.pluginscheduler.ui;

import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerState;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ui.ConditionConfigPanel;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.PluginScheduleEntry.ScheduleFormPanel;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.PluginScheduleEntry.ScheduleTablePanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.plugins.microbot.Microbot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * A graphical user interface for the Plugin Scheduler system.
 * <p>
 * This window provides a comprehensive interface for managing scheduled plugins including:
 * <ul>
 *     <li>Viewing and managing the list of scheduled plugins</li>
 *     <li>Adding new plugins to the schedule</li>
 *     <li>Configuring plugin run parameters and stop conditions</li>
 *     <li>Monitoring scheduler status and currently running plugins</li>
 * </ul>
 * <p>
 * The UI is organized into tabbed sections:
 * <ul>
 *     <li>Schedule Tab - Contains a table of scheduled plugins and a form for adding/editing entries</li>
 *     <li>Stop Conditions Tab - Allows configuration of complex stop conditions for plugins</li>
 * </ul>
 * <p>
 * An information panel on the right side displays real-time information about the scheduler state.
 *
 * @see SchedulerPlugin
 * @see ScheduleTablePanel
 * @see ScheduleFormPanel
 * @see ConditionConfigPanel
 * @see SchedulerInfoPanel
 */

@Slf4j
public class SchedulerWindow extends JFrame {
    private final SchedulerPlugin plugin;
    private JTabbedPane tabbedPane;
    private final ScheduleTablePanel tablePanel;
    private final ScheduleFormPanel formPanel;
    private final ConditionConfigPanel stopConditionPanel;
    private final ConditionConfigPanel startConditionPanel;
    private final SchedulerInfoPanel infoPanel;
    // Timer for refreshing the info panel
    private Timer refreshTimer;

    public SchedulerWindow(SchedulerPlugin plugin) {
        super("Plugin Scheduler");
        this.plugin = plugin;

        // Increase width to accommodate the info panel
        setSize(1050, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Create main components
        tablePanel = new ScheduleTablePanel(plugin);
        formPanel = new ScheduleFormPanel(plugin);
        stopConditionPanel = new ConditionConfigPanel(true);
        startConditionPanel = new ConditionConfigPanel(false);
        infoPanel = new SchedulerInfoPanel(plugin);

        // Set up form panel actions
        formPanel.setAddButtonAction(e -> onAddPlugin());
        formPanel.setUpdateButtonAction(e -> onUpdatePlugin());
        formPanel.setRemoveButtonAction(e -> onRemovePlugin());
        formPanel.setEditMode(false);

        // Set up form panel to clear table selection when ComboBox changes
        formPanel.setSelectionChangeListener(() -> {
            tablePanel.clearSelection();
        });

       

        // Set up condition panel callback
        stopConditionPanel.setUserConditionUpdateCallback(userCondition -> {
            PluginScheduleEntry selected = tablePanel.getSelectedPlugin();
            if (selected != null) {             
                plugin.saveScheduledPlugins();
                tablePanel.refreshTable();
                infoPanel.refresh();
                
                // Check if we're waiting to start a plugin
                if (plugin.getCurrentState() == SchedulerState.STARTING_PLUGIN && 
                    plugin.getCurrentPlugin() == selected &&
                    !selected.getStopConditionManager().getConditions().isEmpty()) {
                    
                    // Conditions added for the plugin we're waiting to start - continue starting
                    int result = JOptionPane.showConfirmDialog(
                        this,
                        "Stop conditions have been added. Would you like to start the plugin now?",
                        "Start Plugin",
                        JOptionPane.YES_NO_OPTION
                    );
                    
                    if (result == JOptionPane.YES_OPTION) {
                        plugin.continueStartingPluginScheduleEntry(selected);
                    } else {
                        // User decided not to start - reset state
                        plugin.resetPendingStart();
                    }
                }
            }
        });

        // Create main content area using a better layout
        JPanel mainContent = createMainContentPanel();                        
        // Add main content to the center of the window
        add(mainContent, BorderLayout.CENTER);
        
        // Add tab change listener to sync selection
        tabbedPane.addChangeListener(e -> {
            PluginScheduleEntry selected = tablePanel.getSelectedPlugin();
            int tabIndex = tabbedPane.getSelectedIndex();
            
            // When switching to either conditions tab, ensure the condition panel shows the currently selected plugin
            if (tabIndex == 1 || tabIndex == 2) { // Start Conditions or Stop Conditions tab
                if (selected == null) {
                    PluginScheduleEntry nextPlugin = plugin.getNextScheduledPlugin(false,null).orElse(selected);
                    if (nextPlugin == null) {
                        log.warn("No plugin selected for editing conditions and no next scheduled plugin found.");                        
                    }else{
                        tablePanel.selectPlugin(nextPlugin);
                        log.warn("No plugin selected for editing conditions, taking next scheduled plugin: " + nextPlugin.getCleanName());                    
                    }
                    selected = nextPlugin;
                }
                
                if (tabIndex == 1) { // Start Conditions tab
                    startConditionPanel.setSelectScheduledPlugin(selected);
                } else if (tabIndex == 2) { // Stop Conditions tab
                    stopConditionPanel.setSelectScheduledPlugin(selected);
                }
            }
        });
        
        // Add table selection listener
        //tablePanel.addSelectionListener(this::onPluginSelected);
        // Modify the existing table selection listener to update ComboBox
        tablePanel.addSelectionListener(pluginEntry -> {
            onPluginSelected(pluginEntry);
            // Synchronize form panel ComboBox with table selection
            if (plugin != null) {
                formPanel.syncWithTableSelection(pluginEntry);
            }
        });
        // Create refresh timer to update info panel
        refreshTimer = new Timer(1000, e -> infoPanel.refresh());
        
        // Start timer when window is opened, stop when closed
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                refreshTimer.start();
            }
            
            @Override
            public void windowClosing(WindowEvent e) {
                refreshTimer.stop();
            }
        });
        
        // Style all comboboxes in the UI
        styleAllComboBoxes(this);
        
        // Initialize with data
        refresh();
        
    }










  
    /**
     * Recursively applies styling to all JComboBox components found in the container hierarchy.
     *
     * @param container The parent container to start searching from
     */

    private void styleAllComboBoxes(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JComboBox) {
                SchedulerUIUtils.styleComboBox((JComboBox<?>) component);
            }
            if (component instanceof Container) {
                styleAllComboBoxes((Container) component);
            }
        }
    }

    
   
    /**
     * Creates the main content panel with tabbed interface and information sidebar.
     *
     * @return JPanel with the configured layout
     */
    private JPanel createMainContentPanel() {
        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Create a more flexible tabbed pane structure
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tabbedPane.setForeground(Color.WHITE);
        
        // Schedule tab - Split into table (top) and form (bottom) with adjustable divider
        JPanel scheduleTab = new JPanel(new BorderLayout());
        scheduleTab.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Create a split pane for the table and form - use VERTICAL layout for better table visibility
        JSplitPane scheduleSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        scheduleSplitPane.setTopComponent(tablePanel);
        scheduleSplitPane.setBottomComponent(formPanel);
        scheduleSplitPane.setResizeWeight(0.7); // Give 70% space to the table on top
        scheduleSplitPane.setDividerLocation(350); // Set initial divider position
        scheduleSplitPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scheduleTab.add(scheduleSplitPane, BorderLayout.CENTER);
        
        // Start Conditions tab
        JPanel startConditionsTab = new JPanel(new BorderLayout());
        startConditionsTab.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        startConditionsTab.add(startConditionPanel, BorderLayout.CENTER);
        
        // Stop Conditions tab
        JPanel stopConditionsTab = new JPanel(new BorderLayout());
        stopConditionsTab.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        stopConditionsTab.add(stopConditionPanel, BorderLayout.CENTER);
        
        // Add tabs to tabbed pane
        tabbedPane.addTab("Schedule", scheduleTab);
        tabbedPane.addTab("Start Conditions", startConditionsTab);
        tabbedPane.addTab("Stop Conditions", stopConditionsTab);
        
        // Create a split pane for the tabs and info panel
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setLeftComponent(tabbedPane);
        mainSplitPane.setRightComponent(infoPanel);
        mainSplitPane.setResizeWeight(0.75); // Favor the main content
        mainSplitPane.setDividerLocation(800);
        mainSplitPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        mainContent.add(mainSplitPane, BorderLayout.CENTER);
        return mainContent;
    }

    

    /**
     * Refreshes all UI components with the latest data from the plugin.
     * Updates the table, form state, condition panel, and information panel.
     */
    public void refresh() {
        tablePanel.refreshTable();
        if (formPanel != null) {
            formPanel.updateControlButton();
        }
        if (stopConditionPanel != null) {
            stopConditionPanel.refreshConditions();            
        }
        if (startConditionPanel != null) {
            startConditionPanel.refreshConditions();
        }
        
        // Refresh info panel
        if (infoPanel != null) {
            infoPanel.refresh();
        }
    }
    /**
     * Handles plugin selection events from the table.
     * Updates the form panel and condition panel with the selected plugin's data.
     *
     * @param plugin The selected plugin or null if no selection
     */
    private void onPluginSelected(PluginScheduleEntry plugin) {        
        if (tablePanel.getRowCount() == 0) {            
            log.info("No plugins in table.");
            return;
        }
        PluginScheduleEntry selected = tablePanel.getSelectedPlugin();   
        if (selected == null) {            
            formPanel.setEditMode(false);
            // Update both condition panels when no plugin is selected
            startConditionPanel.setSelectScheduledPlugin(null);
            stopConditionPanel.setSelectScheduledPlugin(null);
            if (plugin == null) {                
                return;
            }
            log.info("No plugin selected for editing. {} but plugin", plugin.getCleanName());
            return;
        }
        if (selected != plugin && selected != null) {            
            formPanel.loadPlugin(plugin);
            formPanel.setEditMode(true);
            
            // Update both condition panels with the selected plugin
            int currentTabIndex = tabbedPane.getSelectedIndex();
            if (currentTabIndex == 1) { // Start Conditions tab
                startConditionPanel.setSelectScheduledPlugin(selected);
            } else if (currentTabIndex == 2) { // Stop Conditions tab
                stopConditionPanel.setSelectScheduledPlugin(selected);
            }
        }
        
        // Always update control button when selection changes
        formPanel.updateControlButton();
    }

    /**
     * Processes the addition of a new plugin from the form data.
     * Checks for stop conditions and prompts user if none are configured.
     */
    private void onAddPlugin() {
        PluginScheduleEntry scheduledPlugin = formPanel.getPluginFromForm();
        if (scheduledPlugin == null) return;
        
        LogicalCondition pluginCond= scheduledPlugin.getStopConditionManager().getPluginCondition();
        if (pluginCond != null) {
            log.info("Plugin condition: " + pluginCond.getDescription());
        }else {
            log.info("No plugin condition set.");
        }

        scheduledPlugin.logConditionInfo(scheduledPlugin.getStopConditions(),
                            "onAddPlugin Stop Conditions: " + scheduledPlugin.getName(), 
                            true);

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
                log.info("Plugin added without conditions: row count" + tablePanel.getRowCount());
                // Select the newly added plugin
                tablePanel.selectPlugin(scheduledPlugin);
                
                // Switch to conditions tab
                tabbedPane.setSelectedIndex(1);                
                return;
            } else if (result == JOptionPane.CANCEL_OPTION) {
                scheduledPlugin.setEnabled(false); // Set to disabled by default
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

    /**
     * Updates an existing plugin with data from the form.
     * Preserves the plugin's identity while updating its configuration.
     */
    private void onUpdatePlugin() {
        PluginScheduleEntry selectedPlugin = tablePanel.getSelectedPlugin();
        if (selectedPlugin == null) {
            return;
        }
        
        try {
            // Get the updated plugin configuration from the form
            PluginScheduleEntry updatedConfig = formPanel.getPluginFromForm();
            
            // Update the selected plugin's properties but keep its identity
            selectedPlugin.setName(updatedConfig.getName());
            selectedPlugin.setEnabled(updatedConfig.isEnabled());
            selectedPlugin.setAllowRandomScheduling(updatedConfig.isAllowRandomScheduling());
            selectedPlugin.setPriority(updatedConfig.getPriority());
            selectedPlugin.setDefault(updatedConfig.isDefault());
              // Get the main time condition from the updated config
            TimeCondition newTimeCondition = updatedConfig.getMainTimeStartCondition();
            
            selectedPlugin.updatePrimaryTimeCondition((TimeCondition) newTimeCondition);            
            // Update the UI
            plugin.saveScheduledPlugins();
            tablePanel.refreshTable();
            formPanel.setEditMode(false);
            formPanel.clearForm();
            tablePanel.clearSelection();
            
        } catch (Exception e) {
            log.error("Error updating plugin: " + e.getMessage(), e);
            // This correctly logs the stack trace with SLF4J
            log.error("Stack trace: ", e);
            JOptionPane.showMessageDialog(
                this,
                "Error updating plugin: " + e.getMessage(),
                "Update Error",
                JOptionPane.ERROR_MESSAGE
            );
                        
            
        }
    }
    /**
     * Removes the currently selected plugin from the schedule.
     */
    private void onRemovePlugin() {
        PluginScheduleEntry _plugin = tablePanel.getSelectedPlugin();
        if (_plugin != null) {
            plugin.removeScheduledPlugin(_plugin);
            tablePanel.refreshTable();
            formPanel.clearForm();
            
            log.info("onRemovePlugin: " + _plugin.getName());
            stopConditionPanel.setSelectScheduledPlugin(null);
        }

        plugin.saveScheduledPlugins();
    }    
    /**
     * Cleans up resources when window is closed.
     * Stops the refresh timer before disposing the window.
     */
    @Override
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        super.dispose();
    }        
    /**
     * Programmatically selects a plugin in the table.
     *
     * @param plugin The plugin entry to select
     */
    public void selectPlugin(PluginScheduleEntry plugin) {
        if (tablePanel != null) {
            tablePanel.selectPlugin(plugin);
        }
    }
    /**
     * Switches the UI to display the stop conditions tab.
     */
    public void switchToStopConditionsTab() {
        if (tabbedPane != null) {
            tabbedPane.setSelectedIndex(2); // Switch to stop conditions tab (now at index 2)
        }
    }
}
