package net.runelite.client.plugins.microbot.pluginscheduler.ui.components;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.function.BiConsumer;

/**
 * A panel for selecting a date range with start and end dates
 */
public class DateRangePanel extends JPanel {
    private final DatePickerPanel startDatePicker;
    private final DatePickerPanel endDatePicker;
    private BiConsumer<LocalDate, LocalDate> rangeChangeListener;
    
    public DateRangePanel() {
        this(LocalDate.now(), LocalDate.now().plusMonths(1));
    }
    
    public DateRangePanel(LocalDate startDate, LocalDate endDate) {
        setLayout(new GridBagLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(new EmptyBorder(5, 5, 5, 5));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        
        JLabel startLabel = new JLabel("Start Date:");
        startLabel.setForeground(Color.WHITE);
        startLabel.setFont(FontManager.getRunescapeSmallFont());
        add(startLabel, gbc);
        
        gbc.gridx = 1;
        startDatePicker = new DatePickerPanel(startDate);
        add(startDatePicker, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel endLabel = new JLabel("End Date:");
        endLabel.setForeground(Color.WHITE);
        endLabel.setFont(FontManager.getRunescapeSmallFont());
        add(endLabel, gbc);
        
        gbc.gridx = 1;
        endDatePicker = new DatePickerPanel(endDate);
        add(endDatePicker, gbc);
        
        // Add common presets panel
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(10, 2, 2, 2);
        
        JPanel presetsPanel = createPresetsPanel();
        add(presetsPanel, gbc);
        
        // Set up change listeners
        startDatePicker.setDateChangeListener(d -> {
            // Ensure end date is not before start date
            if (endDatePicker.getSelectedDate().isBefore(d)) {
                endDatePicker.setSelectedDate(d);
            }
            notifyRangeChanged();
        });
        
        endDatePicker.setDateChangeListener(d -> {
            // Ensure start date is not after end date
            if (startDatePicker.getSelectedDate().isAfter(d)) {
                startDatePicker.setSelectedDate(d);
            }
            notifyRangeChanged();
        });
    }
    
    private JPanel createPresetsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel presetsLabel = new JLabel("Quick Presets:");
        presetsLabel.setForeground(Color.WHITE);
        panel.add(presetsLabel);
        
        // Common date range presets
        LocalDate today = LocalDate.now();
        
        addPresetButton(panel, "Today", today, today);
        addPresetButton(panel, "This Week", today, today.plusDays(7 - today.getDayOfWeek().getValue()));
        addPresetButton(panel, "This Month", today, today.withDayOfMonth(today.lengthOfMonth()));
        addPresetButton(panel, "Next 7 Days", today, today.plusDays(7));
        addPresetButton(panel, "Next 30 Days", today, today.plusDays(30));
        addPresetButton(panel, "Next 90 Days", today, today.plusDays(90));
        
        return panel;
    }
    
    private void addPresetButton(JPanel panel, String label, LocalDate start, LocalDate end) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.setBackground(ColorScheme.DARK_GRAY_COLOR);
        button.setForeground(Color.WHITE);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.addActionListener(e -> {
            startDatePicker.setSelectedDate(start);
            endDatePicker.setSelectedDate(end);
        });
        panel.add(button);
    }
    
    private void notifyRangeChanged() {
        if (rangeChangeListener != null) {
            rangeChangeListener.accept(getStartDate(), getEndDate());
        }
    }
    
    public LocalDate getStartDate() {
        return startDatePicker.getSelectedDate();
    }
    
    public LocalDate getEndDate() {
        return endDatePicker.getSelectedDate();
    }
    
    public void setStartDate(LocalDate date) {
        startDatePicker.setSelectedDate(date);
    }
    
    public void setEndDate(LocalDate date) {
        endDatePicker.setSelectedDate(date);
    }
    
    public void setRangeChangeListener(BiConsumer<LocalDate, LocalDate> listener) {
        this.rangeChangeListener = listener;
    }
    
    public void setEditable(boolean editable) {
        startDatePicker.setEditable(editable);
        endDatePicker.setEditable(editable);
    }
    
    public DatePickerPanel getStartDatePicker() {
        return startDatePicker;
    }
    
    public DatePickerPanel getEndDatePicker() {
        return endDatePicker;
    }
}