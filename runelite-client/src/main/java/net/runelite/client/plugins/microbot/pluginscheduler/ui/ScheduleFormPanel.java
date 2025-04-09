package net.runelite.client.plugins.microbot.pluginscheduler.ui;

import lombok.Getter;
import net.runelite.client.plugins.microbot.pluginscheduler.type.Scheduled;
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
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ScheduleFormPanel extends JPanel {
    private final SchedulerPlugin plugin;

    @Getter
    private JComboBox<String> pluginComboBox;
    private final JSpinner intervalSpinner;
    private final JComboBox<ScheduleType> scheduleTypeComboBox;
    private final JCheckBox enableDurationCheckbox;
    private JSpinner durationSpinner;
    private final JRadioButton runNowRadio;
    private final JRadioButton runLaterRadio;
    private final JSpinner firstRunTimeSpinner;
    private final JButton addButton;
    private final JButton updateButton;
    private final JButton removeButton;
    private final JButton controlButton;
    private final JCheckBox enableTimeRestrictionCheckbox;
    private JSpinner startHourSpinner;
    private JSpinner endHourSpinner;

    private Scheduled selectedPlugin;

    public ScheduleFormPanel(SchedulerPlugin plugin) {
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
                        BorderFactory.createEmptyBorder(5, 5, 5, 5)
                ),
                "Schedule Configuration",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                FontManager.getRunescapeBoldFont(),
                Color.WHITE
        ));
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

        // Duration
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        enableDurationCheckbox = new JCheckBox("Run for duration:");
        enableDurationCheckbox.setForeground(Color.WHITE);
        enableDurationCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        enableDurationCheckbox.setFont(FontManager.getRunescapeFont());
        enableDurationCheckbox.addActionListener(e ->
                durationSpinner.setEnabled(enableDurationCheckbox.isSelected()));
        formPanel.add(enableDurationCheckbox, gbc);

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        SpinnerDateModel durationModel = new SpinnerDateModel();
        durationSpinner = new JSpinner(durationModel);
        durationSpinner.setEditor(new JSpinner.DateEditor(durationSpinner, "HH:mm"));
        durationSpinner.setEnabled(false);

        // Set default duration to 1 hour
        calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        durationSpinner.setValue(calendar.getTime());

        formPanel.add(durationSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        enableTimeRestrictionCheckbox = new JCheckBox("Time restriction:");
        enableTimeRestrictionCheckbox.setForeground(Color.WHITE);
        enableTimeRestrictionCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        enableTimeRestrictionCheckbox.setFont(FontManager.getRunescapeFont());
        enableTimeRestrictionCheckbox.addActionListener(e -> {
            startHourSpinner.setEnabled(enableTimeRestrictionCheckbox.isSelected());
            endHourSpinner.setEnabled(enableTimeRestrictionCheckbox.isSelected());
        });
        formPanel.add(enableTimeRestrictionCheckbox, gbc);

        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        SpinnerNumberModel startHourModel = new SpinnerNumberModel(8, 0, 23, 1);
        startHourSpinner = new JSpinner(startHourModel);
        startHourSpinner.setEnabled(false);
        startHourSpinner.addChangeListener(e -> {
            if ((int) startHourSpinner.getValue() < 0 || (int) startHourSpinner.getValue() > 23) {

            }
        });
        formPanel.add(startHourSpinner, gbc);


        gbc.gridx = 2;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        JLabel toLabel = new JLabel("to");
        toLabel.setForeground(Color.WHITE);
        toLabel.setFont(FontManager.getRunescapeFont());
        formPanel.add(toLabel, gbc);

        gbc.gridx = 3;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        SpinnerNumberModel endHourModel = new SpinnerNumberModel(20, 0, 23, 1);
        endHourSpinner = new JSpinner(endHourModel);
        endHourSpinner.setEnabled(false);
        formPanel.add(endHourSpinner, gbc);

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
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

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

    public void loadPlugin(Scheduled plugin) {
        this.selectedPlugin = plugin;
        pluginComboBox.setSelectedItem(plugin.getName());
        intervalSpinner.setValue(plugin.getIntervalValue());
        scheduleTypeComboBox.setSelectedItem(plugin.getScheduleType() != null ?
                plugin.getScheduleType() : ScheduleType.HOURS);

        runNowRadio.setSelected(true);
        firstRunTimeSpinner.setEnabled(false);

        if (plugin.getDuration() != null && !plugin.getDuration().isEmpty()) {
            enableDurationCheckbox.setSelected(true);
            durationSpinner.setEnabled(true);
            try {
                String[] parts = plugin.getDuration().split(":");
                if (parts.length == 2) {
                    int hours = Integer.parseInt(parts[0]);
                    int minutes = Integer.parseInt(parts[1]);

                    Calendar calendar = Calendar.getInstance();
                    calendar.set(Calendar.HOUR_OF_DAY, hours);
                    calendar.set(Calendar.MINUTE, minutes);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    durationSpinner.setValue(calendar.getTime());
                }
            } catch (Exception e) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.HOUR_OF_DAY, 1);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                durationSpinner.setValue(calendar.getTime());
            }
        } else {
            enableDurationCheckbox.setSelected(false);
            durationSpinner.setEnabled(false);
        }

        enableTimeRestrictionCheckbox.setSelected(plugin.isTimeRestrictionEnabled());
        startHourSpinner.setValue(plugin.getStartHour());
        endHourSpinner.setValue(plugin.getEndHour());
        startHourSpinner.setEnabled(plugin.isTimeRestrictionEnabled());
        endHourSpinner.setEnabled(plugin.isTimeRestrictionEnabled());

        updateControlButton();
    }

    public void clearForm() {
        selectedPlugin = null;

        if (pluginComboBox.getItemCount() > 0) {
            pluginComboBox.setSelectedIndex(0);
        }

        intervalSpinner.setValue(1);
        scheduleTypeComboBox.setSelectedItem(ScheduleType.HOURS);
        runNowRadio.setSelected(true);
        firstRunTimeSpinner.setEnabled(false);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, 1);
        firstRunTimeSpinner.setValue(calendar.getTime());
        enableDurationCheckbox.setSelected(false);
        durationSpinner.setEnabled(false);
        calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        durationSpinner.setValue(calendar.getTime());
        enableTimeRestrictionCheckbox.setSelected(false);
        startHourSpinner.setValue(8);
        endHourSpinner.setValue(20);
        startHourSpinner.setEnabled(false);
        endHourSpinner.setEnabled(false);
        updateControlButton();
    }

    public Scheduled getPluginFromForm() {
        String pluginName = (String) pluginComboBox.getSelectedItem();
        if (pluginName == null || pluginName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select a plugin.",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }

        int intervalValue = (Integer) intervalSpinner.getValue();
        if (intervalValue < 1) {
            JOptionPane.showMessageDialog(this,
                    "The schedule interval must be at least 1.",
                    "Invalid Interval",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }

        ScheduleType scheduleType = (ScheduleType) scheduleTypeComboBox.getSelectedItem();
        if (scheduleType == null) {
            scheduleType = ScheduleType.HOURS; // Default to HOURS if null
        }

        String durationStr = "";
        if (enableDurationCheckbox.isSelected()) {
            java.util.Date durationDate = (java.util.Date) durationSpinner.getValue();
            durationStr = new java.text.SimpleDateFormat("HH:mm").format(durationDate);
        }

        Scheduled plugin = new Scheduled(pluginName, scheduleType, intervalValue, durationStr, true);

        plugin.setTimeRestrictionEnabled(enableTimeRestrictionCheckbox.isSelected());
        plugin.setStartHour((Integer) startHourSpinner.getValue());
        plugin.setEndHour((Integer) endHourSpinner.getValue());

        if (runLaterRadio.isSelected()) {
            // User wants to run at a specific time
            Date selectedTime = (Date) firstRunTimeSpinner.getValue();
            Calendar selectedCal = Calendar.getInstance();
            selectedCal.setTime(selectedTime);

            int hours = selectedCal.get(Calendar.HOUR_OF_DAY);
            int minutes = selectedCal.get(Calendar.MINUTE);

            Calendar targetCal = Calendar.getInstance();
            targetCal.set(Calendar.HOUR_OF_DAY, hours);
            targetCal.set(Calendar.MINUTE, minutes);
            targetCal.set(Calendar.SECOND, 0);
            targetCal.set(Calendar.MILLISECOND, 0);

            if (targetCal.getTimeInMillis() < System.currentTimeMillis()) {
                targetCal.add(Calendar.DAY_OF_MONTH, 1);
            }

            plugin.setNextRunTime(targetCal.getTimeInMillis());
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
            plugin.stopCurrentPlugin();
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
