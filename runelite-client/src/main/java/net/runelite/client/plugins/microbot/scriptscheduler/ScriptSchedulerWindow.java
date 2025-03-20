package net.runelite.client.plugins.microbot.scriptscheduler;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ScriptSchedulerWindow extends JFrame {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ScriptSchedulerPlugin plugin;
    private final ScriptSchedulerConfig config;

    private final JTable scheduleTable;
    private final DefaultTableModel tableModel;
    private JComboBox<String> scriptComboBox;
    private JComboBox<ScheduledScript.ScheduleType> scheduleTypeComboBox;
    private JSpinner timeSpinner;
    private JCheckBox enableDurationCheckbox;
    private JSpinner durationSpinner;
    private final JCheckBox[] dayCheckboxes = new JCheckBox[7];
    private JButton addButton;
    private JButton removeButton;
    private JButton editButton;
    private JButton runNowButton;
//    private JComboBox<String> defaultScriptComboBox;

    private ScheduledScript editingScript = null;

    public ScriptSchedulerWindow(ScriptSchedulerPlugin plugin, ScriptSchedulerConfig config) {
        super("Script Scheduler");
        this.plugin = plugin;
        this.config = config;

        setSize(600, 500);
        setLocationRelativeTo(null); // Center on screen
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Add window listener to handle window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        // Create the table model with columns
        tableModel = new DefaultTableModel(
                new Object[]{"Script", "Time", "Duration", "Schedule", "Enabled"}, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 4) return Boolean.class;
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4; // Only enabled column is editable
            }
        };

        // Create the table
        scheduleTable = new JTable(tableModel);
        scheduleTable.setFillsViewportHeight(true);
        scheduleTable.setRowHeight(25);

        // Add table to scroll pane
        JScrollPane scrollPane = new JScrollPane(scheduleTable);

        // Create form panel
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 5, 5, 5);

        // Script selection
        c.gridx = 0;
        c.gridy = 0;
        // Script selection
        c.gridx = 0;
        c.gridy = 0;
        formPanel.add(new JLabel("Script:"), c);

        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 2;
        scriptComboBox = new JComboBox<>();
        formPanel.add(scriptComboBox, c);

        // Time selection
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        formPanel.add(new JLabel("Start Time:"), c);

        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 2;
        SpinnerDateModel timeModel = new SpinnerDateModel();
        timeSpinner = new JSpinner(timeModel);
        JSpinner.DateEditor timeEditor = new JSpinner.DateEditor(timeSpinner, "HH:mm");
        timeSpinner.setEditor(timeEditor);
        formPanel.add(timeSpinner, c);

        // Duration selection
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        formPanel.add(new JLabel("Duration:"), c);

        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 1;
        enableDurationCheckbox = new JCheckBox("Enable");
        enableDurationCheckbox.setSelected(true);
        enableDurationCheckbox.addActionListener(e -> durationSpinner.setEnabled(enableDurationCheckbox.isSelected()));
        formPanel.add(enableDurationCheckbox, c);

        c.gridx = 2;
        c.gridy = 2;
        c.gridwidth = 1;
        SpinnerDateModel durationModel = new SpinnerDateModel();
        durationSpinner = new JSpinner(durationModel);
        JSpinner.DateEditor durationEditor = new JSpinner.DateEditor(durationSpinner, "HH:mm");
        durationSpinner.setEditor(durationEditor);
        formPanel.add(durationSpinner, c);

        // Schedule type
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 1;
        formPanel.add(new JLabel("Schedule:"), c);

        c.gridx = 1;
        c.gridy = 3;
        c.gridwidth = 2;
        scheduleTypeComboBox = new JComboBox<>(ScheduledScript.ScheduleType.values());
        scheduleTypeComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                if (value instanceof ScheduledScript.ScheduleType) {
                    value = ((ScheduledScript.ScheduleType) value).toString();
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });
        formPanel.add(scheduleTypeComboBox, c);

        // Day selection (for custom schedule)
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 3;
        JPanel daysPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        String[] dayNames = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (int i = 0; i < 7; i++) {
            dayCheckboxes[i] = new JCheckBox(dayNames[i]);
            daysPanel.add(dayCheckboxes[i]);
        }
        formPanel.add(daysPanel, c);

        // Show/hide day checkboxes based on schedule type
        scheduleTypeComboBox.addActionListener(e -> {
            ScheduledScript.ScheduleType type = (ScheduledScript.ScheduleType) scheduleTypeComboBox.getSelectedItem();
            boolean showDays = type == ScheduledScript.ScheduleType.CUSTOM;
            boolean isHourly = type == ScheduledScript.ScheduleType.HOURLY;

            for (JCheckBox cb : dayCheckboxes) {
                cb.setEnabled(showDays);
            }

            // For hourly schedule, change the time label to "Start From:"
            Component[] components = formPanel.getComponents();
            for (Component component : components) {
                if (component instanceof JLabel) {
                    JLabel label = (JLabel) component;
                    if (label.getText().equals("Start Time:") || label.getText().equals("Start From:")) {
                        label.setText(isHourly ? "Start From:" : "Start Time:");
                        break;
                    }
                }
            }
        });

        // Buttons
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 1;
        addButton = new JButton("Add");
        addButton.addActionListener(this::onAddButtonClicked);
        formPanel.add(addButton, c);

        c.gridx = 1;
        c.gridy = 5;
        editButton = new JButton("Update");
        editButton.addActionListener(this::onEditButtonClicked);
        editButton.setEnabled(false);
        formPanel.add(editButton, c);

        c.gridx = 2;
        c.gridy = 5;
        removeButton = new JButton("Remove");
        removeButton.addActionListener(this::onRemoveButtonClicked);
        removeButton.setEnabled(false);
        formPanel.add(removeButton, c);

        // Run Now button
        c.gridx = 0;
        c.gridy = 6;
        c.gridwidth = 3;
        runNowButton = new JButton("Run Selected Script Now");
        runNowButton.addActionListener(this::onRunNowButtonClicked);
        runNowButton.setEnabled(false);
        formPanel.add(runNowButton, c);

        // Default script section
