package net.runelite.client.plugins.microbot.scriptscheduler.ui;

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
import java.time.LocalTime;
import java.util.Calendar;
import java.util.List;

public class ScheduleFormPanel extends JPanel {
    private final ScriptSchedulerPlugin plugin;

    private JComboBox<String> scriptComboBox;
    private JSpinner intervalSpinner;
    private JComboBox<ScheduleType> scheduleTypeComboBox;
    private JCheckBox enableDurationCheckbox;
    private JSpinner durationSpinner;
    private JButton addButton;
    private JButton updateButton;
    private JButton removeButton;
    private JButton controlButton;

    private ScheduledScript currentScript;

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

        // Duration
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        enableDurationCheckbox = new JCheckBox("Run for duration:");
        enableDurationCheckbox.setForeground(Color.WHITE);
        enableDurationCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        enableDurationCheckbox.setFont(FontManager.getRunescapeFont());
        enableDurationCheckbox.addActionListener(e ->
                durationSpinner.setEnabled(enableDurationCheckbox.isSelected()));
        formPanel.add(enableDurationCheckbox, gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        SpinnerDateModel durationModel = new SpinnerDateModel();
        durationSpinner = new JSpinner(durationModel);
        durationSpinner.setEditor(new JSpinner.DateEditor(durationSpinner, "HH:mm"));
        durationSpinner.setEnabled(false);

        // Set default duration to 1 hour
        Calendar calendar = Calendar.getInstance();
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
        this.currentScript = script;

        // Set script
        scriptComboBox.setSelectedItem(script.getScriptName());

        // Set interval and type
        intervalSpinner.setValue(script.getIntervalValue());
        scheduleTypeComboBox.setSelectedItem(script.getScheduleType() != null ?
                script.getScheduleType() : ScheduleType.HOURS);

        // Set duration
        if (script.getDuration() != null && !script.getDuration().isEmpty()) {
            enableDurationCheckbox.setSelected(true);
            durationSpinner.setEnabled(true);
            try {
                LocalTime duration = LocalTime.parse(script.getDuration(), ScheduledScript.TIME_FORMATTER);
                // Convert LocalTime to java.util.Date
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.HOUR_OF_DAY, duration.getHour());
                calendar.set(Calendar.MINUTE, duration.getMinute());
                calendar.set(Calendar.SECOND, 0);
                durationSpinner.setValue(calendar.getTime());
            } catch (Exception e) {
                // Use 01:00 as default duration if parsing fails
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.HOUR_OF_DAY, 1);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                durationSpinner.setValue(calendar.getTime());
            }
        } else {
            enableDurationCheckbox.setSelected(false);
            durationSpinner.setEnabled(false);
        }
    }

    public void clearForm() {
        currentScript = null;

        // Reset script selection
        if (scriptComboBox.getItemCount() > 0) {
            scriptComboBox.setSelectedIndex(0);
        }

        // Reset interval to 1
        intervalSpinner.setValue(1);

        // Reset schedule type to HOURS
        scheduleTypeComboBox.setSelectedItem(ScheduleType.HOURS);

        // Reset duration to 1 hour and disable
        enableDurationCheckbox.setSelected(false);
        durationSpinner.setEnabled(false);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        durationSpinner.setValue(calendar.getTime());
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

        return new ScheduledScript(scriptName, scheduleType, intervalValue, durationStr, true);
    }

    public void updateControlButton() {
        boolean isScriptRunning = plugin.getCurrentScriptName() != null && !plugin.getCurrentScriptName().isEmpty();

        if (isScriptRunning) {
            // If a script is running, show "Stop Script" button
            controlButton.setText("Stop Script");
            controlButton.setBackground(ColorScheme.PROGRESS_ERROR_COLOR);
            controlButton.setEnabled(true);

            // Update hover effect for red button
            updateButtonHoverEffect(controlButton, ColorScheme.PROGRESS_ERROR_COLOR);
        } else if (currentScript != null) {
            controlButton.setText("Run now!");
            controlButton.setBackground(ColorScheme.PROGRESS_COMPLETE_COLOR);
            controlButton.setEnabled(true);

            // Update hover effect for green button
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
            if (listener.getClass() == java.awt.event.MouseAdapter.class) {
                button.removeMouseListener(listener);
            }
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
        boolean isScriptRunning = plugin.getCurrentScriptName() != null && !plugin.getCurrentScriptName().isEmpty();

        if (isScriptRunning) {
            // Stop the current script
            plugin.stopCurrentScript();
        } else if (currentScript != null) {
            // Run the selected script now
            plugin.startScript(currentScript.getScriptName());
        }
    }

    public void setEditMode(boolean editMode) {
        updateButton.setEnabled(editMode);
        removeButton.setEnabled(editMode);
        addButton.setEnabled(!editMode);
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
