package net.runelite.client.plugins.microbot.pluginscheduler.ui;

import lombok.Getter;

import net.runelite.client.plugins.microbot.pluginscheduler.type.ScheduledPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;

import net.runelite.client.plugins.microbot.pluginscheduler.type.ScheduleType;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.time.ZonedDateTime;
import java.time.ZoneId;

public class ScheduleFormPanel extends JPanel {
    private final SchedulerPlugin plugin;

    @Getter
    private JComboBox<String> pluginComboBox;
    private JSpinner intervalSpinner;
    private JComboBox<ScheduleType> scheduleTypeComboBox;
    private JCheckBox randomSchedulingCheckbox;
    
    

    // First run time components
    private JRadioButton runNowRadio;
    private JRadioButton runLaterRadio;
    private JSpinner firstRunTimeSpinner;

    private JButton addButton;
    private JButton updateButton;
    private JButton removeButton;
    private JButton controlButton;

    private ScheduledPlugin selectedPlugin;
    
    

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

        // Create the form panel with GridBagLayout for flexibility
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

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

        // Schedule type
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        JLabel intervalLabel = new JLabel("Run every:");
        intervalLabel.setForeground(Color.WHITE);
        intervalLabel.setFont(FontManager.getRunescapeFont());
        formPanel.add(intervalLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        SpinnerNumberModel intervalModel = new SpinnerNumberModel(1, 1, 999, 1);
        intervalSpinner = new JSpinner(intervalModel);
        formPanel.add(intervalSpinner, gbc);

        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        scheduleTypeComboBox = new JComboBox<>(ScheduleType.values());
        formPanel.add(scheduleTypeComboBox, gbc);

        // First run time
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        JLabel firstRunLabel = new JLabel("First run:");
        firstRunLabel.setForeground(Color.WHITE);
        firstRunLabel.setFont(FontManager.getRunescapeFont());
        formPanel.add(firstRunLabel, gbc);

        // Radio buttons for first run options
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        runNowRadio = new JRadioButton("Now");
        runNowRadio.setForeground(Color.WHITE);
        runNowRadio.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        runNowRadio.setFont(FontManager.getRunescapeFont());
        runNowRadio.setSelected(true);
        formPanel.add(runNowRadio, gbc);

        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        runLaterRadio = new JRadioButton("At:");
        runLaterRadio.setForeground(Color.WHITE);
        runLaterRadio.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        runLaterRadio.setFont(FontManager.getRunescapeFont());
        formPanel.add(runLaterRadio, gbc);

        // Group the radio buttons
        ButtonGroup firstRunGroup = new ButtonGroup();
        firstRunGroup.add(runNowRadio);
        firstRunGroup.add(runLaterRadio);

        // Time spinner for first run
        gbc.gridx = 3;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        SpinnerDateModel firstRunModel = new SpinnerDateModel();
        firstRunTimeSpinner = new JSpinner(firstRunModel);
        firstRunTimeSpinner.setEditor(new JSpinner.DateEditor(firstRunTimeSpinner, "HH:mm"));
        firstRunTimeSpinner.setEnabled(false);

        // Set default first run time to current time + 1 hour
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, 1);
        firstRunTimeSpinner.setValue(calendar.getTime());

        formPanel.add(firstRunTimeSpinner, gbc);

        // Enable/disable time spinner based on radio selection
        runNowRadio.addActionListener(e -> firstRunTimeSpinner.setEnabled(false));
        runLaterRadio.addActionListener(e -> firstRunTimeSpinner.setEnabled(true));

        // Random scheduling checkbox
        randomSchedulingCheckbox = new JCheckBox("Allow random scheduling");
        randomSchedulingCheckbox.setSelected(true);
        randomSchedulingCheckbox.setToolTipText(
            "<html>When enabled, this plugin can be randomly selected when multiple plugins are due to run.<br>" +
            "If disabled, this plugin will have higher priority than randomizable plugins.</html>");
        randomSchedulingCheckbox.setForeground(Color.WHITE);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 4;
        formPanel.add(randomSchedulingCheckbox, gbc);

        // Add the form panel to the center
        add(formPanel, BorderLayout.CENTER);

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

