package net.runelite.client.plugins.microbot.pluginscheduler.ui.components;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.awt.event.FocusEvent;
import java.awt.event.FocusAdapter;
/**
 * A custom time picker component with hours and minutes selection
 */
public class TimePickerPanel extends JPanel {
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final JTextField timeField; // Changed from JFormattedTextField
    private LocalTime selectedTime;
    private Consumer<LocalTime> timeChangeListener;
    
    public TimePickerPanel() {
        this(LocalTime.of(9, 0)); // Default to 9:00
    }
    
    public TimePickerPanel(LocalTime initialTime) {
        this.selectedTime = initialTime;
        setLayout(new BorderLayout(5, 0));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(new EmptyBorder(0, 0, 0, 0));
        
        // Create a regular text field instead of formatted
        timeField = new JTextField(selectedTime.format(timeFormatter));
        timeField.setForeground(Color.WHITE);
        timeField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        timeField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)));
        
        // Button to show time picker popup
        JButton timeButton = new JButton("🕒");
        timeButton.setFocusPainted(false);
        timeButton.setForeground(Color.WHITE);
        timeButton.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        timeButton.setPreferredSize(new Dimension(30, timeField.getPreferredSize().height));
        timeButton.addActionListener(e -> showTimePickerPopup());
        
        add(timeField, BorderLayout.CENTER);
        add(timeButton, BorderLayout.EAST);
        
        // Update time when text changes
        timeField.addActionListener(e -> {
            try {
                String text = timeField.getText();
                LocalTime parsedTime = LocalTime.parse(text, timeFormatter);
                setSelectedTime(parsedTime);
            } catch (Exception ex) {
                // Reset to current value if parsing fails
                timeField.setText(selectedTime.format(timeFormatter));
            }
        });
        
        // Add a focus listener to validate when the field loses focus
        timeField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    String text = timeField.getText();
                    LocalTime parsedTime = LocalTime.parse(text, timeFormatter);
                    setSelectedTime(parsedTime);
                } catch (Exception ex) {
                    // Reset to current value if parsing fails
                    timeField.setText(selectedTime.format(timeFormatter));
                }
            }
        });
    }
    
    private void showTimePickerPopup() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
        
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // Hour and minute spinners
        JPanel timeControls = new JPanel(new FlowLayout());
        timeControls.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Hour spinner (0-23)
        SpinnerNumberModel hourModel = new SpinnerNumberModel(selectedTime.getHour(), 0, 23, 1);
        JSpinner hourSpinner = new JSpinner(hourModel);
        JComponent hourEditor = new JSpinner.NumberEditor(hourSpinner, "00");
        hourSpinner.setEditor(hourEditor);
        hourSpinner.setPreferredSize(new Dimension(60, 30));
        
        // Minute spinner (0-59)
        SpinnerNumberModel minuteModel = new SpinnerNumberModel(selectedTime.getMinute(), 0, 59, 1);
        JSpinner minuteSpinner = new JSpinner(minuteModel);
        JComponent minuteEditor = new JSpinner.NumberEditor(minuteSpinner, "00");
        minuteSpinner.setEditor(minuteEditor);
        minuteSpinner.setPreferredSize(new Dimension(60, 30));
        
        JLabel colonLabel = new JLabel(":");
        colonLabel.setForeground(Color.WHITE);
        
        timeControls.add(hourSpinner);
        timeControls.add(colonLabel);
        timeControls.add(minuteSpinner);
        
        // Common time presets
        JPanel presets = new JPanel(new GridLayout(2, 3, 5, 5));
        presets.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        addTimePresetButton(presets, "00:00", popup);
        addTimePresetButton(presets, "06:00", popup);
        addTimePresetButton(presets, "09:00", popup);
        addTimePresetButton(presets, "12:00", popup);
        addTimePresetButton(presets, "18:00", popup);
        addTimePresetButton(presets, "22:00", popup);
        
        // Apply button
        JButton applyButton = new JButton("Apply");
        applyButton.setFocusPainted(false);
        applyButton.setBackground(ColorScheme.PROGRESS_COMPLETE_COLOR);
        applyButton.setForeground(Color.WHITE);
        applyButton.addActionListener(e -> {
            int hour = (Integer) hourSpinner.getValue();
            int minute = (Integer) minuteSpinner.getValue();
            setSelectedTime(LocalTime.of(hour, minute));
            popup.setVisible(false);
        });
        
        contentPanel.add(timeControls);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(new JLabel("Presets:"));
        contentPanel.add(presets);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(applyButton);
        
        popup.add(contentPanel);
        popup.show(this, 0, this.getHeight());
    }
    
    private void addTimePresetButton(JPanel panel, String timeText, JPopupMenu popup) {
        JButton button = new JButton(timeText);
        button.setFocusPainted(false);
        button.setBackground(ColorScheme.DARK_GRAY_COLOR);
        button.setForeground(Color.WHITE);
        button.addActionListener(e -> {
            setSelectedTime(LocalTime.parse(timeText));
            popup.setVisible(false);
        });
        panel.add(button);
    }
    
    public LocalTime getSelectedTime() {
        return selectedTime;
    }
    
    public void setSelectedTime(LocalTime time) {
        this.selectedTime = time;
        timeField.setText(time.format(timeFormatter));
        
        if (timeChangeListener != null) {
            timeChangeListener.accept(time);
        }
    }
    
    public void setTimeChangeListener(Consumer<LocalTime> listener) {
        this.timeChangeListener = listener;
    }
    
    public void setEditable(boolean editable) {
        timeField.setEditable(editable);
    }
    
    public JTextField getTextField() {
        return timeField;
    }
}