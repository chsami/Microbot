package net.runelite.client.plugins.microbot.pluginscheduler.ui.PluginScheduleEntry;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.SchedulerWindow;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerConfig;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.DayOfWeekCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.SingleTriggerTimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeWindowCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.ui.TimeConditionPanelUtil;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
@Slf4j
public class ScheduleFormPanel extends JPanel {
    private final SchedulerPlugin plugin;

    @Getter
    private JComboBox<String> pluginComboBox;
    private JComboBox<String> timeConditionTypeComboBox;
    private JCheckBox randomSchedulingCheckbox;
    private JCheckBox timeBasedStopConditionCheckbox;
    @Getter
    private JSpinner prioritySpinner;
    private JCheckBox defaultPluginCheckbox;

    // Condition config panels
    private JPanel conditionConfigPanel;
    private JPanel currentConditionPanel;

    private JButton addButton;
    private JButton updateButton;
    private JButton removeButton;
    private JButton controlButton;

    private PluginScheduleEntry selectedPlugin;

    // Constants for time condition types
    private static final String CONDITION_DEFAULT = "Run Default";
    private static final String CONDITION_SPECIFIC_TIME = "Run at Specific Time";
    private static final String CONDITION_INTERVAL = "Run at Interval";
    private static final String CONDITION_TIME_WINDOW = "Run in Time Window";
    private static final String CONDITION_DAY_OF_WEEK = "Run on Day of Week";
    private static final String[] TIME_CONDITION_TYPES = {
            CONDITION_DEFAULT,
            CONDITION_SPECIFIC_TIME,
            CONDITION_INTERVAL,
            CONDITION_TIME_WINDOW,
            CONDITION_DAY_OF_WEEK
    };

    // Add fields and methods for the selection change listener
    private Runnable selectionChangeListener;

    public void setSelectionChangeListener(Runnable listener) {
        this.selectionChangeListener = listener;
    }