    public void loadPlugin(ScheduledPlugin plugin) {
        this.selectedPlugin = plugin;

        // Set plugin
        pluginComboBox.setSelectedItem(plugin.getName());
    
        // Set interval and type
        intervalSpinner.setValue(plugin.getScheduleIntervalValue());
        scheduleTypeComboBox
                .setSelectedItem(plugin.getScheduleType() != null ? plugin.getScheduleType() : ScheduleType.HOURS);
    
        // Set random scheduling checkbox
        randomSchedulingCheckbox.setSelected(plugin.isAllowRandomScheduling());
    
        // Set first run time - for existing plugins, we'll default to "Now"
        runNowRadio.setSelected(true);
        firstRunTimeSpinner.setEnabled(false);
    
        // Remove duration code
        // Duration is now handled by stop conditions
    
        // Update the control button to reflect the current plugin
        updateControlButton();
    }

    public void clearForm() {
        selectedPlugin = null;

        // Reset plugin selection
        if (pluginComboBox.getItemCount() > 0) {
            pluginComboBox.setSelectedIndex(0);
        }

        // Reset interval to 1
        intervalSpinner.setValue(1);

        // Reset schedule type to HOURS
        scheduleTypeComboBox.setSelectedItem(ScheduleType.HOURS);

        // Reset first run time to "Now"
        runNowRadio.setSelected(true);
        firstRunTimeSpinner.setEnabled(false);

        // Reset first run time spinner to current time + 1 hour
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, 1);
        firstRunTimeSpinner.setValue(calendar.getTime());

        
        calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        
        
        // Update the control button
        updateControlButton();
    }

    public ScheduledPlugin getPluginFromForm() {
        String pluginName = (String) pluginComboBox.getSelectedItem();
        if (pluginName == null || pluginName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select a plugin.",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
    
        // Get interval value and ensure it's at least 1
        int intervalValue = (Integer) intervalSpinner.getValue();
        if (intervalValue < 1) {
            JOptionPane.showMessageDialog(this,
                    "The schedule interval must be at least 1.",
                    "Invalid Interval",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
    
        // Get schedule type
        ScheduleType scheduleType = (ScheduleType) scheduleTypeComboBox.getSelectedItem();
        if (scheduleType == null) {
            scheduleType = ScheduleType.HOURS; // Default to HOURS if null
        }
    
        // No longer using duration - it's handled by stop conditions
        String durationStr = "";
    
        // Create the plugin with default settings
        ScheduledPlugin plugin = new ScheduledPlugin(pluginName, scheduleType, intervalValue, durationStr, true, randomSchedulingCheckbox.isSelected());
    
        // Set start time based on user selection
        if (runLaterRadio.isSelected()) {
            // Set specific start time
            Date selectedTime = (Date) firstRunTimeSpinner.getValue();
            Calendar selectedCal = Calendar.getInstance();
            selectedCal.setTime(selectedTime);
    
            // Get hours and minutes from the spinner
            int hours = selectedCal.get(Calendar.HOUR_OF_DAY);
            int minutes = selectedCal.get(Calendar.MINUTE);
    
            // Create a Date for today at the specified time
            Calendar targetCal = Calendar.getInstance();
            targetCal.set(Calendar.HOUR_OF_DAY, hours);
            targetCal.set(Calendar.MINUTE, minutes);
            targetCal.set(Calendar.SECOND, 0);
            targetCal.set(Calendar.MILLISECOND, 0);
    
            // If the time is in the past, add a day to make it future
            if (targetCal.getTimeInMillis() < System.currentTimeMillis()) {
                targetCal.add(Calendar.DAY_OF_MONTH, 1);
            }
    
            // Convert Calendar to ZonedDateTime
            ZonedDateTime nextRunTime = ZonedDateTime.ofInstant(
                    targetCal.toInstant(),
                    ZoneId.systemDefault());
            plugin.setNextRunTime(nextRunTime);
        }
    
        return plugin;
    }

    

    public void updateControlButton() {

        if (plugin.isRunning()) {
            // If a plugin is running, show "Stop Plugin" button
            controlButton.setText("Stop Running Plugin");
            controlButton.setEnabled(true);
        } else if (selectedPlugin != null) {
            controlButton.setText("Run \"" + selectedPlugin.getCleanName() + "\" Now");
            controlButton.setEnabled(true);
        } else {
            // If no plugin is selected, disable the button
            controlButton.setText("Select a Plugin");
            controlButton.setEnabled(false);
        }
    }

    private void onControlButtonClicked(ActionEvent e) {

        if (plugin.isRunning()) {
            // Stop the current plugin
            plugin.forceStopCurrentPlugin();
        } else if (selectedPlugin != null) {
            // Run the selected plugin now
            plugin.startPlugin(selectedPlugin);
        }
    }

    public void setEditMode(boolean editMode) {
        updateButton.setEnabled(editMode);
        removeButton.setEnabled(editMode);
        addButton.setEnabled(!editMode);

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
}
