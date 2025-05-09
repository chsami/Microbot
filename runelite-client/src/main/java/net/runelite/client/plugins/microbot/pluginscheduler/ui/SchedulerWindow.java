package net.runelite.client.plugins.microbot.pluginscheduler.ui;

import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerState;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ui.ConditionConfigPanel;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ui.callback.ConditionUpdateCallback;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.PluginScheduleEntry.ScheduleFormPanel;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.PluginScheduleEntry.ScheduleTablePanel;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.util.SchedulerUIUtils;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

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
public class SchedulerWindow extends JFrame implements ConditionUpdateCallback {
    private final SchedulerPlugin plugin;
    private JTabbedPane tabbedPane;
    private final ScheduleTablePanel tablePanel;
    private final ScheduleFormPanel formPanel;
    private final ConditionConfigPanel stopConditionPanel;
    private final ConditionConfigPanel startConditionPanel;
    private final SchedulerInfoPanel infoPanel;
    // Timer for refreshing the info panel
    private Timer refreshTimer;
    // Last used file for saving/loading conditions
    private File lastSaveFile;

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

        // Set up condition panels with the callback
        stopConditionPanel.setConditionUpdateCallback(this);
        startConditionPanel.setConditionUpdateCallback(this);
        
       

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
            if (tabIndex == 0) {
                formPanel.loadPlugin(selected);
                formPanel.setEditMode(true);
            }
        });
        
        // Add table selection listener
        //tablePanel.addSelectionListener(this::onPluginSelected);
        // Modify the existing table selection listener to update ComboBox
        tablePanel.addSelectionListener(pluginEntry -> {
            onPluginSelected(pluginEntry);
            // Synchronize form panel ComboBox with table selection        
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
     * Implementation of ConditionUpdateCallback interface.
     * Called when conditions are updated in the UI and need to be saved.
     */
    @Override
    public void onConditionsUpdated(    LogicalCondition logicalCondition, 
                                        PluginScheduleEntry plugin, 
                                        boolean isStopCondition) {
        // Save to default configuration
        onConditionsUpdated(logicalCondition, plugin, isStopCondition, null);
    }
    
    /**
     * Implementation of ConditionUpdateCallback interface.
     * Called when conditions are updated and need to be saved to a specific file.
     */
    @Override
    public void onConditionsUpdated(LogicalCondition logicalCondition, PluginScheduleEntry pluginEntry, 
                                  boolean isStopCondition, File saveFile) {
        if (pluginEntry == null) {
            log.warn("Cannot save conditions: No plugin selected");
            return;
        }
        
       
        
        try {
           
            // Update the plugin's condition manager with the new logical condition
            /*if (isStopCondition) {
                // For stop conditions
                
                this.plugin.saveConditionsToPlugin(
                    pluginEntry, 
                    pluginEntry.getStopConditions(), 
                    null,  // No changes to start conditions
                    requireAll, 
                    true,  // Stop on conditions met
                    saveFile
                );
            } else {
                // For start conditions                
                this.plugin.saveConditionsToPlugin(
                    pluginEntry, 
                    pluginEntry.getStopConditions(),  // Keep existing stop conditions
                    pluginEntry.getStartConditions(), // Update start conditions
                    requireAll, 
                    true,  // Stop on conditions met
                    saveFile
                );
            }*/
            
            // Remember this file for future operations
            if (saveFile != null) {
                this.lastSaveFile = saveFile;
            }
            PluginScheduleEntry selected = tablePanel.getSelectedPlugin();
            if (selected != null) {                                                             
                // Check if we're waiting to start a plugin
                if ( isStopCondition && plugin.getCurrentState() == SchedulerState.WAITING_FOR_STOP_CONDITION && 
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
                        plugin.continuePendingStart(selected);
                    } else {
                        // User decided not to start - reset state
                        plugin.resetPendingStart();
                    }
                }
            }
            // Refresh UI elements
            tablePanel.refreshTable();
            infoPanel.refresh();
            plugin.saveScheduledPlugins();
            log.info("Successfully saved {} conditions for plugin: {}", 
                    isStopCondition ? "stop" : "start", 
                    pluginEntry.getCleanName());
        } catch (Exception e) {
            log.error("Error saving conditions for plugin: " + pluginEntry.getCleanName(), e);
            JOptionPane.showMessageDialog(
                this,
                "Error saving conditions: " + e.getMessage(),
                "Save Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    /**
     * Implementation of ConditionUpdateCallback interface.
     * Called when conditions are reset in the UI.
     */
    @Override
    public void onConditionsReset(PluginScheduleEntry pluginEntry, boolean isStopCondition) {
        if (pluginEntry == null) {
            log.warn("Cannot reset conditions: No plugin selected");
            return;
        }
        
        log.info("Resetting {} conditions for plugin: {}", 
                isStopCondition ? "stop" : "start", 
                pluginEntry.getCleanName());
        
        try {
            // Clear conditions from the plugin's condition manager
            if (isStopCondition) {
                pluginEntry.getStopConditionManager().clearUserConditions();
            } else {
                pluginEntry.getStartConditionManager().clearUserConditions();
            }
            
            // Save changes to config
            this.plugin.saveScheduledPlugins();
            
            // Refresh UI
            tablePanel.refreshTable();
            infoPanel.refresh();
            
            // If this was the start conditions tab, refresh also the start condition panel
            if (!isStopCondition) {
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(100); // Small delay to ensure changes are processed
                        SwingUtilities.invokeLater(() -> startConditionPanel.refreshConditions());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            // If this was the stop conditions tab, refresh also the stop condition panel
            else {
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(100); // Small delay to ensure changes are processed
                        SwingUtilities.invokeLater(() -> stopConditionPanel.refreshConditions());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            
            log.info("Successfully reset {} conditions for plugin: {}", 
                    isStopCondition ? "stop" : "start", 
                    pluginEntry.getCleanName());
        } catch (Exception e) {
            log.error("Error resetting conditions for plugin: " + pluginEntry.getCleanName(), e);
            JOptionPane.showMessageDialog(
                this,
                "Error resetting conditions: " + e.getMessage(),
                "Reset Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Shows a dialog to choose a file for saving or loading conditions
     * 
     * @param save True for save dialog, false for open dialog
     * @return The selected file, or null if canceled
     */
    public File showFileChooser(boolean save) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(save ? "Save Scheduler Plan" : "Load Scheduler Plan");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        // Set initial directory to last used if available
        if (lastSaveFile != null && lastSaveFile.getParentFile() != null && lastSaveFile.getParentFile().exists()) {
            fileChooser.setCurrentDirectory(lastSaveFile.getParentFile());
        }
        
        // Add file extension filter
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".json");
            }
            
            @Override
            public String getDescription() {
                return "JSON Files (*.json)";
            }
        });
        
        int result = save ? fileChooser.showSaveDialog(this) : fileChooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            
            // Add .json extension if missing for save dialogs
            if (save && !selectedFile.getName().toLowerCase().endsWith(".json")) {
                selectedFile = new File(selectedFile.getAbsolutePath() + ".json");
            }
            
            return selectedFile;
        }
        
        return null;
    }
    
    /**
     * Saves the currently loaded scheduler plan to a file
     */
    public void saveSchedulerPlanToFile() {
        File saveFile = showFileChooser(true);
        if (saveFile == null) {
            return;
        }
        
        // Check if file already exists
        if (saveFile.exists()) {
            int option = JOptionPane.showConfirmDialog(
                this,
                "File already exists. Overwrite?",
                "File Exists",
                JOptionPane.YES_NO_OPTION
            );
            
            if (option != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        // Save the plan
        boolean success = plugin.saveScheduledPluginsToFile(saveFile);
        
        if (success) {
            lastSaveFile = saveFile;
            JOptionPane.showMessageDialog(
                this,
                "Scheduler plan saved successfully!",
                "Save Complete",
                JOptionPane.INFORMATION_MESSAGE
            );
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Failed to save scheduler plan. See log for details.",
                "Save Failed",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    /**
     * Loads a scheduler plan from a file
     */
    public void loadSchedulerPlanFromFile() {
        File loadFile = showFileChooser(false);
        if (loadFile == null) {
            return;
        }
        
        // Confirm before loading
        int option = JOptionPane.showConfirmDialog(
            this,
            "Loading will replace the current scheduler plan. Continue?",
            "Load Scheduler Plan",
            JOptionPane.YES_NO_OPTION
        );
        
        if (option != JOptionPane.YES_OPTION) {
            return;
        }
        
        // Load the plan
        boolean success = plugin.loadScheduledPluginsFromFile(loadFile);
        
        if (success) {
            lastSaveFile = loadFile;
            refresh();
            JOptionPane.showMessageDialog(
                this,
                "Scheduler plan loaded successfully!",
                "Load Complete",
                JOptionPane.INFORMATION_MESSAGE
            );
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Failed to load scheduler plan. See log for details.",
                "Load Failed",
                JOptionPane.ERROR_MESSAGE
            );
        }
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
        
        // Calculate the preferred size for showing 3 rows in the table
        // Row height (30px) * 3 rows + header height (~25px) + legend panel (~30px) + borders/margins (~15px)
        int preferredTableHeight = (30 * 3) + 25 + 30 + 15; // ~160px for 3 rows
        
        // Calculate the maximum size for showing 8 rows in the table
        int maxTableHeight = (30 * 8) + 25 + 30 + 15; // ~310px for 8 rows
        
        // Set minimum size for the form panel to ensure it doesn't get too small
        formPanel.setMinimumSize(new Dimension(0, 140));
        
        // Set minimum size for the table panel to ensure at least 3 rows are visible
        tablePanel.setMinimumSize(new Dimension(0, preferredTableHeight));
        
        // Set preferred size for the table panel
        tablePanel.setPreferredSize(new Dimension(0, preferredTableHeight));
        
        // Set maximum size for the table panel to prevent expanding beyond 8 rows
        tablePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxTableHeight));
        
        scheduleSplitPane.setResizeWeight(0.7); // Give 70% space to the table on top
        scheduleSplitPane.setDividerLocation(preferredTableHeight); // Set initial divider position
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
        
        // Add a top control panel for file operations with better visibility
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BorderLayout());
        controlPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        controlPanel.setBorder(new EmptyBorder(5, 8, 5, 8));
        
        // Create button panel with FlowLayout for better spacing
        JPanel fileButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        fileButtonsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Create load button with improved styling
        JButton loadButton = createStyledButton("Load Plan from File...", Color.BLUE, "load.png");
        loadButton.setToolTipText("Load a saved scheduler plan from a file");
        loadButton.addActionListener(e -> loadSchedulerPlanFromFile());
        
        // Create save button with improved styling
        JButton saveButton = createStyledButton("Save Plan to File...", ColorScheme.PROGRESS_COMPLETE_COLOR, "save.png");
        saveButton.setToolTipText("Save current scheduler plan to a file");
        saveButton.addActionListener(e -> saveSchedulerPlanToFile());
        
        // Add buttons to the panel
        fileButtonsPanel.add(loadButton);
        fileButtonsPanel.add(saveButton);
        
        // Add the buttons panel to the control panel
        controlPanel.add(fileButtonsPanel, BorderLayout.WEST);
        
        // Add optional heading/title if desired
        JLabel controlPanelTitle = new JLabel("Scheduler Controls");
        controlPanelTitle.setForeground(Color.WHITE);
        controlPanelTitle.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        controlPanel.add(controlPanelTitle, BorderLayout.EAST);
        
        // Add control panel to the top
        mainContent.add(controlPanel, BorderLayout.NORTH);
        
        // Add main split pane to the center
        mainContent.add(mainSplitPane, BorderLayout.CENTER);
        
        return mainContent;
    }

    /**
     * Creates a consistently styled button with icon and hover effects
     */
    private JButton createStyledButton(String text, Color color, String iconName) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setForeground(Color.WHITE);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setFocusPainted(false);
        button.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 1),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
                
        // Add hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(ColorScheme.DARK_GRAY_COLOR);
                button.setBorder(new CompoundBorder(
                    BorderFactory.createLineBorder(color, 1),
                    BorderFactory.createEmptyBorder(5, 10, 5, 10)));
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                button.setBorder(new CompoundBorder(
                    BorderFactory.createLineBorder(color.darker(), 1),
                    BorderFactory.createEmptyBorder(5, 10, 5, 10)));
            }
        });
        
        return button;
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
            formPanel.clearForm();
            formPanel.setEditMode(false);
            // Update both condition panels when no plugin is selected
            startConditionPanel.setSelectScheduledPlugin(null);
            stopConditionPanel.setSelectScheduledPlugin(null);
            
            if (plugin == null) {                
                return;
            }
            
            // Handle case where we have a plugin reference but nothing is selected
            log.info("No plugin selected for editing. Plugin: {}", plugin.getCleanName());
            // Find if this is the next scheduled plugin
            PluginScheduleEntry nextPlugin = this.plugin.getNextScheduledPlugin(false,null).orElse(selected);
            if (nextPlugin == null) {
                log.warn("No plugin selected for editing conditions and no next scheduled plugin found.");                        
            } else {
                tablePanel.selectPlugin(nextPlugin);
                log.info("No plugin selected for editing conditions, selecting next scheduled plugin: {}", nextPlugin.getCleanName());                    
            }
            selected = nextPlugin;
        }
        
        // Update form panel with selection
        formPanel.loadPlugin(selected);
        formPanel.setEditMode(true);
        
        // Update condition panels based on the current tab
        int currentTabIndex = tabbedPane.getSelectedIndex();
        if (currentTabIndex == 1) { // Start Conditions tab
            startConditionPanel.setSelectScheduledPlugin(selected);
        } else if (currentTabIndex == 2) { // Stop Conditions tab
            stopConditionPanel.setSelectScheduledPlugin(selected);
        }
        
        // Always update control button when selection changes
        formPanel.updateControlButton();
    }

    /**
     * Processes the addition of a new plugin from the form data.
     * Checks for stop conditions and prompts user if none are configured.
     */
    private void onAddPlugin() {
        PluginScheduleEntry scheduledPlugin = formPanel.getPluginFromForm(null);
        if (scheduledPlugin == null) return;

        // Check if the plugin has stop conditions
        if (scheduledPlugin.getStopConditionManager().getConditions().isEmpty()) {
            // Check if this plugin needs time-based stop conditions (from checkbox)
            if (scheduledPlugin.isNeedsStopCondition()) {
                int result = JOptionPane.showConfirmDialog(this,
                        "No stop conditions are set. The plugin will run until manually stopped.\n" +
                        "Would you like to configure stop conditions now?",
                        "No Stop Conditions",
                        JOptionPane.YES_NO_CANCEL_OPTION);
                        
                if (result == JOptionPane.YES_OPTION) {
                    // Add the plugin first (disabled by default) so we can set conditions on it
                    scheduledPlugin.setEnabled(false);
                    plugin.addScheduledPlugin(scheduledPlugin);
                    plugin.saveScheduledPlugins();
                    refresh();
                    log.info("Plugin added without conditions: row count" + tablePanel.getRowCount());                    
                    // Select the newly added plugin
                    tablePanel.selectPlugin(scheduledPlugin);
                    // Switch to stop conditions tab
                    tabbedPane.setSelectedIndex(2);
                    return;
                } else if (result == JOptionPane.CANCEL_OPTION) {
                    scheduledPlugin.setEnabled(false); // Set to disabled by default
                    return; // Cancel the operation
                }
                // If NO, continue with adding plugin without conditions
            }
        }
        
        // Add the plugin (disabled by default for safety)
        scheduledPlugin.setEnabled(false);
        plugin.addScheduledPlugin(scheduledPlugin);
        plugin.saveScheduledPlugins();
        refresh();
        
        // Select the newly added plugin
        tablePanel.selectPlugin(scheduledPlugin);
        
        // Show a hint about enabling the plugin
        JOptionPane.showMessageDialog(this,
                "Plugin added successfully (currently disabled).\n" +
                "Enable it in the Properties tab when you're ready to schedule it.",
                "Plugin Added",
                JOptionPane.INFORMATION_MESSAGE);
        
        // Switch to the Properties tab in the form panel
        if (formPanel.getComponent(0) instanceof JTabbedPane) {
            ((JTabbedPane)formPanel.getComponent(0)).setSelectedIndex(1);
        }
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
            // Apply form values to the selected plugin
            formPanel.getPluginFromForm(selectedPlugin);
            
            // Update the UI
            plugin.saveScheduledPlugins();
            tablePanel.refreshTable();
            
            // Clear edit mode and selection to encourage users to review the changes
            formPanel.setEditMode(false);
            tablePanel.clearSelection();
            
            JOptionPane.showMessageDialog(this,
                "Plugin schedule updated successfully!",
                "Update Success",
                JOptionPane.INFORMATION_MESSAGE);
            
        } catch (Exception e) {
            log.error("Error updating plugin: {}", e.getMessage(), e);
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
        PluginScheduleEntry selectedPlugin = tablePanel.getSelectedPlugin();
        if (selectedPlugin == null) {
            return;
        }
        
        // Confirm deletion
        int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to remove '" + selectedPlugin.getCleanName() + "' from the schedule?",
                "Confirm Removal",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
                
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        
        // Stop the plugin if it's running
        if (plugin.getCurrentPlugin()!=null && plugin.getCurrentPlugin().equals(selectedPlugin) && selectedPlugin.isRunning()) {

            plugin.forceStopCurrentPluginScheduleEntry(false);
        }
        
        // Remove from schedule
        plugin.removeScheduledPlugin(selectedPlugin);
        plugin.saveScheduledPlugins();
        
        // Update UI
        tablePanel.refreshTable();
        formPanel.clearForm();
        startConditionPanel.setSelectScheduledPlugin(null);
        stopConditionPanel.setSelectScheduledPlugin(null);
        
        JOptionPane.showMessageDialog(this,
            "Plugin removed from schedule.",
            "Plugin Removed",
            JOptionPane.INFORMATION_MESSAGE);
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