    public ScheduleFormPanel(SchedulerPlugin plugin) {
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
                        BorderFactory.createEmptyBorder(5, 5, 5, 5)),
                "Schedule Configuration",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                FontManager.getRunescapeBoldFont(),
                Color.WHITE));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Set minimum size
        setMinimumSize(new Dimension(350, 300));
        setPreferredSize(new Dimension(400, 500));

        // Create the form panel with GridBagLayout for flexibility
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0; // Make components expand horizontally
        gbc.anchor = GridBagConstraints.WEST;

        // Plugin selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        JLabel pluginLabel = new JLabel("Plugin:");
        pluginLabel.setForeground(Color.WHITE);
        pluginLabel.setFont(FontManager.getRunescapeFont());
        formPanel.add(pluginLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        pluginComboBox = new JComboBox<>();
        formPanel.add(pluginComboBox, gbc);
        updatePluginList(plugin.getAvailablePlugins());

        // Add listener to clear table selection when ComboBox changes
        pluginComboBox.addActionListener(e -> {
            // Notify that ComboBox was manually changed so table selection can be cleared
            if (selectionChangeListener != null) {
                selectionChangeListener.run();
            }
        });

        // Time condition type selection
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        JLabel conditionTypeLabel = new JLabel("Schedule Type:");
        conditionTypeLabel.setForeground(Color.WHITE);
        conditionTypeLabel.setFont(FontManager.getRunescapeFont());
        formPanel.add(conditionTypeLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        timeConditionTypeComboBox = new JComboBox<>(TIME_CONDITION_TYPES);
        timeConditionTypeComboBox.addActionListener(e -> updateConditionPanel());
        formPanel.add(timeConditionTypeComboBox, gbc);

        // Dynamic condition config panel
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 4;
        conditionConfigPanel = new JPanel(new BorderLayout());
        conditionConfigPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        conditionConfigPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        formPanel.add(conditionConfigPanel, gbc);

        // Random scheduling checkbox
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 4;
        randomSchedulingCheckbox = new JCheckBox("Allow random scheduling");
        randomSchedulingCheckbox.setSelected(true);
        randomSchedulingCheckbox.setToolTipText(
            "<html>When enabled, this plugin can be randomly selected when multiple plugins are due to run.<br>" +
            "If disabled, this plugin will have higher priority than randomizable plugins.</html>");
        randomSchedulingCheckbox.setForeground(Color.WHITE);
        randomSchedulingCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        formPanel.add(randomSchedulingCheckbox, gbc);
        
        // Time-based stop condition checkbox
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 4;
        timeBasedStopConditionCheckbox = new JCheckBox("Requires time-based stop condition");
        timeBasedStopConditionCheckbox.setSelected(false);
        timeBasedStopConditionCheckbox.setToolTipText(
            "<html>When enabled, the scheduler will prompt you to add a time-based stop condition for this plugin.<br>" +
            "This helps prevent plugins from running indefinitely if other stop conditions don't trigger.</html>");
        timeBasedStopConditionCheckbox.setForeground(Color.WHITE);
        timeBasedStopConditionCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        formPanel.add(timeBasedStopConditionCheckbox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
        JLabel priorityLabel = new JLabel("Priority:");
        priorityLabel.setForeground(Color.WHITE);
        priorityLabel.setFont(FontManager.getRunescapeFont());
        formPanel.add(priorityLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        prioritySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        prioritySpinner.setToolTipText("Higher priority plugins will be scheduled before lower priority ones");
        formPanel.add(prioritySpinner, gbc);

        gbc.gridx = 2;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        defaultPluginCheckbox = new JCheckBox("Set as default plugin");
        defaultPluginCheckbox.setSelected(false);
        defaultPluginCheckbox.setToolTipText(
            "<html>When enabled, this plugin is marked as a default option.<br>" +
            "Non-default plugins will always be scheduled first.</html>");
        defaultPluginCheckbox.setForeground(Color.WHITE);
        defaultPluginCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        formPanel.add(defaultPluginCheckbox, gbc);

        // Wrap the formPanel in a scroll pane
        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Add the scroll pane to the center
        add(scrollPane, BorderLayout.CENTER);

        // Create button panel
        JPanel buttonPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttonPanel.setBorder(new EmptyBorder(5, 0, 0, 0));

        addButton = createButton("Add Schedule", ColorScheme.BRAND_ORANGE_TRANSPARENT);
        updateButton = createButton("Update Schedule", ColorScheme.BRAND_ORANGE);
        removeButton = createButton("Remove Schedule", ColorScheme.PROGRESS_ERROR_COLOR);

        // Control button (Run Now/Stop)
        controlButton = createButton("Run Now", ColorScheme.PROGRESS_COMPLETE_COLOR);
        controlButton.addActionListener(this::onControlButtonClicked);

        buttonPanel.add(addButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(controlButton);

        // Add button panel to the bottom
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Initialize the condition panel
        updateConditionPanel();
    }

    /**
     * Updates the condition configuration panel based on the selected time condition type
     */
    private void updateConditionPanel() {
        // Clear existing panel
        if (currentConditionPanel != null) {
            conditionConfigPanel.remove(currentConditionPanel);
        }
        
        // Create a new panel based on selection
        String selectedType = (String) timeConditionTypeComboBox.getSelectedItem();
        currentConditionPanel = new JPanel();
        currentConditionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 4;
        
        // Create the appropriate condition panel
        if (CONDITION_DEFAULT.equals(selectedType)) {
            // No configuration needed for default execution mode
            JLabel nowLabel = new JLabel("Plugin will be scheduled as a default option");
            nowLabel.setForeground(Color.WHITE);
            currentConditionPanel.add(nowLabel);
            
            // Force default checkbox to be checked and disable it
            defaultPluginCheckbox.setSelected(true);
            defaultPluginCheckbox.setEnabled(false);
            
            // Set priority to 0 for default plugins and disable the spinner
            prioritySpinner.setValue(0);
            prioritySpinner.setEnabled(false);
        } 
        else {
            // For non-default scheduling types, enable the default checkbox and priority spinner
            defaultPluginCheckbox.setEnabled(true);
            prioritySpinner.setEnabled(true);
            
            // Continue with other panel types...
            if (CONDITION_SPECIFIC_TIME.equals(selectedType)) {
                // Use TimeConditionPanelUtil instead of ConditionConfigPanelUtil
                currentConditionPanel.setLayout(new GridBagLayout());
                TimeConditionPanelUtil.createSingleTriggerConfigPanel(currentConditionPanel, gbc, currentConditionPanel);
            }
            else if (CONDITION_INTERVAL.equals(selectedType)) {
                // Use TimeConditionPanelUtil instead of ConditionConfigPanelUtil
                currentConditionPanel.setLayout(new GridBagLayout());
                TimeConditionPanelUtil.createIntervalConfigPanel(currentConditionPanel, gbc, currentConditionPanel);
            }
            else if (CONDITION_TIME_WINDOW.equals(selectedType)) {
                // Use TimeConditionPanelUtil instead of ConditionConfigPanelUtil
                currentConditionPanel.setLayout(new GridBagLayout());
                TimeConditionPanelUtil.createEnhancedTimeWindowConfigPanel(currentConditionPanel, gbc, currentConditionPanel);
            }else if (CONDITION_DAY_OF_WEEK.equals(selectedType)) {
                // Use TimeConditionPanelUtil instead of ConditionConfigPanelUtil
                currentConditionPanel.setLayout(new GridBagLayout());
                TimeConditionPanelUtil.createDayOfWeekConfigPanel(currentConditionPanel, gbc, currentConditionPanel);
            } else {
                // Default to empty panel
                currentConditionPanel.add(new JLabel("No configuration available for this type."));
            }
            
        }
        
        // Add the panel
        conditionConfigPanel.add(currentConditionPanel, BorderLayout.CENTER);
        conditionConfigPanel.revalidate();
        conditionConfigPanel.repaint();
    }

    private JButton createButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 1),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        });

        return button;
    }

    public void updatePluginList(List<String> plugins) {
        if (plugins == null || plugins.isEmpty()) {
            return;
        }

        pluginComboBox.removeAllItems();
        for (String plugin : plugins) {
            pluginComboBox.addItem(plugin);
        }
    }

    public void loadPlugin(PluginScheduleEntry entry) {
        
        if (entry == null ) {
            if ( this.selectedPlugin != null){
                clearForm();            
            }   
            this.selectedPlugin = null;  
            
            return;
        }
        if (entry.equals(selectedPlugin)){            
            return; // No need to update if the same plugin is selected
        }
        this.selectedPlugin = entry;    
        


        
        
        // Block combobox events temporarily to avoid feedback loops
        ActionListener[] listeners = pluginComboBox.getActionListeners();
        for (ActionListener listener : listeners) {
            pluginComboBox.removeActionListener(listener);
        }
        log.info("Loading plugin: {}", entry.getName());
        // Update plugin selection
        pluginComboBox.setSelectedItem(entry.getName());
        
        // Re-add listeners
        for (ActionListener listener : listeners) {
            pluginComboBox.addActionListener(listener);
        }
        
        // Set random scheduling checkbox
        randomSchedulingCheckbox.setSelected(entry.isAllowRandomScheduling());
        
        // Set time-based stop condition checkbox
        timeBasedStopConditionCheckbox.setSelected(entry.isNeedsStopCondition());
        
        // Set priority spinner
        prioritySpinner.setValue(entry.getPriority());
        
        // Set default checkbox
        defaultPluginCheckbox.setSelected(entry.isDefault());
        
        // Determine if this is a default plugin with 1-second interval
        boolean isDefaultPlugin = entry.isDefault() || 
                                  entry.getIntervalDisplay().contains("Every 1 second");
        
        // Determine the time condition type and set appropriate panel
        TimeCondition startCondition = null;
        if (entry.getStartConditionManager() != null) {
            List<TimeCondition> timeConditions = entry.getStartConditionManager().getTimeConditions();
            if (!timeConditions.isEmpty()) {
                startCondition = timeConditions.get(0);
            }
        }
        if (startCondition == null) {
            log.warn("No start condition found for plugin: {}", entry.getName());
            return;
        }
        
        TimeCondition mainStartCondition = entry.getMainTimeStartCondition();
        
        // Block combobox events again for condition type changes
        ActionListener[] conditionListeners = timeConditionTypeComboBox.getActionListeners();
        for (ActionListener listener : conditionListeners) {
            timeConditionTypeComboBox.removeActionListener(listener);
        }
        
        // If it's a default plugin (by flag or by interval), show "Run Default"
        
        if (startCondition instanceof SingleTriggerTimeCondition) {
            timeConditionTypeComboBox.setSelectedItem(CONDITION_SPECIFIC_TIME);
            updateConditionPanel();            
            // Configure the panel with existing values
            setupTimeConditionPanel(startCondition);
        }
        else if (startCondition instanceof IntervalCondition) {
            
            Optional<Duration> nextTriger = startCondition.getDurationUntilNextTrigger();
            IntervalCondition interval = (IntervalCondition) startCondition;

            if (interval.getInterval().getSeconds() <= 1) {                
                timeConditionTypeComboBox.setSelectedItem(CONDITION_DEFAULT);
            }else{
                // Configure the panel with existing values
                timeConditionTypeComboBox.setSelectedItem(CONDITION_INTERVAL);    
            
            }
            updateConditionPanel();
            
            setupTimeConditionPanel(startCondition);
            
        }
        else if (startCondition instanceof TimeWindowCondition) {
            timeConditionTypeComboBox.setSelectedItem(CONDITION_TIME_WINDOW);
            updateConditionPanel();            
            // Configure the panel with existing values
            setupTimeConditionPanel(startCondition);
        }
        else if (startCondition instanceof DayOfWeekCondition) {
            // Handle day of week condition if needed
            timeConditionTypeComboBox.setSelectedItem(CONDITION_DAY_OF_WEEK);
            updateConditionPanel();
            setupTimeConditionPanel(startCondition);
        }
        else if (isDefaultPlugin) {
            timeConditionTypeComboBox.setSelectedItem(CONDITION_DEFAULT);
            updateConditionPanel();
        }
        else {
            // Default to "Run Default"
            timeConditionTypeComboBox.setSelectedItem(CONDITION_DEFAULT);
            updateConditionPanel();
        }
        
        // Re-add condition type listeners
        for (ActionListener listener : conditionListeners) {
            timeConditionTypeComboBox.addActionListener(listener);
        }
        
        // Update the control button to reflect the current plugin
        updateControlButton();
    }

    public void clearForm() {
        
        this.selectedPlugin = null;  
        if (pluginComboBox.getItemCount() > 0) {
            
            pluginComboBox.setSelectedIndex(0);
        }

        // Reset condition type to default
        timeConditionTypeComboBox.setSelectedItem(CONDITION_DEFAULT);
        updateConditionPanel();
        
        // Reset random scheduling
        randomSchedulingCheckbox.setSelected(true);
        
        // Update the control button
        updateControlButton();
    }

    public PluginScheduleEntry getPluginFromForm(PluginScheduleEntry existingPlugin) {
        String pluginName = (String) pluginComboBox.getSelectedItem();
        if (pluginName == null || pluginName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
            "Please select a plugin.",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        
        // Create TimeCondition based on selected type
        String selectedType = (String) timeConditionTypeComboBox.getSelectedItem();
        TimeCondition timeCondition = null;
        
        // Flag to track if this is a default plugin by schedule type
        boolean isDefaultByScheduleType = false;
        
        if (CONDITION_DEFAULT.equals(selectedType)) {
            // For default plugin, create 1-second interval condition
            timeCondition = new IntervalCondition(Duration.ofSeconds(1));
            isDefaultByScheduleType = true;
        }
        else if (CONDITION_SPECIFIC_TIME.equals(selectedType)) {
            // Create SingleTriggerTimeCondition using the utility
            timeCondition = TimeConditionPanelUtil.createSingleTriggerCondition(currentConditionPanel);
        }
        else if (CONDITION_INTERVAL.equals(selectedType)) {
            // Create IntervalCondition using TimeConditionPanelUtil
            timeCondition = TimeConditionPanelUtil.createIntervalCondition(currentConditionPanel);
        }
        else if (CONDITION_TIME_WINDOW.equals(selectedType)) {
            // Use TimeConditionPanelUtil instead of ConditionConfigPanelUtil
            timeCondition = TimeConditionPanelUtil.createEnhancedTimeWindowCondition(currentConditionPanel);
        }
        
        if (timeCondition == null) {
            JOptionPane.showMessageDialog(this,
                    "Failed to create time condition.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        PluginScheduleEntry entry = null;
        if (existingPlugin == null) {
            // Create a new plugin schedule entry
            entry= new PluginScheduleEntry(
                pluginName,
                timeCondition,
                true,  // enabled by default
                randomSchedulingCheckbox.isSelected());
        } else {
            // Update the selected plugin's properties but keep its identity
            //existingPlugin.setName(updatedConfig.getName());
            //existingPlugin.setEnabled(updatedConfig.isEnabled());
            //existingPlugin.setAllowRandomScheduling(updatedConfig.isAllowRandomScheduling());
            //existingPlugin.setPriority(updatedConfig.getPriority());
            //existingPlugin.setDefault(updatedConfig.isDefault());
                  
            existingPlugin.updatePrimaryTimeCondition((TimeCondition) timeCondition);     
            entry = existingPlugin;  
        }
        // Create the plugin schedule entry with the time condition
        
        
        // Set priority based on whether this is a default plugin
        if (isDefaultByScheduleType) {
            // Force priority to 0 for default plugins
            entry.setPriority(0);
        } else {
            entry.setPriority((Integer) prioritySpinner.getValue());
        }
        
        // If it's default by schedule type, force default to true, otherwise use checkbox value
        entry.setDefault(isDefaultByScheduleType || defaultPluginCheckbox.isSelected());
        
        // Set the time-based stop condition flag
        entry.setNeedsStopCondition(timeBasedStopConditionCheckbox.isSelected());
        
        return entry;
    }

    public void updateControlButton() {
        PluginScheduleEntry runningPlugin = plugin.getCurrentPlugin();
        boolean isPluginRunning = plugin.isScheduledPluginRunning();
        
        if (isPluginRunning && runningPlugin != null) {
            // If a plugin is running, show the "Stop Plugin" button
            controlButton.setText("Stop \"" + runningPlugin.getCleanName() + "\"");
            controlButton.setEnabled(true);
            
            // Change color to indicate stopping action
            controlButton.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.PROGRESS_ERROR_COLOR.darker(), 1),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        } else if (selectedPlugin != null) {
            // Check if this plugin's next scheduled time is beyond the configurable threshold
            boolean canBeManuallyStarted = true;
            String disabledReason = "";
            SchedulerConfig schedulerConfig = plugin.provideConfig(Microbot.getConfigManager());
            int minThresholdMinutes = 0;
            if (schedulerConfig != null) {
                minThresholdMinutes = schedulerConfig.minManualStartThresholdMinutes();                
            }
            // Only perform this check if the plugin has a scheduled time
            if (selectedPlugin.getCurrentStartTriggerTime().isPresent()) {
                // Get the minimum time threshold from config (in minutes)
               
                
                
                // Get the time until this plugin's next scheduled run
                java.time.Duration timeUntilScheduled = java.time.Duration.between(
                    java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault()),
                    selectedPlugin.getCurrentStartTriggerTime().get()
                );
                
                // If the plugin is scheduled to run within the threshold time, don't allow manual start
                if (timeUntilScheduled.toMinutes() < minThresholdMinutes) {
                    canBeManuallyStarted = false;
                    disabledReason = String.format(" (scheduled in %d minutes)", timeUntilScheduled.toMinutes());
                }
            }
            
            // If no plugin is running but one is selected, conditionally enable "Run Now" for that plugin
            if (canBeManuallyStarted) {
                controlButton.setText("Run \"" + selectedPlugin.getCleanName() + "\" Now");
                controlButton.setEnabled(true);
                controlButton.setToolTipText(null); // Clear any previous tooltip
                
                // Change color to indicate starting action
                controlButton.setBorder(new CompoundBorder(
                    BorderFactory.createLineBorder(ColorScheme.PROGRESS_COMPLETE_COLOR.darker(), 1),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)));
            } else {
                controlButton.setText("Run \"" + selectedPlugin.getCleanName() + "\" Scheduled" + disabledReason);
                controlButton.setEnabled(false);
                controlButton.setToolTipText("This plugin is scheduled to run soon. Wait until after the minimum threshold time (" + 
                                                minThresholdMinutes + 
                                             " minutes) to manually start it.");
                
                // Use a disabled color
                controlButton.setBorder(new CompoundBorder(
                    BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR.darker(), 1),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)));
            }
        } else {
            // If no plugin is selected, disable the button
            controlButton.setText("Select a Plugin");
            controlButton.setEnabled(false);
            controlButton.setToolTipText(null); // Clear any previous tooltip
            
            // Reset button color
            controlButton.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.darker(), 1),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        }
    }

    private void onControlButtonClicked(ActionEvent e) {
        if (plugin.isScheduledPluginRunning()) {
            // Stop the currently running plugin
            log.info("Stopping currently running plugin via control button");
            
            // Show confirmation dialog before stopping
            PluginScheduleEntry currentPlugin = plugin.getCurrentPlugin();
            String pluginName = currentPlugin != null ? currentPlugin.getCleanName() : "Unknown";
            
            int result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to stop the running plugin \"" + pluginName + "\"?",
                "Confirm Stop Plugin",
                JOptionPane.YES_NO_OPTION
            );
            
            if (result == JOptionPane.YES_OPTION) {
                // User confirmed - stop the plugin
                plugin.forceStopCurrentPluginScheduleEntry(true);
                
                // Update UI after stopping
                SwingUtilities.invokeLater(() -> {
                    updateControlButton();
                });
            }
        } else if (selectedPlugin != null) {
            // Start the selected plugin
            log.info("Starting plugin \"{}\" via control button", selectedPlugin.getCleanName());
            
            // Check if the plugin has any stop conditions if enforcement is enabled
            if (plugin.provideConfig(null).enforceTimeBasedStopCondition() && 
                selectedPlugin.getStopConditionManager().getConditions().isEmpty()) {
                
                int result = JOptionPane.showConfirmDialog(
                    this,
                    "Plugin \"" + selectedPlugin.getCleanName() + "\" has no stop conditions set.\n" +
                    "It will run until manually stopped.\n\n" +
                    "Would you like to configure stop conditions first?",
                    "No Stop Conditions",
                    JOptionPane.YES_NO_CANCEL_OPTION
                );
                
                if (result == JOptionPane.YES_OPTION) {
                    // Open the stop conditions tab in the scheduler window
                    SwingUtilities.invokeLater(() -> {
                        if (SwingUtilities.getWindowAncestor(this) instanceof SchedulerWindow) {
                            SchedulerWindow window = (SchedulerWindow) SwingUtilities.getWindowAncestor(this);
                            window.switchToStopConditionsTab();
                        }
                    });
                    selectedPlugin.setNeedsStopCondition(true);
                    return;
                } else if (result == JOptionPane.CANCEL_OPTION) {
                    selectedPlugin.setNeedsStopCondition(false);
                    return; // Don't start the plugin
                }
                selectedPlugin.setNeedsStopCondition(false);
                // If NO, continue to start the plugin without stop conditions
            }
            
            // Start the plugin
            plugin.startPluginScheduleEntry(selectedPlugin);
            
            // Update UI after starting
            SwingUtilities.invokeLater(() -> {
                updateControlButton();
            });
        }
    }

    public void setEditMode(boolean editMode) {
        updateButton.setEnabled(true);
        removeButton.setEnabled(true);
        addButton.setEnabled(true);

        // Update control button based on edit mode
        updateControlButton();
    }

    public void setAddButtonAction(ActionListener listener) {
        addButton.addActionListener(listener);
    }

    public void setUpdateButtonAction(ActionListener listener) {
        updateButton.addActionListener(listener);
    }

    public void setRemoveButtonAction(ActionListener listener) {
        removeButton.addActionListener(listener);
    }
   
    // Add a new method to sync with table selection
    public void syncWithTableSelection(PluginScheduleEntry selectedPlugin) {
        // Only update if different than current selection to avoid feedback loops
        if (selectedPlugin == null) {
            return;
        }        

        if (selectedPlugin != null && !selectedPlugin.getName().equals(pluginComboBox.getSelectedItem())) {
            // Block action listener temporarily to avoid side effects
            ActionListener[] listeners = pluginComboBox.getActionListeners();
            for (ActionListener listener : listeners) {
                pluginComboBox.removeActionListener(listener);
            }
            
            // Update selection
            pluginComboBox.setSelectedItem(selectedPlugin.getName());
            
            // Re-add listeners
            for (ActionListener listener : listeners) {
                pluginComboBox.addActionListener(listener);
            }
        }
    }

    /**
     * Sets up the time condition panel with values from an existing condition
     */
    private void setupTimeConditionPanel(TimeCondition condition) {
        if (condition == null || currentConditionPanel == null) {
            log.warn("Condition or current condition panel is null");
            return;
        }        
        // Delegate to the utility class
        TimeConditionPanelUtil.setupTimeCondition(currentConditionPanel, condition);
    }
}
