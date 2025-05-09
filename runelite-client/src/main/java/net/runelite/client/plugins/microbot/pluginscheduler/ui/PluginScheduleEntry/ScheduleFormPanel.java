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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

    // New panel for editing plugin properties when one is selected
    private JPanel pluginPropertiesPanel;
    private JSpinner selectedPluginPrioritySpinner;
    private JCheckBox selectedPluginDefaultCheckbox;
    private JCheckBox selectedPluginEnabledCheckbox;
    private JCheckBox selectedPluginRandomCheckbox;
    private JCheckBox selectedPluginTimeStopCheckbox;

    // Statistics labels
    private JLabel selectedPluginNameLabel;
    private JLabel runsLabel;
    private JLabel lastRunLabel;
    private JLabel lastDurationLabel;
    private JLabel lastStopReasonLabel;
    private JButton saveChangesButton;

    // Condition config panels
    private JPanel conditionConfigPanel;
    private JPanel currentConditionPanel;

    private JButton addButton;
    private JButton updateButton;
    private JButton removeButton;
    private JButton controlButton;
    private JTabbedPane tabbedPane;
    private PluginScheduleEntry selectedPlugin;
    
    // Flag to prevent update loops
    private boolean updatingValues = false;

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
        
        // Create a tabbed pane to separate plugin selection and properties
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tabbedPane.setForeground(Color.WHITE);
        tabbedPane.setFont(FontManager.getRunescapeFont());
        
        // Add tabs
        tabbedPane.addTab("New Schedule", createScheduleFormPanel());
        tabbedPane.addTab("Properties", createPropertiesPanel());
        
        add(tabbedPane, BorderLayout.CENTER);

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
     * Creates the main schedule form panel for adding new schedules
     */
    private JScrollPane createScheduleFormPanel() {
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
            if (pluginComboBox.getSelectedItem() != null && selectionChangeListener != null) {
                selectionChangeListener.run();
            }
        });

   
        
        // Plugin settings section with improved UI
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 4;
        JPanel pluginSettingsPanel = new JPanel(new BorderLayout());
        pluginSettingsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pluginSettingsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                "Plugin Settings",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                FontManager.getRunescapeBoldFont(),
                Color.WHITE
        ));

        // Create a panel for the checkboxes with horizontal layout
        JPanel checkboxesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        checkboxesPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Random scheduling checkbox
        randomSchedulingCheckbox = new JCheckBox("Allow random scheduling");
        randomSchedulingCheckbox.setSelected(true);
        randomSchedulingCheckbox.setToolTipText(
            "<html>When enabled, this plugin can be randomly selected when multiple plugins are due to run.<br>" +
            "If disabled, this plugin will have higher priority than randomizable plugins.</html>");
        randomSchedulingCheckbox.setForeground(Color.WHITE);
        randomSchedulingCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        checkboxesPanel.add(randomSchedulingCheckbox);

        // Time-based stop condition checkbox
        timeBasedStopConditionCheckbox = new JCheckBox("Requires time-based stop condition");
        timeBasedStopConditionCheckbox.setSelected(false);
        timeBasedStopConditionCheckbox.setToolTipText(
            "<html>When enabled, the scheduler will prompt you to add a time-based stop condition for this plugin.<br>" +
            "This helps prevent plugins from running indefinitely if other stop conditions don't trigger.</html>");
        timeBasedStopConditionCheckbox.setForeground(Color.WHITE);
        timeBasedStopConditionCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        checkboxesPanel.add(timeBasedStopConditionCheckbox);

        // Add checkboxes panel to the top
        pluginSettingsPanel.add(checkboxesPanel, BorderLayout.NORTH);

        // Priority and Default panel - improved layout with better spacing
        JPanel priorityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        priorityPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Priority label and spinner in one group
        JLabel priorityLabel = new JLabel("Priority:");
        priorityLabel.setForeground(Color.WHITE);
        priorityLabel.setFont(FontManager.getRunescapeFont());
        priorityPanel.add(priorityLabel);

        prioritySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        prioritySpinner.setToolTipText("Higher priority plugins will be scheduled before lower priority ones");
        prioritySpinner.setPreferredSize(new Dimension(60, 28));
        priorityPanel.add(prioritySpinner);

        // Add some spacing
        priorityPanel.add(Box.createHorizontalStrut(20));

        // Default plugin checkbox
        defaultPluginCheckbox = new JCheckBox("Set as default plugin");
        defaultPluginCheckbox.setSelected(false);
        defaultPluginCheckbox.setToolTipText(
            "<html>When enabled, this plugin is marked as a default option.<br>" +
            "Default plugins always have priority 0.<br>" +
            "Non-default plugins will always be scheduled first.</html>");
        defaultPluginCheckbox.setForeground(Color.WHITE);
        defaultPluginCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        priorityPanel.add(defaultPluginCheckbox);

        // Synchronize priority and default checkbox
        prioritySpinner.addChangeListener(e -> {
            if (updatingValues) return;
            updatingValues = true;
            int value = (Integer) prioritySpinner.getValue();
            defaultPluginCheckbox.setSelected(value == 0);
            updatingValues = false;
        });

        defaultPluginCheckbox.addItemListener(e -> {
            if (updatingValues) return;
            updatingValues = true;
            if (e.getStateChange() == ItemEvent.SELECTED) {
                prioritySpinner.setValue(0); // Default plugins always have priority 0
            } else if ((Integer) prioritySpinner.getValue() == 0) {
                prioritySpinner.setValue(1); // If unchecking default and priority is 0, set to 1
            }
            updatingValues = false;
        });

        // Add priority panel to bottom
        pluginSettingsPanel.add(priorityPanel, BorderLayout.CENTER);

        formPanel.add(pluginSettingsPanel, gbc);
        // Time condition type selection
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        JLabel conditionTypeLabel = new JLabel("Schedule Type:");
        conditionTypeLabel.setForeground(Color.WHITE);
        conditionTypeLabel.setFont(FontManager.getRunescapeFont());
        formPanel.add(conditionTypeLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        timeConditionTypeComboBox = new JComboBox<>(TIME_CONDITION_TYPES);
        timeConditionTypeComboBox.addActionListener(e -> updateConditionPanel());
        formPanel.add(timeConditionTypeComboBox, gbc);

        // Dynamic condition config panel
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 4;
        conditionConfigPanel = new JPanel(new BorderLayout());
        conditionConfigPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        conditionConfigPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        conditionConfigPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        conditionConfigPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                "Main Start Condition",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                FontManager.getRunescapeBoldFont(),
                Color.WHITE
        ));
        formPanel.add(conditionConfigPanel, gbc);

        

        // Wrap the formPanel in a scroll pane
        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        return scrollPane;
    }
    
    /**
     * Creates a panel for editing selected plugin properties
     */
    private JScrollPane createPropertiesPanel() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 1.0;
        
        // Create a message for when no plugin is selected
        JPanel noSelectionPanel = new JPanel(new BorderLayout());
        noSelectionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        JLabel noSelectionLabel = new JLabel("Select a plugin from the table to edit its properties");
        noSelectionLabel.setForeground(Color.WHITE);
        noSelectionLabel.setFont(FontManager.getRunescapeFont());
        noSelectionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        noSelectionPanel.add(noSelectionLabel, BorderLayout.CENTER);
        
        // Create editor panel
        JPanel editorPanel = new JPanel(new GridBagLayout());
        editorPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Plugin name header
        selectedPluginNameLabel = new JLabel("Plugin Properties");
        selectedPluginNameLabel.setForeground(Color.WHITE);
        selectedPluginNameLabel.setFont(FontManager.getRunescapeBoldFont());
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        editorPanel.add(selectedPluginNameLabel, gbc);
        
        // Enabled checkbox
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        selectedPluginEnabledCheckbox = createPropertyCheckbox("Enabled", 
            "When checked, the plugin is eligible to be scheduled based on its conditions");
        editorPanel.add(selectedPluginEnabledCheckbox, gbc);
        
        // Default plugin checkbox
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        selectedPluginDefaultCheckbox = createPropertyCheckbox("Default Plugin", 
            "When checked, this plugin will be treated as a default plugin (priority 0) and scheduled after all others");
        editorPanel.add(selectedPluginDefaultCheckbox, gbc);
        
        // Priority spinner with improved alignment
        JPanel priorityGroupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        priorityGroupPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel priorityLabel = new JLabel("Priority:");
        priorityLabel.setForeground(Color.WHITE);
        priorityGroupPanel.add(priorityLabel);
        
        SpinnerModel priorityModel = new SpinnerNumberModel(1, 0, 100, 1);
        selectedPluginPrioritySpinner = new JSpinner(priorityModel);
        selectedPluginPrioritySpinner.setPreferredSize(new Dimension(60, 28));
        selectedPluginPrioritySpinner.setToolTipText("<html>Sets the priority for this plugin.<br>Higher priority = run first.<br>0 = default plugin (scheduled after all others)</html>");
        priorityGroupPanel.add(selectedPluginPrioritySpinner);
        
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        editorPanel.add(priorityGroupPanel, gbc);
        
        // Random scheduling checkbox
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        selectedPluginRandomCheckbox = createPropertyCheckbox("Allow Random Scheduling", 
            "When enabled, the scheduler will apply some randomization to when this plugin runs");
        editorPanel.add(selectedPluginRandomCheckbox, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        selectedPluginTimeStopCheckbox = createPropertyCheckbox("Requires Time-based Stop Condition", 
            "When enabled, the scheduler will prompt you to add a time-based stop condition for this plugin.");
        editorPanel.add(selectedPluginTimeStopCheckbox, gbc);
        
        // Plugin run statistics
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        JPanel statsPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        statsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                "Statistics",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                FontManager.getRunescapeFont(),
                Color.WHITE));
        
        // Statistics info labels
        runsLabel = new JLabel("Total Runs: 0");
        runsLabel.setForeground(Color.WHITE);
        lastRunLabel = new JLabel("Last Run: Never");
        lastRunLabel.setForeground(Color.WHITE);
        lastDurationLabel = new JLabel("Last Duration: N/A");
        lastDurationLabel.setForeground(Color.WHITE);
        lastStopReasonLabel = new JLabel("Last Stop Reason: N/A");
        lastStopReasonLabel.setForeground(Color.WHITE);
        
        statsPanel.add(runsLabel);
        statsPanel.add(lastRunLabel);
        statsPanel.add(lastDurationLabel);
        statsPanel.add(lastStopReasonLabel);
        
        gbc.gridx = 0;
        gbc.gridy = 7;
        editorPanel.add(statsPanel, gbc);
        
        // Save changes button with prominent styling
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 2;
        saveChangesButton = new JButton("Save Changes");
        saveChangesButton.setBackground(new Color(76, 175, 80)); // Green
        saveChangesButton.setForeground(Color.WHITE);
        saveChangesButton.setFocusPainted(false);
        saveChangesButton.addActionListener(e -> updateSelectedPlugin());
        editorPanel.add(saveChangesButton, gbc);
        
        // Add property change listeners
        selectedPluginEnabledCheckbox.addItemListener(e -> {
            if (selectedPlugin != null) {
                selectedPlugin.setEnabled(selectedPluginEnabledCheckbox.isSelected());
                updateSelectedPlugin();
                updateControlButton();
                updateStatistics();
            }
        });
        
        selectedPluginRandomCheckbox.addItemListener(e -> {
            if (selectedPlugin != null) {
                selectedPlugin.setAllowRandomScheduling(selectedPluginRandomCheckbox.isSelected());
                updateSelectedPlugin();
            }
        });
        
        selectedPluginTimeStopCheckbox.addItemListener(e -> {
            if (selectedPlugin != null) {
                selectedPlugin.setNeedsStopCondition(selectedPluginTimeStopCheckbox.isSelected());
                updateSelectedPlugin();
            }
        });
        
        // Initialize with no selection panel
        pluginPropertiesPanel = new JPanel(new BorderLayout());
        pluginPropertiesPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pluginPropertiesPanel.add(noSelectionPanel, BorderLayout.CENTER);
        
        // Store panels as client properties for later retrieval
        pluginPropertiesPanel.putClientProperty("noSelectionPanel", noSelectionPanel);
        pluginPropertiesPanel.putClientProperty("editorPanel", editorPanel);

        // Link default checkbox and priority spinner
        selectedPluginDefaultCheckbox.addItemListener(e -> {
            if (updatingValues) return; // Skip if we're programmatically updating values
            
            updatingValues = true; // Set flag to prevent recursive updates
            try {
                if (selectedPlugin != null) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        selectedPluginPrioritySpinner.setValue(0); // Default plugins have priority 0
                    } else if ((Integer) selectedPluginPrioritySpinner.getValue() == 0) {
                        selectedPluginPrioritySpinner.setValue(1); // Non-default get priority 1
                    }
                    selectedPlugin.setDefault(e.getStateChange() == ItemEvent.SELECTED);
                    selectedPlugin.setPriority((Integer) selectedPluginPrioritySpinner.getValue());
                    updateSelectedPlugin();
                }
            } finally {
                updatingValues = false; // Always reset flag
            }
        });

        selectedPluginPrioritySpinner.addChangeListener(e -> {
            if (updatingValues) return; // Skip if we're programmatically updating values
            
            updatingValues = true; // Set flag to prevent recursive updates
            try {
                if (selectedPlugin != null) {
                    int priority = (Integer) selectedPluginPrioritySpinner.getValue();
                    selectedPlugin.setPriority(priority);
                    
                    // Update default checkbox based on priority value
                    boolean shouldBeDefault = priority == 0;
                    if (selectedPluginDefaultCheckbox.isSelected() != shouldBeDefault) {
                        selectedPluginDefaultCheckbox.setSelected(shouldBeDefault);
                    }
                    
                    selectedPlugin.setDefault(shouldBeDefault);
                    updateSelectedPlugin();
                }
            } finally {
                updatingValues = false; // Always reset flag
            }
        });

        // Wrap the formPanel in a scroll pane
        JScrollPane scrollPane = new JScrollPane(pluginPropertiesPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }
    
    /**
     * Helper method to create a styled checkbox for properties panel
     */
    private JCheckBox createPropertyCheckbox(String text, String tooltip) {
        JCheckBox checkbox = new JCheckBox(text);
        checkbox.setForeground(Color.WHITE);
        checkbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        checkbox.setToolTipText(tooltip);
        checkbox.setFocusPainted(false);
        return checkbox;
    }
    
    /**
     * Updates the statistics display in the properties panel
     */
    private void updateStatistics() {
        if (selectedPlugin == null) {
            runsLabel.setText("Total Runs: 0");
            lastRunLabel.setText("Last Run: Never");
            lastDurationLabel.setText("Last Duration: N/A");
            lastStopReasonLabel.setText("Last Stop Reason: N/A");
            return;
        }
        
        // Update run count
        runsLabel.setText("Total Runs: " + selectedPlugin.getRunCount());
        
        // Update last run time
        ZonedDateTime lastRunTime = selectedPlugin.getLastRunTime();
        if (lastRunTime != null) {
            lastRunLabel.setText("Last Run: " + lastRunTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } else {
            lastRunLabel.setText("Last Run: Never");
        }
        
        // Update duration if available
        if (selectedPlugin.getLastRunDuration() != null && !selectedPlugin.getLastRunDuration().isZero()) {
            Duration duration = selectedPlugin.getLastRunDuration();
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();
            lastDurationLabel.setText(String.format("Last Duration: %d:%02d:%02d", hours, minutes, seconds));
        } else {
            lastDurationLabel.setText("Last Duration: N/A");
        }
        
        // Update stop reason
        String stopReason = selectedPlugin.getLastStopReason();
        if (stopReason != null && !stopReason.isEmpty()) {
            if (stopReason.length() > 40) {
                stopReason = stopReason.substring(0, 37) + "...";
            }
            lastStopReasonLabel.setText("Last Stop: " + stopReason);
        } else {
            lastStopReasonLabel.setText("Last Stop: N/A");
        }
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
            JLabel defaultLabel = new JLabel("Default plugin with 1-second interval (always runs last)");
            defaultLabel.setForeground(Color.WHITE);
            currentConditionPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
            currentConditionPanel.add(defaultLabel);
        } 
        else if (CONDITION_SPECIFIC_TIME.equals(selectedType)) {
            currentConditionPanel.setLayout(new GridBagLayout());
            TimeConditionPanelUtil.createSingleTriggerConfigPanel(currentConditionPanel, gbc);
        }
        else if (CONDITION_INTERVAL.equals(selectedType)) {
            currentConditionPanel.setLayout(new GridBagLayout());
            TimeConditionPanelUtil.createIntervalConfigPanel(currentConditionPanel, gbc);
        }
        else if (CONDITION_TIME_WINDOW.equals(selectedType)) {
            currentConditionPanel.setLayout(new GridBagLayout());
            TimeConditionPanelUtil.createTimeWindowConfigPanel(currentConditionPanel, gbc);
        }
        else if (CONDITION_DAY_OF_WEEK.equals(selectedType)) {
            currentConditionPanel.setLayout(new GridBagLayout());
            TimeConditionPanelUtil.createDayOfWeekConfigPanel(currentConditionPanel, gbc);
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
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(color);
                button.setForeground(ColorScheme.DARK_GRAY_COLOR);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                button.setForeground(Color.WHITE);
            }
        });

        return button;
    }

    public void updatePluginList(List<String> plugins) {
        if (plugins == null || plugins.isEmpty()) {
            log.debug("No plugins available to populate combo box");
            return;
        }

        pluginComboBox.removeAllItems();
        for (String plugin : plugins) {
            pluginComboBox.addItem(plugin);
        }
    }

    public void loadPlugin(PluginScheduleEntry entry) {
        
        if (entry == null ) {
            log.warn("Attempted to load null plugin entry");
            //switch to "new schedule" tab
            tabbedPane.setSelectedIndex(0); 
            // set tab 1 not showing
            // Disable the properties tab when no plugin is selected
            tabbedPane.setEnabledAt(1, false);
            return;
        }
        tabbedPane.setEnabledAt(1, true);
        if (entry.equals(selectedPlugin)){
            log.debug("Attempted to load already selected plugin entry");
            return;
        }
        
        this.selectedPlugin = entry;
        
        // Block combobox events temporarily to avoid feedback loops
        ActionListener[] listeners = pluginComboBox.getActionListeners();
        for (ActionListener listener : listeners) {
            pluginComboBox.removeActionListener(listener);
        }
                
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
                
        
        // Determine the time condition type and set appropriate panel
        TimeCondition startCondition = null;
        if (entry.getStartConditionManager() != null) {
            List<TimeCondition> timeConditions = entry.getStartConditionManager().getTimeConditions();
            if (!timeConditions.isEmpty()) {
                startCondition = timeConditions.get(0);
            }
        }
        
        if (startCondition == null) {
            // Default to showing the default panel, as we can't determine the condition type
            timeConditionTypeComboBox.setSelectedItem(CONDITION_INTERVAL);
            updateConditionPanel();
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
            setupTimeConditionPanel(startCondition);
        } else if (startCondition instanceof IntervalCondition) {
            Optional<Duration> nextTriger = startCondition.getDurationUntilNextTrigger();
            IntervalCondition interval = (IntervalCondition) startCondition;

            if (interval.getInterval().getSeconds() <= 1 && entry.isDefault()) {                
                timeConditionTypeComboBox.setSelectedItem(CONDITION_DEFAULT);
                updateConditionPanel();                        
            }else{
                // Configure the panel with existing values
                timeConditionTypeComboBox.setSelectedItem(CONDITION_INTERVAL);    
                updateConditionPanel();                        
                setupTimeConditionPanel(startCondition);
            }                        
            //updateConditionPanel();
            
        } else if (startCondition instanceof TimeWindowCondition) {
            timeConditionTypeComboBox.setSelectedItem(CONDITION_TIME_WINDOW);
            updateConditionPanel();
            setupTimeConditionPanel(startCondition);
        } else if (startCondition instanceof DayOfWeekCondition) {
            timeConditionTypeComboBox.setSelectedItem(CONDITION_DAY_OF_WEEK);
            updateConditionPanel();
            setupTimeConditionPanel(startCondition);
        }
        // Re-add condition type listeners
        for (ActionListener listener : conditionListeners) {
            timeConditionTypeComboBox.addActionListener(listener);
        }
        
        // Update the control button to reflect the current plugin
        updateControlButton();        
        // Update the properties panel
        updatePropertiesPanel(entry);

    }
    
    /**
     * Updates the properties panel to show the selected plugin's properties
     */
    private void updatePropertiesPanel(PluginScheduleEntry entry) {
        
        if (entry == null) {
            // Show the no selection panel
            JPanel noSelectionPanel = (JPanel) pluginPropertiesPanel.getClientProperty("noSelectionPanel");
            pluginPropertiesPanel.removeAll();
            pluginPropertiesPanel.add(noSelectionPanel, BorderLayout.CENTER);
            pluginPropertiesPanel.revalidate();
            pluginPropertiesPanel.repaint();
            return;
        }
        
        // Get the editor panel
        JPanel editorPanel = (JPanel) pluginPropertiesPanel.getClientProperty("editorPanel");
        
        // Update the header with the plugin name - directly use the selectedPluginNameLabel reference
        if (selectedPluginNameLabel != null) {
            selectedPluginNameLabel.setText("Plugin: " + entry.getCleanName());
        }
        
        // Set the flag to prevent update loops
        updatingValues = true;
        
        try {
            
            selectedPluginEnabledCheckbox.setSelected(entry.isEnabled());
            
            // Update default checkbox first as it may impact the priority spinner
            selectedPluginDefaultCheckbox.setSelected(entry.isDefault());
            
            // Then update priority spinner
            selectedPluginPrioritySpinner.setValue(entry.getPriority());
            
            // Update other checkboxes
            selectedPluginRandomCheckbox.setSelected(entry.isAllowRandomScheduling());
            selectedPluginTimeStopCheckbox.setSelected(entry.isNeedsStopCondition());
            
            // Update statistics
            updateStatistics();
        } finally {
            // Reset the flag after all updates
            updatingValues = false;
        }
        
        // Show the editor panel
        pluginPropertiesPanel.removeAll();
        pluginPropertiesPanel.add(editorPanel, BorderLayout.CENTER);
        pluginPropertiesPanel.revalidate();
        pluginPropertiesPanel.repaint();
    }

    public void clearForm() {
        
        this.selectedPlugin = null;  
        if (pluginComboBox.getItemCount() > 0) {
            pluginComboBox.setSelectedIndex(0);
        }

        // Reset condition type to default
        timeConditionTypeComboBox.setSelectedItem(CONDITION_INTERVAL);
        updateConditionPanel();
        
        // Reset random scheduling
        randomSchedulingCheckbox.setSelected(true);
        
        // Update the control button
        updateControlButton();
        
        // Reset properties panel
        updatePropertiesPanel(null);
    }

    public PluginScheduleEntry getPluginFromForm(PluginScheduleEntry existingPlugin) {
        String pluginName = (String) pluginComboBox.getSelectedItem();
        if (pluginName == null || pluginName.isEmpty()) {
            return null;
        }
        
        // Get the selected time condition type
        String selectedType = (String) timeConditionTypeComboBox.getSelectedItem();
        TimeCondition timeCondition = null;
        
        // Create the appropriate time condition
        if (CONDITION_DEFAULT.equals(selectedType)) {
            // Default plugin with 1-second interval
            timeCondition = new IntervalCondition(Duration.ofSeconds(1));
        } else if (CONDITION_SPECIFIC_TIME.equals(selectedType)) {
            timeCondition = TimeConditionPanelUtil.createSingleTriggerCondition(currentConditionPanel);
        } else if (CONDITION_INTERVAL.equals(selectedType)) {
            timeCondition = TimeConditionPanelUtil.createIntervalCondition(currentConditionPanel);
        } else if (CONDITION_TIME_WINDOW.equals(selectedType)) {
            timeCondition = TimeConditionPanelUtil.createTimeWindowCondition(currentConditionPanel);
        } else if (CONDITION_DAY_OF_WEEK.equals(selectedType)) {
            timeCondition = TimeConditionPanelUtil.createDayOfWeekCondition(currentConditionPanel);
        }
        
        // If we couldn't create a time condition, return null
        if (timeCondition == null) {
            log.warn("Could not create time condition from form");
            return null;
        }
        
        // Get other settings
        boolean randomScheduling = randomSchedulingCheckbox.isSelected();
        boolean needsStopCondition = timeBasedStopConditionCheckbox.isSelected();
        int priority = (Integer) prioritySpinner.getValue();
        boolean isDefault = defaultPluginCheckbox.isSelected();
        
        // Create the plugin schedule entry
        PluginScheduleEntry entry;
        if (existingPlugin != null) {            
            // Update the existing plugin with new values
            existingPlugin.updatePrimaryTimeCondition(timeCondition);
            existingPlugin.setAllowRandomScheduling(randomScheduling);
            existingPlugin.setNeedsStopCondition(needsStopCondition);
            existingPlugin.setPriority(priority);
            existingPlugin.setDefault(isDefault);
            entry = existingPlugin;
        } else {
            // Create a new plugin schedule entry
            entry = new PluginScheduleEntry(
                    pluginName,
                    timeCondition,
                    true,  // Enabled by default
                    randomScheduling
            );
            entry.setNeedsStopCondition(needsStopCondition);
            entry.setPriority(priority);
            entry.setDefault(isDefault);
        }
        
        return entry;
    }
    
    /**
     * Updates the selected plugin with values from the properties panel
     */
    private void updateSelectedPlugin() {
        if (selectedPlugin == null) return;
        
        // Get the values from the properties panel
        boolean enabled = selectedPluginEnabledCheckbox.isSelected();
        boolean randomScheduling = selectedPluginRandomCheckbox.isSelected();
        boolean needsStopCondition = selectedPluginTimeStopCheckbox.isSelected();
        int priority = (Integer) selectedPluginPrioritySpinner.getValue();
        boolean isDefault = selectedPluginDefaultCheckbox.isSelected();
        
        // Update the plugin
        selectedPlugin.setEnabled(enabled);
        selectedPlugin.setAllowRandomScheduling(randomScheduling);
        selectedPlugin.setNeedsStopCondition(needsStopCondition);
        selectedPlugin.setPriority(priority);
        selectedPlugin.setDefault(isDefault);
        
        // Save the changes
        plugin.saveScheduledPlugins();
        
        // Update the control button
        updateControlButton();
        
        // Notify the main window to refresh the table
        //plugin.refreshScheduleTable();
    }

    public void updateControlButton() {
        boolean isRunning = selectedPlugin != null && selectedPlugin.isRunning();
        boolean isEnabled = selectedPlugin != null && selectedPlugin.isEnabled();
        
        if (isRunning) {
            controlButton.setText("Stop Plugin");
            controlButton.setBackground(ColorScheme.PROGRESS_ERROR_COLOR);
        } else {
            controlButton.setText("Run Now");
            controlButton.setBackground(ColorScheme.PROGRESS_COMPLETE_COLOR);
        }
        
        controlButton.setEnabled(selectedPlugin != null && isEnabled);
    }

    private void onControlButtonClicked(ActionEvent e) {
        if (selectedPlugin == null) {
            return;
        }
        
        if (selectedPlugin.isRunning()) {
            // Stop the plugin
            //plugin.stopPlugin(selectedPlugin);
        } else {
            // Start the plugin
            //plugin.startPlugin(selectedPlugin);
        }
        
        // Update control button and statistics
        updateControlButton();
        updateStatistics();
    }

    public void setEditMode(boolean editMode) {
        updateButton.setVisible(editMode);
        addButton.setVisible(!editMode);
    }

    public void setAddButtonAction(ActionListener listener) {
        for (ActionListener l : addButton.getActionListeners()) {
            addButton.removeActionListener(l);
        }
        addButton.addActionListener(listener);
    }

    public void setUpdateButtonAction(ActionListener listener) {
        for (ActionListener l : updateButton.getActionListeners()) {
            updateButton.removeActionListener(l);
        }
        updateButton.addActionListener(listener);
    }

    public void setRemoveButtonAction(ActionListener listener) {
        for (ActionListener l : removeButton.getActionListeners()) {
            removeButton.removeActionListener(l);
        }
        removeButton.addActionListener(listener);
    }
   
    /**
     * Sets up the time condition panel with values from an existing condition
     */
    private void setupTimeConditionPanel(TimeCondition condition) {
        if (condition == null || currentConditionPanel == null) {
            return;
        }
        
        if (condition instanceof SingleTriggerTimeCondition) {
            TimeConditionPanelUtil.setupTimeCondition(currentConditionPanel, (SingleTriggerTimeCondition) condition);
        } else if (condition instanceof IntervalCondition) {
            TimeConditionPanelUtil.setupTimeCondition(currentConditionPanel, (IntervalCondition) condition);
        } else if (condition instanceof TimeWindowCondition) {
            TimeConditionPanelUtil.setupTimeCondition(currentConditionPanel, (TimeWindowCondition) condition);
        } else if (condition instanceof DayOfWeekCondition) {
            TimeConditionPanelUtil.setupTimeCondition(currentConditionPanel, (DayOfWeekCondition) condition);
        }
    }
}