//        JPanel defaultScriptPanel = new JPanel(new BorderLayout(5, 0));
//        defaultScriptPanel.setBorder(BorderFactory.createTitledBorder("Default Script"));

//        defaultScriptComboBox = new JComboBox<>();
//        defaultScriptComboBox.insertItemAt("", 0);
//        defaultScriptComboBox.setSelectedItem(this.config.defaultScript());
//        defaultScriptComboBox.addActionListener(e -> {
//            String selected = (String) defaultScriptComboBox.getSelectedItem();
//            if (selected != null) {
//                this.config.defaultScript();
//            }
//        });
//
//        defaultScriptPanel.add(new JLabel("Script:"), BorderLayout.WEST);
//        defaultScriptPanel.add(defaultScriptComboBox, BorderLayout.CENTER);

        // Table selection listener
        scheduleTable.getSelectionModel().addListSelectionListener(e -> {
            boolean hasSelection = scheduleTable.getSelectedRow() >= 0;
            removeButton.setEnabled(hasSelection);
            editButton.setEnabled(hasSelection);
            runNowButton.setEnabled(hasSelection);

            if (hasSelection) {
                int row = scheduleTable.getSelectedRow();
                loadScriptToForm(plugin.getScheduledScripts().get(row));
            }
        });

        // Add components to main panel
        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setLayout(new BorderLayout(0, 10));

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Scheduled Scripts"));
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        JPanel formWrapper = new JPanel(new BorderLayout());
        formWrapper.setBorder(BorderFactory.createTitledBorder("Add/Edit Schedule"));
        formWrapper.add(formPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(formWrapper, BorderLayout.CENTER);
//        bottomPanel.add(defaultScriptPanel, BorderLayout.SOUTH);

        mainPanel.add(tablePanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Initialize table with saved schedules
        refreshTable();
        updateScriptList();
    }

    private void updateScriptList() {
        List<String> scripts = plugin.getAvailableScripts();

        // If no scripts are available yet, don't clear the existing items
        if (scripts == null || scripts.isEmpty()) {
            return;
        }

        scriptComboBox.removeAllItems();
        for (String script : scripts) {
            scriptComboBox.addItem(script);
        }

//        defaultScriptComboBox.removeAllItems();
//        defaultScriptComboBox.addItem(""); // Empty option
//        for (String script : scripts) {
//            defaultScriptComboBox.addItem(script);
//        }

//        // Set the default script if it was previously configured
//        String defaultScript = this.config.defaultScript();
//        if (defaultScript != null && !defaultScript.isEmpty()) {
//            defaultScriptComboBox.setSelectedItem(defaultScript);
//        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);

        for (ScheduledScript script : plugin.getScheduledScripts()) {
            tableModel.addRow(new Object[]{
                    script.getScriptName(),
                    script.getStartTime(),
                    script.getDuration(),
                    script.getScheduleDescription(),
                    script.isEnabled()
            });
        }
    }

    private void loadScriptToForm(ScheduledScript script) {
        editingScript = script;

        // Set script
        scriptComboBox.setSelectedItem(script.getScriptName());

        // Set time
        try {
            LocalTime time = LocalTime.parse(script.getStartTime(), TIME_FORMATTER);
            // Use a safer way to convert LocalTime to java.util.Date
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, time.getHour());
            calendar.set(Calendar.MINUTE, time.getMinute());
            calendar.set(Calendar.SECOND, 0);
            timeSpinner.setValue(calendar.getTime());
        } catch (Exception e) {
            // Use current time if parsing fails
            timeSpinner.setValue(new java.util.Date());
        }

        // Set duration
        if (script.getDuration() != null && !script.getDuration().isEmpty()) {
            enableDurationCheckbox.setSelected(true);
            durationSpinner.setEnabled(true);
            try {
                LocalTime duration = LocalTime.parse(script.getDuration(), TIME_FORMATTER);
                // Use a safer way to convert LocalTime to java.util.Date
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
            // Set a default value even when disabled
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 1);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            durationSpinner.setValue(calendar.getTime());
        }

        // Set schedule type
        scheduleTypeComboBox.setSelectedItem(script.getScheduleType());

        // Set days for custom schedule
        for (int i = 0; i < 7; i++) {
            dayCheckboxes[i].setSelected(script.getDays().contains(i + 1));
        }

        // Update button text
        addButton.setText("Add New");
    }

    private void clearForm() {
        editingScript = null;

        // Reset script selection
        if (scriptComboBox.getItemCount() > 0) {
            scriptComboBox.setSelectedIndex(0);
        }

        // Reset time to current time
        timeSpinner.setValue(new java.util.Date());

        // Reset duration to 1 hour
        enableDurationCheckbox.setSelected(true);
        durationSpinner.setEnabled(true);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        durationSpinner.setValue(calendar.getTime());

        // Reset schedule type
        scheduleTypeComboBox.setSelectedItem(ScheduledScript.ScheduleType.DAILY);

        // Uncheck all days
        for (JCheckBox cb : dayCheckboxes) {
            cb.setSelected(false);
        }

        // Reset button text
        addButton.setText("Add");
    }

    private ScheduledScript getScriptFromForm() {
        String scriptName = (String) scriptComboBox.getSelectedItem();
        if (scriptName == null || scriptName.isEmpty()) {
            return null;
        }

        // Get time
        java.util.Date date = (java.util.Date) timeSpinner.getValue();
        String timeStr = new java.text.SimpleDateFormat("HH:mm").format(date);

        // Get duration (if enabled)
        String durationStr = "";
        if (enableDurationCheckbox.isSelected()) {
            java.util.Date durationDate = (java.util.Date) durationSpinner.getValue();
            durationStr = new java.text.SimpleDateFormat("HH:mm").format(durationDate);
        }

        // Get schedule type
        ScheduledScript.ScheduleType scheduleType = (ScheduledScript.ScheduleType) scheduleTypeComboBox.getSelectedItem();

        // Get selected days for custom schedule
        List<Integer> days = new ArrayList<>();
        if (scheduleType == ScheduledScript.ScheduleType.CUSTOM) {
            for (int i = 0; i < 7; i++) {
                if (dayCheckboxes[i].isSelected()) {
                    days.add(i + 1); // 1 = Monday, 7 = Sunday
                }
            }
        }

        return new ScheduledScript(scriptName, timeStr, durationStr, scheduleType, days, true);
    }

    private void onAddButtonClicked(ActionEvent e) {
        ScheduledScript script = getScriptFromForm();
        if (script != null) {
            plugin.addScheduledScript(script);
            refreshTable();
            clearForm();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Please select a script and set a valid time.",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onEditButtonClicked(ActionEvent e) {
        if (editingScript == null) {
            return;
        }

        ScheduledScript newScript = getScriptFromForm();
        if (newScript != null) {
            plugin.updateScheduledScript(editingScript, newScript);
            refreshTable();
            clearForm();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Please select a script and set a valid time.",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onRemoveButtonClicked(ActionEvent e) {
        int row = scheduleTable.getSelectedRow();
        if (row >= 0) {
            ScheduledScript script = plugin.getScheduledScripts().get(row);
            plugin.removeScheduledScript(script);
            refreshTable();
            clearForm();
        }
    }

    private void onRunNowButtonClicked(ActionEvent e) {
        int row = scheduleTable.getSelectedRow();
        if (row >= 0) {
            ScheduledScript script = plugin.getScheduledScripts().get(row);
            plugin.startScript(script.getScriptName());
        }
    }
}
