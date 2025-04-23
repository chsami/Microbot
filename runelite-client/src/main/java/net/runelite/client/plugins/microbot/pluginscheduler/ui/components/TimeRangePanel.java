package net.runelite.client.plugins.microbot.pluginscheduler.ui.components;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalTime;
import java.util.function.BiConsumer;

/**
 * A panel for selecting a time range with start and end times
 */
public class TimeRangePanel extends JPanel {
    private final TimePickerPanel startTimePicker;
    private final TimePickerPanel endTimePicker;
    private BiConsumer<LocalTime, LocalTime> rangeChangeListener;
    
    public TimeRangePanel() {
        this(LocalTime.of(9, 0), LocalTime.of(17, 0));
    }
    
    public TimeRangePanel(LocalTime startTime, LocalTime endTime) {
        setLayout(new GridBagLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(new EmptyBorder(5, 5, 5, 5));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        
        JLabel startLabel = new JLabel("Start Time:");
        startLabel.setForeground(Color.WHITE);
        startLabel.setFont(FontManager.getRunescapeSmallFont());
        add(startLabel, gbc);
        
        gbc.gridx = 1;
        startTimePicker = new TimePickerPanel(startTime);
        add(startTimePicker, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel endLabel = new JLabel("End Time:");
        endLabel.setForeground(Color.WHITE);
        endLabel.setFont(FontManager.getRunescapeSmallFont());
        add(endLabel, gbc);
        
        gbc.gridx = 1;
        endTimePicker = new TimePickerPanel(endTime);
        add(endTimePicker, gbc);
        
        // Add common presets panel
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(10, 2, 2, 2);
        
        JPanel presetsPanel = createPresetsPanel();
        add(presetsPanel, gbc);
        
        // Set up change listeners
        startTimePicker.setTimeChangeListener(t -> notifyRangeChanged());
        endTimePicker.setTimeChangeListener(t -> notifyRangeChanged());
    }
    
    private JPanel createPresetsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel presetsLabel = new JLabel("Quick Presets:");
        presetsLabel.setForeground(Color.WHITE);
        panel.add(presetsLabel);
        
        // Common time range presets
        addPresetButton(panel, "Morning (6-12)", LocalTime.of(6, 0), LocalTime.of(12, 0));
        addPresetButton(panel, "Afternoon (12-18)", LocalTime.of(12, 0), LocalTime.of(18, 0));
        addPresetButton(panel, "Evening (18-22)", LocalTime.of(18, 0), LocalTime.of(22, 0));
        addPresetButton(panel, "Night (22-6)", LocalTime.of(22, 0), LocalTime.of(6, 0));
        addPresetButton(panel, "Business Hours", LocalTime.of(9, 0), LocalTime.of(17, 0));
        
        return panel;
    }
    
    private void addPresetButton(JPanel panel, String label, LocalTime start, LocalTime end) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.setBackground(ColorScheme.DARK_GRAY_COLOR);
        button.setForeground(Color.WHITE);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.addActionListener(e -> {
            startTimePicker.setSelectedTime(start);
            endTimePicker.setSelectedTime(end);
        });
        panel.add(button);
    }
    
    private void notifyRangeChanged() {
        if (rangeChangeListener != null) {
            rangeChangeListener.accept(getStartTime(), getEndTime());
        }
    }
    
    public LocalTime getStartTime() {
        return startTimePicker.getSelectedTime();
    }
    
    public LocalTime getEndTime() {
        return endTimePicker.getSelectedTime();
    }
    
    public void setStartTime(LocalTime time) {
        startTimePicker.setSelectedTime(time);
    }
    
    public void setEndTime(LocalTime time) {
        endTimePicker.setSelectedTime(time);
    }
    
    public void setRangeChangeListener(BiConsumer<LocalTime, LocalTime> listener) {
        this.rangeChangeListener = listener;
    }
    
    public void setEditable(boolean editable) {
        startTimePicker.setEditable(editable);
        endTimePicker.setEditable(editable);
    }
    
    public TimePickerPanel getStartTimePicker() {
        return startTimePicker;
    }
    
    public TimePickerPanel getEndTimePicker() {
        return endTimePicker;
    }
}