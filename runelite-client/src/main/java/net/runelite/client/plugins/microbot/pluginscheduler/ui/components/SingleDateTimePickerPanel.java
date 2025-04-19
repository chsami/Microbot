package net.runelite.client.plugins.microbot.pluginscheduler.ui.components;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.function.Consumer;

/**
 * A panel for selecting a single date and time with presets
 */
public class SingleDateTimePickerPanel extends JPanel {
    private final DateTimePickerPanel dateTimePicker;
    private Consumer<LocalDateTime> dateTimeChangeListener;
    
    public SingleDateTimePickerPanel() {
        this(LocalDateTime.now().plusHours(1)); // Default to one hour from now
    }
    
    public SingleDateTimePickerPanel(LocalDateTime initialDateTime) {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(new EmptyBorder(5, 5, 5, 5));
        
        dateTimePicker = new DateTimePickerPanel(initialDateTime.toLocalDate(), initialDateTime.toLocalTime());
        
        // Create a main panel with title
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel titleLabel = new JLabel("Select Date and Time:");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        mainPanel.add(dateTimePicker, BorderLayout.CENTER);
        
        // Add presets panel
        JPanel presetsPanel = createPresetsPanel();
        mainPanel.add(presetsPanel, BorderLayout.SOUTH);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Set up change listener
        dateTimePicker.setDateTimeChangeListener(this::notifyDateTimeChanged);
    }
    
    private JPanel createPresetsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        JLabel presetsLabel = new JLabel("Quick Presets:");
        presetsLabel.setForeground(Color.WHITE);
        presetsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(presetsLabel);
        
        // Flow panel for buttons
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        buttonsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        LocalDateTime now = LocalDateTime.now();
        
        // Common time presets
        addPresetButton(buttonsPanel, "In 1 hour", now.plusHours(1));
        addPresetButton(buttonsPanel, "In 3 hours", now.plusHours(3));
        addPresetButton(buttonsPanel, "Tomorrow", now.plusDays(1).withHour(9).withMinute(0));
        addPresetButton(buttonsPanel, "This evening", now.withHour(19).withMinute(0));
        addPresetButton(buttonsPanel, "Next week", now.plusWeeks(1));
        addPresetButton(buttonsPanel, "Next month", now.plusMonths(1));
        
        panel.add(buttonsPanel);
        return panel;
    }
    
    private void addPresetButton(JPanel panel, String label, LocalDateTime dateTime) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.setBackground(ColorScheme.DARK_GRAY_COLOR);
        button.setForeground(Color.WHITE);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.addActionListener(e -> setDateTime(dateTime));
        panel.add(button);
    }
    
    private void notifyDateTimeChanged(LocalDateTime dateTime) {
        if (dateTimeChangeListener != null) {
            dateTimeChangeListener.accept(dateTime);
        }
    }
    
    public LocalDateTime getDateTime() {
        return dateTimePicker.getDateTime();
    }
    
    public void setDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            // Create a default time (1 hour from now)
            LocalDateTime defaultDateTime = LocalDateTime.now().plusHours(1);
            try {
                // Try using individual setters if available
                dateTimePicker.setDate(LocalDate.now());
                dateTimePicker.setTime(
                    Integer.valueOf(defaultDateTime.getHour()),
                    Integer.valueOf(defaultDateTime.getMinute())
                );
            } catch (Exception e) {
                // Fall back to the combined setter if individual ones aren't available
                dateTimePicker.setDateTime(defaultDateTime);
            }
        } else {
            try {
                // Try using individual setters if available
                dateTimePicker.setDate(dateTime.toLocalDate());
                dateTimePicker.setTime(
                    Integer.valueOf(dateTime.getHour()),
                    Integer.valueOf(dateTime.getMinute())
                );
            } catch (Exception e) {
                // Fall back to the combined setter
                dateTimePicker.setDateTime(dateTime);
            }
        }
    }
    
    public void setDateTimeChangeListener(Consumer<LocalDateTime> listener) {
        this.dateTimeChangeListener = listener;
    }
}