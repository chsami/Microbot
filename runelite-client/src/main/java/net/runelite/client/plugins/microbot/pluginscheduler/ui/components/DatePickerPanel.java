package net.runelite.client.plugins.microbot.pluginscheduler.ui.components;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * A custom date picker component that shows a calendar popup for date selection
 */
public class DatePickerPanel extends JPanel {
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private JTextField dateField;
    private LocalDate selectedDate;
    private JPopupMenu calendarPopup;
    private JPanel calendarPanel;
    private JLabel monthYearLabel;
    private YearMonth currentYearMonth;
    private Consumer<LocalDate> dateChangeListener;
    
    public DatePickerPanel() {
        this(LocalDate.now());
    }
    
    public DatePickerPanel(LocalDate initialDate) {
        this.selectedDate = initialDate;
        this.currentYearMonth = YearMonth.from(initialDate);
        setLayout(new BorderLayout(5, 0));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(new EmptyBorder(0, 0, 0, 0));
        
        initComponents();
    }

    private void initComponents() {
        // Date text field with formatted date
        dateField = new JTextField(selectedDate.format(dateFormatter), 10);
        dateField.setForeground(Color.WHITE);
        dateField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        dateField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)));
        
        // Calendar button with ImageIcon
        JButton calendarButton = new JButton();
        calendarButton.setFocusPainted(false);
        calendarButton.setForeground(Color.WHITE);
        calendarButton.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        calendarButton.setPreferredSize(new Dimension(30, dateField.getPreferredSize().height));

        
        try {
            ImageIcon icon = new ImageIcon(getClass().getResource("/net/runelite/client/plugins/microbot/pluginscheduler/"+"calendar-icon.png"));
            // Scale the icon to fit the button
            Image img = icon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            calendarButton.setIcon(new ImageIcon(img));
        } catch (Exception e) {
            // Fallback to simple text if icon can't be loaded
            calendarButton.setText("▼");
        }
        
        // Initialize calendar popup
        createCalendarPopup();
        
        // Show calendar on button click
        calendarButton.addActionListener(e -> {
            Point location = dateField.getLocationOnScreen();
            calendarPopup.show(dateField, 0, dateField.getHeight());
            calendarPopup.setLocation(location.x, location.y + dateField.getHeight());
        });
        
        // Update date when text field changes
        dateField.addActionListener(e -> {
            try {
                LocalDate newDate = LocalDate.parse(dateField.getText(), dateFormatter);
                setSelectedDate(newDate);
            } catch (Exception ex) {
                // If parsing fails, revert to current selection
                dateField.setText(selectedDate.format(dateFormatter));
            }
        });
        
        // Add components to panel
        add(dateField, BorderLayout.CENTER);
        add(calendarButton, BorderLayout.EAST);
    }

    private void createCalendarPopup() {
        calendarPopup = new JPopupMenu();
        calendarPopup.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
        
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Month navigation panel
        JPanel navigationPanel = new JPanel(new BorderLayout());
        navigationPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JButton prevButton = new JButton("<");
        prevButton.setFocusPainted(false);
        prevButton.setForeground(Color.WHITE);
        prevButton.setBackground(ColorScheme.BRAND_ORANGE);
        prevButton.addActionListener(e -> {
            currentYearMonth = currentYearMonth.minusMonths(1);
            updateCalendar();
        });
        
        JButton nextButton = new JButton(">");
        nextButton.setFocusPainted(false);
        nextButton.setForeground(Color.WHITE);
        nextButton.setBackground(ColorScheme.BRAND_ORANGE);
        nextButton.addActionListener(e -> {
            currentYearMonth = currentYearMonth.plusMonths(1);
            updateCalendar();
        });
        
        monthYearLabel = new JLabel("", SwingConstants.CENTER);
        monthYearLabel.setForeground(Color.WHITE);
        monthYearLabel.setFont(FontManager.getRunescapeBoldFont());
        
        navigationPanel.add(prevButton, BorderLayout.WEST);
        navigationPanel.add(monthYearLabel, BorderLayout.CENTER);
        navigationPanel.add(nextButton, BorderLayout.EAST);
        
        // Calendar panel (will be populated in updateCalendar())
        calendarPanel = new JPanel(new GridLayout(7, 7, 2, 2));
        calendarPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        contentPanel.add(navigationPanel, BorderLayout.NORTH);
        contentPanel.add(calendarPanel, BorderLayout.CENTER);
        
        calendarPopup.add(contentPanel);
        
        // Update calendar when shown
        calendarPopup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                currentYearMonth = YearMonth.from(selectedDate);
                updateCalendar();
            }
            
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
            
            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {}
        });
    }

    private void updateCalendar() {
        calendarPanel.removeAll();
        
        // Update month/year label
        monthYearLabel.setText(currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        
        // Day of week headers
        String[] daysOfWeek = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        for (String day : daysOfWeek) {
            JLabel label = new JLabel(day, SwingConstants.CENTER);
            label.setForeground(Color.LIGHT_GRAY);
            calendarPanel.add(label);
        }
        
        // Get the first day of the month and adjust for Monday-based week
        LocalDate firstOfMonth = currentYearMonth.atDay(1);
        int dayOfWeekValue = firstOfMonth.getDayOfWeek().getValue(); // 1 for Monday, 7 for Sunday
        
        // Add empty cells before the first day of the month
        for (int i = 1; i < dayOfWeekValue; i++) {
            calendarPanel.add(new JLabel());
        }
        
        // Add day buttons
        for (int day = 1; day <= currentYearMonth.lengthOfMonth(); day++) {
            final int dayValue = day;
            final LocalDate date = currentYearMonth.atDay(day);
            
            JButton dayButton = new JButton(String.valueOf(day));
            dayButton.setFocusPainted(false);
            dayButton.setMargin(new Insets(2, 2, 2, 2));
            
            // Highlight today
            if (date.equals(LocalDate.now())) {
                dayButton.setBackground(new Color(70, 130, 180)); // Steel blue
                dayButton.setForeground(Color.WHITE);
            } 
            // Highlight selected date
            else if (date.equals(selectedDate)) {
                dayButton.setBackground(ColorScheme.BRAND_ORANGE);
                dayButton.setForeground(Color.WHITE);
            } 
            // Weekend days
            else if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                dayButton.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
                dayButton.setForeground(Color.LIGHT_GRAY);
            }
            // Regular days
            else {
                dayButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
                dayButton.setForeground(Color.WHITE);
            }
            
            // Select this date and close popup when clicked
            dayButton.addActionListener(e -> {
                setSelectedDate(date);
                calendarPopup.setVisible(false);
            });
            
            calendarPanel.add(dayButton);
        }
        
        calendarPanel.revalidate();
        calendarPanel.repaint();
    }
    
    public LocalDate getSelectedDate() {
        return selectedDate;
    }
    
    public void setSelectedDate(LocalDate date) {
        this.selectedDate = date;
        dateField.setText(date.format(dateFormatter));
        
        if (dateChangeListener != null) {
            dateChangeListener.accept(date);
        }
    }
    
    public void setDateChangeListener(Consumer<LocalDate> listener) {
        this.dateChangeListener = listener;
    }
    
    public void setEditable(boolean editable) {
        dateField.setEditable(editable);
    }
    
    public JTextField getTextField() {
        return dateField;
    }
}