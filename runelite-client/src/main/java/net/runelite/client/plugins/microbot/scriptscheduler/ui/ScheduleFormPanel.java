package net.runelite.client.plugins.microbot.scriptscheduler.ui;

import lombok.Getter;
import net.runelite.client.plugins.microbot.scriptscheduler.ScheduledScript;
import net.runelite.client.plugins.microbot.scriptscheduler.ScriptSchedulerPlugin;
import net.runelite.client.plugins.microbot.scriptscheduler.type.ScheduleType;
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
    private final ScriptSchedulerPlugin plugin;

    @Getter
    private JComboBox<String> scriptComboBox;
    private JSpinner intervalSpinner;
    private JComboBox<ScheduleType> scheduleTypeComboBox;
    private JCheckBox enableDurationCheckbox;
    private JSpinner durationSpinner;

    // First run time components
    private JRadioButton runNowRadio;
    private JRadioButton runLaterRadio;
    private JSpinner firstRunTimeSpinner;

    private JButton addButton;
    private JButton updateButton;
    private JButton removeButton;
    private JButton controlButton;

    private ScheduledScript selectedScript;

    public ScheduleFormPanel(ScriptSchedulerPlugin plugin) {
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

        // Script selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        JLabel scriptLabel = new JLabel("Script:");
        scriptLabel.setForeground(Color.WHITE);
        scriptLabel.setFont(FontManager.getRunescapeFont());
        formPanel.add(scriptLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        scriptComboBox = new JComboBox<>();
        formPanel.add(scriptComboBox, gbc);
        updateScriptList(plugin.getAvailableScripts());

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
        button.setBackground(color);
        button.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 1),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        // Add hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });

        return button;
    }

    public void updateScriptList(List<String> scripts) {
        if (scripts == null || scripts.isEmpty()) {
            return;
        }

        scriptComboBox.removeAllItems();
        for (String script : scripts) {
            scriptComboBox.addItem(script);
        }
    }

    public void loadScript(ScheduledScript script) {
        this.selectedScript = script;

        // Set script
        scriptComboBox.setSelectedItem(script.getScriptName());

        // Set interval and type
        intervalSpinner.setValue(script.getIntervalValue());
        scheduleTypeComboBox.setSelectedItem(script.getScheduleType() != null ?
                script.getScheduleType() : ScheduleType.HOURS);

        // Set first run time - for existing scripts, we'll default to "Now"
        runNowRadio.setSelected(true);
        firstRunTimeSpinner.setEnabled(false);

        // Set duration
        if (script.getDuration() != null && !script.getDuration().isEmpty()) {
            enableDurationCheckbox.setSelected(true);
            durationSpinner.setEnabled(true);
            try {
                String[] parts = script.getDuration().split(":");
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
                // Use 01:00 as default duration if parsing fails
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

        // Update the control button to reflect the current script
        updateControlButton();
    }
    public void clearForm() {
        selectedScript = null;

        // Reset script selection
        if (scriptComboBox.getItemCount() > 0) {
            scriptComboBox.setSelectedIndex(0);
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

        // Reset duration to 1 hour and disable
        enableDurationCheckbox.setSelected(false);
        durationSpinner.setEnabled(false);
        calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        durationSpinner.setValue(calendar.getTime());

        // Update the control button
        updateControlButton();
    }

    public ScheduledScript getScriptFromForm() {
        String scriptName = (String) scriptComboBox.getSelectedItem();
        if (scriptName == null || scriptName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select a script.",
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

        // Get duration (if enabled)
        String durationStr = "";
        if (enableDurationCheckbox.isSelected()) {
            java.util.Date durationDate = (java.util.Date) durationSpinner.getValue();
            durationStr = new java.text.SimpleDateFormat("HH:mm").format(durationDate);
        }

        // Create the script with default settings
        ScheduledScript script = new ScheduledScript(scriptName, scheduleType, intervalValue, durationStr, true);

        if (runLaterRadio.isSelected()) {
            // User wants to run at a specific time
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

            script.setNextRunTime(targetCal.getTimeInMillis());
        }

        return script;
    }

    public void updateControlButton() {
        boolean isScriptRunning = selectedScript != null && selectedScript.isRunning();

        if (isScriptRunning) {
            // If a script is running, show "Stop Script" button
            controlButton.setText("Stop Script");
            controlButton.setBackground(ColorScheme.PROGRESS_ERROR_COLOR);
            controlButton.setEnabled(true);
            updateButtonHoverEffect(controlButton, ColorScheme.PROGRESS_ERROR_COLOR);
        } else if (selectedScript != null) {
            controlButton.setText("Run \"" + selectedScript.getCleanName() + "\" Now");
            controlButton.setBackground(ColorScheme.PROGRESS_COMPLETE_COLOR);
            controlButton.setEnabled(true);
            updateButtonHoverEffect(controlButton, ColorScheme.PROGRESS_COMPLETE_COLOR);
        } else {
            // If no script is selected, disable the button
            controlButton.setText("Select a Script");
            controlButton.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
            controlButton.setEnabled(false);
        }
    }

    private void updateButtonHoverEffect(JButton button, Color baseColor) {
        // Remove existing mouse listeners
        for (java.awt.event.MouseListener listener : button.getMouseListeners()) {
            button.removeMouseListener(listener);
        }

        // Add new hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(baseColor.brighter());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(baseColor);
            }
        });
    }

    private void onControlButtonClicked(ActionEvent e) {

        if (selectedScript.isRunning()) {
            // Stop the current script
            plugin.stopCurrentScript();
        } else if (selectedScript != null) {
            // Run the selected script now
            plugin.startScript(selectedScript);
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
