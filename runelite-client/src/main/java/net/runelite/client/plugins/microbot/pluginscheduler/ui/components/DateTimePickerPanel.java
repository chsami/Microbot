package net.runelite.client.plugins.microbot.pluginscheduler.ui.components;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.function.Consumer;

/**
 * A combined date and time picker panel
 */
public class DateTimePickerPanel extends JPanel {
    private final DatePickerPanel datePicker;
    private final TimePickerPanel timePicker;
    private Consumer<LocalDateTime> dateTimeChangeListener;
    
    public DateTimePickerPanel() {
        this(LocalDate.now(), LocalTime.now());
    }
    
    public DateTimePickerPanel(LocalDate date, LocalTime time) {
        setLayout(new BorderLayout(10, 0));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(new EmptyBorder(0, 0, 0, 0));
        
        datePicker = new DatePickerPanel(date);
        timePicker = new TimePickerPanel(time);
        
        JPanel container = new JPanel(new BorderLayout(5, 0));
        container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        container.add(datePicker, BorderLayout.CENTER);
        container.add(timePicker, BorderLayout.EAST);
        
        add(container, BorderLayout.CENTER);
        
        // Set up change listeners
        datePicker.setDateChangeListener(d -> notifyDateTimeChanged());
        timePicker.setTimeChangeListener(t -> notifyDateTimeChanged());
    }
    
    private void notifyDateTimeChanged() {
        if (dateTimeChangeListener != null) {
            dateTimeChangeListener.accept(getDateTime());
        }
    }
    
    public LocalDateTime getDateTime() {
        return LocalDateTime.of(datePicker.getSelectedDate(), timePicker.getSelectedTime());
    }
    
    public void setDateTime(LocalDateTime dateTime) {
        datePicker.setSelectedDate(dateTime.toLocalDate());
        timePicker.setSelectedTime(dateTime.toLocalTime());
    }
    
    public void setDateTimeChangeListener(Consumer<LocalDateTime> listener) {
        this.dateTimeChangeListener = listener;
    }
    
    public void setEditable(boolean editable) {
        datePicker.setEditable(editable);
        timePicker.setEditable(editable);
    }
    
    public DatePickerPanel getDatePicker() {
        return datePicker;
    }
    
    public TimePickerPanel getTimePicker() {
        return timePicker;
    }

    public void setDate(LocalDate date) {
        datePicker.setSelectedDate(date);
        notifyDateTimeChanged();
    }

    public void setTime(Integer hour, Integer minute) {        
        LocalTime time = LocalTime.of(hour, minute);
        timePicker.setSelectedTime(time);
        notifyDateTimeChanged();
    }
}