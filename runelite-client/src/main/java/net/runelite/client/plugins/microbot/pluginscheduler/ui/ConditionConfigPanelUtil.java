package net.runelite.client.plugins.microbot.pluginscheduler.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.BankItemCountCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.InventoryItemCountCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.LootItemCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillLevelCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillXpCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.DayOfWeekCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeWindowCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.NotCondition;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class ConditionConfigPanelUtil {

    public static void createTimeConfigPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel) {
        JLabel durationLabel = new JLabel("Duration Range:");
        durationLabel.setForeground(Color.WHITE);
        durationLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(durationLabel, gbc);
        
        // Time range panel with combined HH:MM format
        gbc.gridy++;
        JPanel timeRangePanel = new JPanel(new GridLayout(2, 2, 5, 5));
        timeRangePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Min time input
        JLabel minTimeLabel = new JLabel("Min Duration (HH:MM):");
        minTimeLabel.setForeground(Color.WHITE);
        timeRangePanel.add(minTimeLabel);
        
        // Create combined min time panel
        JPanel minTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        minTimePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Use a formatted text field with validation for "HH:MM" format
        JTextField minTimeField = new JTextField(5);
        minTimeField.setText("01:00"); // Default 1 hour
        minTimeField.setForeground(Color.WHITE);
        minTimeField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        minTimePanel.add(minTimeField);
        
        timeRangePanel.add(minTimePanel);
        
        // Max time input
        JLabel maxTimeLabel = new JLabel("Max Duration (HH:MM):");
        maxTimeLabel.setForeground(Color.WHITE);
        timeRangePanel.add(maxTimeLabel);
        
        // Create combined max time panel
        JPanel maxTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        maxTimePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Use a formatted text field with validation for "HH:MM" format
        JTextField maxTimeField = new JTextField(5);
        maxTimeField.setText("02:30"); // Default 2:30 hours
        maxTimeField.setForeground(Color.WHITE);
        maxTimeField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        maxTimePanel.add(maxTimeField);
        
        timeRangePanel.add(maxTimePanel);
        
        panel.add(timeRangePanel, gbc);
        
        // Add input validation for time fields
        minTimeField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                validateTimeField(minTimeField, maxTimeField, true);
            }
        });
        
        maxTimeField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                validateTimeField(minTimeField, maxTimeField, false);
            }
        });
        
        // Randomization options
        gbc.gridy++;
        JPanel randomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        randomPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize within range");
        randomizeCheckBox.setForeground(Color.WHITE);
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setSelected(true);
        randomPanel.add(randomizeCheckBox);
        
        JLabel randomFactorLabel = new JLabel("Random Factor:");
        randomFactorLabel.setForeground(Color.WHITE);
        randomPanel.add(randomFactorLabel);
        
        SpinnerNumberModel randomFactorModel = new SpinnerNumberModel(0.3, 0.0, 1.0, 0.05);
        JSpinner randomFactorSpinner = new JSpinner(randomFactorModel);
        randomFactorSpinner.setPreferredSize(new Dimension(70, randomFactorSpinner.getPreferredSize().height));
        randomPanel.add(randomFactorSpinner);
        
        // Disable random factor when randomize is unchecked
        randomizeCheckBox.addChangeListener(e -> {
            randomFactorSpinner.setEnabled(randomizeCheckBox.isSelected());
        });
        
        panel.add(randomPanel, gbc);
        
        // Add a helpful description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Plugin will stop after a random time between min and max");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Store components for later access when creating conditions
        configPanel.putClientProperty("minTimeField", minTimeField);
        configPanel.putClientProperty("maxTimeField", maxTimeField);
        configPanel.putClientProperty("randomizeCheckBox", randomizeCheckBox);
        configPanel.putClientProperty("randomFactorSpinner", randomFactorSpinner);
    }
    /**
     * Validates a time field to ensure it contains a valid HH:MM format
     * Enforces min ≤ max constraint when comparing fields
     */
    private static void validateTimeField(JTextField minField, JTextField maxField, boolean isMinField) {
        try {
            // Parse the current field
            String text = isMinField ? minField.getText() : maxField.getText();
            String[] parts = text.split(":");
            
            if (parts.length != 2) {
                throw new NumberFormatException("Invalid format");
            }
            
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            
            // Validate ranges
            hours = Math.min(23, Math.max(0, hours));
            minutes = Math.min(59, Math.max(0, minutes));
            
            // Format properly
            String formatted = String.format("%02d:%02d", hours, minutes);
            
            if (isMinField) {
                minField.setText(formatted);
                
                // Check min ≤ max
                String[] maxParts = maxField.getText().split(":");
                if (maxParts.length == 2) {
                    int maxHours = Integer.parseInt(maxParts[0]);
                    int maxMinutes = Integer.parseInt(maxParts[1]);
                    
                    if (hours > maxHours || (hours == maxHours && minutes > maxMinutes)) {
                        maxField.setText(formatted);
                    }
                }
            } else {
                maxField.setText(formatted);
                
                // Check max ≥ min
                String[] minParts = minField.getText().split(":");
                if (minParts.length == 2) {
                    int minHours = Integer.parseInt(minParts[0]);
                    int minMinutes = Integer.parseInt(minParts[1]);
                    
                    if (hours < minHours || (hours == minHours && minutes < minMinutes)) {
                        minField.setText(formatted);
                    }
                }
            }
        } catch (NumberFormatException e) {
            // Reset to a default valid value if parsing fails
            if (isMinField) {
                minField.setText("01:00");
            } else {
                maxField.setText("02:00");
            }
        }
    }

     public static IntervalCondition createTimeCondition(JPanel configPanel) {
        JTextField minTimeField = (JTextField) configPanel.getClientProperty("minTimeField");
        JTextField maxTimeField = (JTextField) configPanel.getClientProperty("maxTimeField");
        JCheckBox randomizeCheckBox = (JCheckBox) configPanel.getClientProperty("randomizeCheckBox");
        JSpinner randomFactorSpinner = (JSpinner) configPanel.getClientProperty("randomFactorSpinner");
        
        // Parse min time
        String[] minParts = minTimeField.getText().split(":");
        int minHours = Integer.parseInt(minParts[0]);
        int minMinutes = Integer.parseInt(minParts[1]);
        
        // Parse max time
        String[] maxParts = maxTimeField.getText().split(":");
        int maxHours = Integer.parseInt(maxParts[0]);
        int maxMinutes = Integer.parseInt(maxParts[1]);
        
        boolean randomize = randomizeCheckBox.isSelected();
        double randomFactor = randomize ? (Double) randomFactorSpinner.getValue() : 0.0;
        
        Duration minDuration = Duration.ofHours(minHours).plusMinutes(minMinutes);
        Duration maxDuration = Duration.ofHours(maxHours).plusMinutes(maxMinutes);
        
        // Check if min and max are the same
        if (minHours == maxHours && minMinutes == maxMinutes) {
            return new IntervalCondition(minDuration, randomize, randomFactor);
        }
        
        // Create randomized interval condition
        return IntervalCondition.createRandomized(minDuration, maxDuration);
    }



    public static void createTimeWindowConfigPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel, boolean withInWindow) {
        JLabel startTimeLabel = new JLabel("Start Time (24h format):");
        startTimeLabel.setForeground(Color.WHITE);
        panel.add(startTimeLabel, gbc);
        
        // Start time panel with hour and minute
        gbc.gridy++;
        JPanel startTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        startTimePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        SpinnerNumberModel startHourModel = new SpinnerNumberModel(9, 0, 23, 1);
        JSpinner startHourSpinner = new JSpinner(startHourModel);
        startTimePanel.add(startHourSpinner);
        
        JLabel hourLabel = new JLabel("hour");
        hourLabel.setForeground(Color.WHITE);
        startTimePanel.add(hourLabel);
        
        SpinnerNumberModel startMinuteModel = new SpinnerNumberModel(0, 0, 59, 1);
        JSpinner startMinuteSpinner = new JSpinner(startMinuteModel);
        startTimePanel.add(startMinuteSpinner);
        
        JLabel minuteLabel = new JLabel("min");
        minuteLabel.setForeground(Color.WHITE);
        startTimePanel.add(minuteLabel);
        
        panel.add(startTimePanel, gbc);
        
        // End time
        gbc.gridy++;
        JLabel endTimeLabel = new JLabel("End Time (24h format):");
        endTimeLabel.setForeground(Color.WHITE);
        panel.add(endTimeLabel, gbc);
        
        gbc.gridy++;
        JPanel endTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        endTimePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        SpinnerNumberModel endHourModel = new SpinnerNumberModel(17, 0, 23, 1);
        JSpinner endHourSpinner = new JSpinner(endHourModel);
        endTimePanel.add(endHourSpinner);
        
        JLabel endHourLabel = new JLabel("hour");
        endHourLabel.setForeground(Color.WHITE);
        endTimePanel.add(endHourLabel);
        
        SpinnerNumberModel endMinuteModel = new SpinnerNumberModel(0, 0, 59, 1);
        JSpinner endMinuteSpinner = new JSpinner(endMinuteModel);
        endTimePanel.add(endMinuteSpinner);
        
        JLabel endMinuteLabel = new JLabel("min");
        endMinuteLabel.setForeground(Color.WHITE);
        endTimePanel.add(endMinuteLabel);
        
        panel.add(endTimePanel, gbc);
        
        // Description
        gbc.gridy++;
        JLabel descriptionLabel; 
        if(withInWindow){
            descriptionLabel = new JLabel("Plugin can not be stop\\started in the give only run during window");
        }else{
            descriptionLabel = new JLabel("Plugin will only stoped\\started during the given window");
        }
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        gbc.gridy++;
        JLabel crossDayLabel = new JLabel("Note: If start > end, window crosses midnight");
        crossDayLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        crossDayLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(crossDayLabel, gbc);
        
        // Store the components for later
        configPanel.putClientProperty("startHourSpinner", startHourSpinner);
        configPanel.putClientProperty("startMinuteSpinner", startMinuteSpinner);
        configPanel.putClientProperty("endHourSpinner", endHourSpinner);
        configPanel.putClientProperty("endMinuteSpinner", endMinuteSpinner);
        configPanel.putClientProperty("withInWindow", !withInWindow);
    }
    public static Condition createTimeWindowCondition(JPanel configPanel) {
        JSpinner startHourSpinner = (JSpinner) configPanel.getClientProperty("startHourSpinner");
        JSpinner startMinuteSpinner = (JSpinner) configPanel.getClientProperty("startMinuteSpinner");
        JSpinner endHourSpinner = (JSpinner) configPanel.getClientProperty("endHourSpinner");
        JSpinner endMinuteSpinner = (JSpinner) configPanel.getClientProperty("endMinuteSpinner");
        boolean withInWindow = (boolean) configPanel.getClientProperty("withInWindow");
        
        int startHour = (Integer) startHourSpinner.getValue();
        int startMinute = (Integer) startMinuteSpinner.getValue();
        int endHour = (Integer) endHourSpinner.getValue();
        int endMinute = (Integer) endMinuteSpinner.getValue();
        
        LocalTime startTime = LocalTime.of(startHour, startMinute);
        LocalTime endTime = LocalTime.of(endHour, endMinute);
        if (withInWindow) {
            return (Condition)(new TimeWindowCondition(startTime, endTime));
        }else{
            return (Condition)(new NotCondition(new TimeWindowCondition(startTime, endTime)));
        }
        
    }
    public static void createDayOfWeekConfigPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel) {
        JLabel daysLabel = new JLabel("Active Days:");
        daysLabel.setForeground(Color.WHITE);
        panel.add(daysLabel, gbc);
        
        // Preset options
        gbc.gridy++;
        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        presetPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JButton weekdaysButton = new JButton("Weekdays");
        weekdaysButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        weekdaysButton.setForeground(Color.WHITE);
        
        JButton weekendsButton = new JButton("Weekends");
        weekendsButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        weekendsButton.setForeground(Color.WHITE);
        
        JButton allDaysButton = new JButton("All Days");
        allDaysButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        allDaysButton.setForeground(Color.WHITE);
        
        presetPanel.add(weekdaysButton);
        presetPanel.add(weekendsButton);
        presetPanel.add(allDaysButton);
        
        panel.add(presetPanel, gbc);
        
        // Day checkboxes
        gbc.gridy++;
        JPanel daysPanel = new JPanel(new GridLayout(0, 3));
        daysPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        String[] dayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        JCheckBox[] dayCheckboxes = new JCheckBox[7];
        
        for (int i = 0; i < dayNames.length; i++) {
            dayCheckboxes[i] = new JCheckBox(dayNames[i]);
            dayCheckboxes[i].setBackground(ColorScheme.DARKER_GRAY_COLOR);
            dayCheckboxes[i].setForeground(Color.WHITE);
            daysPanel.add(dayCheckboxes[i]);
        }
        
        // Set up weekdays button
        weekdaysButton.addActionListener(e -> {
            for (int i = 0; i < 5; i++) {
                dayCheckboxes[i].setSelected(true);
            }
            dayCheckboxes[5].setSelected(false);
            dayCheckboxes[6].setSelected(false);
        });
        
        // Set up weekends button
        weekendsButton.addActionListener(e -> {
            for (int i = 0; i < 5; i++) {
                dayCheckboxes[i].setSelected(false);
            }
            dayCheckboxes[5].setSelected(true);
            dayCheckboxes[6].setSelected(true);
        });
        
        // Set up all days button
        allDaysButton.addActionListener(e -> {
            for (JCheckBox checkbox : dayCheckboxes) {
                checkbox.setSelected(true);
            }
        });
        
        panel.add(daysPanel, gbc);
        
        // Description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Plugin will only run on selected days of the week");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Store components for later access
        configPanel.putClientProperty("dayCheckboxes", dayCheckboxes);
    }
    public static void createSkillLevelConfigPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel, boolean stopConditionPanel) {
        JLabel skillLabel = new JLabel("Skill:");
        skillLabel.setForeground(Color.WHITE);
        panel.add(skillLabel, gbc);
        
        gbc.gridx++;
        JComboBox<String> skillComboBox = new JComboBox<>();
        for (Skill skill : Skill.values()) {
            skillComboBox.addItem(skill.getName());
        }
        panel.add(skillComboBox, gbc);
        
        // Target level panel
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        
        JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        levelPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel targetLevelLabel = new JLabel("Target Level:");
        targetLevelLabel.setForeground(Color.WHITE);
        levelPanel.add(targetLevelLabel);
        
        SpinnerNumberModel levelModel = new SpinnerNumberModel(10, 1, 99, 1);
        JSpinner levelSpinner = new JSpinner(levelModel);
        levelPanel.add(levelSpinner);
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize");
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setForeground(Color.WHITE);
        levelPanel.add(randomizeCheckBox);
        
        panel.add(levelPanel, gbc);
        
        // Min/Max panel (only visible when randomize is checked)
        gbc.gridy++;
        JPanel minMaxPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min:");
        minLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(5, 1, 99, 1);
        JSpinner minSpinner = new JSpinner(minModel);
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max:");
        maxLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(10, 1, 99, 1);
        JSpinner maxSpinner = new JSpinner(maxModel);
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
        
        // Toggle min/max panel visibility based on randomize checkbox
        randomizeCheckBox.addChangeListener(e -> {
            minMaxPanel.setVisible(randomizeCheckBox.isSelected());
            levelSpinner.setEnabled(!randomizeCheckBox.isSelected());
            
            // If enabling randomize, set min/max from current level
            if (randomizeCheckBox.isSelected()) {
                int level = (Integer) levelSpinner.getValue();
                minSpinner.setValue(Math.max(1, level - 5));
                maxSpinner.setValue(Math.min(99, level + 5));
            }
            
            panel.revalidate();
            panel.repaint();
        });
        
        // Add value change listeners for min/max validation
        minSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (min > max) {
                maxSpinner.setValue(min);
            }
        });
        
        maxSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (max < min) {
                minSpinner.setValue(max);
            }
        });
        
        // Description
        gbc.gridy++;
        JLabel descriptionLabel;
        if (stopConditionPanel) {
            descriptionLabel = new JLabel("Plugin will stop when skill reaches target level");
        } else {
            descriptionLabel = new JLabel("Plugin will only start when skill is at or above target level");
        }
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Store components
        configPanel.putClientProperty("skillComboBox", skillComboBox);
        configPanel.putClientProperty("levelSpinner", levelSpinner);
        configPanel.putClientProperty("minLevelSpinner", minSpinner);
        configPanel.putClientProperty("maxLevelSpinner", maxSpinner);
        configPanel.putClientProperty("randomizeSkillLevel", randomizeCheckBox);
    }

     // Update SkillLevelCondition to use randomization
    public static SkillLevelCondition createSkillLevelCondition(JPanel configPanel) {
        JComboBox<String> skillComboBox = (JComboBox<String>) configPanel.getClientProperty("skillComboBox");
        JCheckBox randomizeCheckBox = (JCheckBox) configPanel.getClientProperty("randomizeSkillLevel");
        
        String skillName = (String) skillComboBox.getSelectedItem();
        Skill skill = Skill.valueOf(skillName.toUpperCase());
        
        if (randomizeCheckBox.isSelected()) {
            JSpinner minLevelSpinner = (JSpinner) configPanel.getClientProperty("minLevelSpinner");
            JSpinner maxLevelSpinner = (JSpinner) configPanel.getClientProperty("maxLevelSpinner");
            
            int minLevel = (Integer) minLevelSpinner.getValue();
            int maxLevel = (Integer) maxLevelSpinner.getValue();
            
            return SkillLevelCondition.createRandomized(skill, minLevel, maxLevel);
        } else {
            JSpinner levelSpinner = (JSpinner) configPanel.getClientProperty("levelSpinner");
            int level = (Integer) levelSpinner.getValue();
            
            return new SkillLevelCondition(skill, level);
        }
    }
      
    public static void createSkillXpConfigPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel) {
        JLabel skillLabel = new JLabel("Skill:");
        skillLabel.setForeground(Color.WHITE);
        panel.add(skillLabel, gbc);
        
        gbc.gridx++;
        JComboBox<String> skillComboBox = new JComboBox<>();
        for (Skill skill : Skill.values()) {
            skillComboBox.addItem(skill.getName());
        }
        panel.add(skillComboBox, gbc);
        
        // Target XP panel
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        
        JPanel xpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        xpPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel targetXpLabel = new JLabel("Target XP:");
        targetXpLabel.setForeground(Color.WHITE);
        xpPanel.add(targetXpLabel);
        
        SpinnerNumberModel xpModel = new SpinnerNumberModel(10000, 1, 200000000, 1000);
        JSpinner xpSpinner = new JSpinner(xpModel);
        // Make XP spinner wider to accommodate larger numbers
        xpSpinner.setPreferredSize(new Dimension(100, xpSpinner.getPreferredSize().height));
        xpPanel.add(xpSpinner);
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize");
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setForeground(Color.WHITE);
        xpPanel.add(randomizeCheckBox);
        
        panel.add(xpPanel, gbc);
        
        // Min/Max panel (only visible when randomize is checked)
        gbc.gridy++;
        JPanel minMaxPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min XP:");
        minLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(5000, 1, 200000000, 1000);
        JSpinner minSpinner = new JSpinner(minModel);
        minSpinner.setPreferredSize(new Dimension(100, minSpinner.getPreferredSize().height));
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max XP:");
        maxLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(15000, 1, 200000000, 1000);
        JSpinner maxSpinner = new JSpinner(maxModel);
        maxSpinner.setPreferredSize(new Dimension(100, maxSpinner.getPreferredSize().height));
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
        
        // Toggle min/max panel visibility based on randomize checkbox
        randomizeCheckBox.addChangeListener(e -> {
            minMaxPanel.setVisible(randomizeCheckBox.isSelected());
            xpSpinner.setEnabled(!randomizeCheckBox.isSelected());
            
            // If enabling randomize, set min/max from current XP
            if (randomizeCheckBox.isSelected()) {
                int xp = (Integer) xpSpinner.getValue();
                minSpinner.setValue(Math.max(1, xp - 5000));
                maxSpinner.setValue(Math.min(200000000, xp + 5000));
            }
            
            panel.revalidate();
            panel.repaint();
        });
        
        // Add value change listeners for min/max validation
        minSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (min > max) {
                maxSpinner.setValue(min);
            }
        });
        
        maxSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (max < min) {
                minSpinner.setValue(max);
            }
        });
        
        // Description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Plugin will stop when skill XP reaches target value");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Store components
        configPanel.putClientProperty("xpSkillComboBox", skillComboBox);
        configPanel.putClientProperty("xpSpinner", xpSpinner);
        configPanel.putClientProperty("minXpSpinner", minSpinner);
        configPanel.putClientProperty("maxXpSpinner", maxSpinner);
        configPanel.putClientProperty("randomizeSkillXp", randomizeCheckBox);
    }
    public static SkillXpCondition createSkillXpCondition(JPanel configPanel) {
        JComboBox<String> skillComboBox = (JComboBox<String>) configPanel.getClientProperty("xpSkillComboBox");
        JCheckBox randomizeCheckBox = (JCheckBox) configPanel.getClientProperty("randomizeSkillXp");
        
        String skillName = (String) skillComboBox.getSelectedItem();
        Skill skill = Skill.valueOf(skillName.toUpperCase());
        
        if (randomizeCheckBox.isSelected()) {
            JSpinner minXpSpinner = (JSpinner) configPanel.getClientProperty("minXpSpinner");
            JSpinner maxXpSpinner = (JSpinner) configPanel.getClientProperty("maxXpSpinner");
            
            int minXp = (Integer) minXpSpinner.getValue();
            int maxXp = (Integer) maxXpSpinner.getValue();
            
            return SkillXpCondition.createRandomized(skill, minXp, maxXp);
        } else {
            JSpinner xpSpinner = (JSpinner) configPanel.getClientProperty("xpSpinner");
            int xp = (Integer) xpSpinner.getValue();
            
            return new SkillXpCondition(skill, xp);
        }
    }
    public static DayOfWeekCondition createDayOfWeekCondition(JPanel configPanel) {
            JCheckBox[] dayCheckboxes = (JCheckBox[]) configPanel.getClientProperty("dayCheckboxes");
            Set<DayOfWeek> activeDays = EnumSet.noneOf(DayOfWeek.class);
            
            // Map checkbox indices to DayOfWeek enum values (0 = Monday)
            if (dayCheckboxes[0].isSelected()) activeDays.add(DayOfWeek.MONDAY);
            if (dayCheckboxes[1].isSelected()) activeDays.add(DayOfWeek.TUESDAY);
            if (dayCheckboxes[2].isSelected()) activeDays.add(DayOfWeek.WEDNESDAY);
            if (dayCheckboxes[3].isSelected()) activeDays.add(DayOfWeek.THURSDAY);
            if (dayCheckboxes[4].isSelected()) activeDays.add(DayOfWeek.FRIDAY);
            if (dayCheckboxes[5].isSelected()) activeDays.add(DayOfWeek.SATURDAY);
            if (dayCheckboxes[6].isSelected()) activeDays.add(DayOfWeek.SUNDAY);
            
            return new DayOfWeekCondition(activeDays);
    }
    /**
     * Creates a panel for configuring Inventory Item Count conditions
     */
    public static void createInventoryItemCountPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel) {
        // Title
        JLabel titleLabel = new JLabel("Inventory Item Count:");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);
        
        // Item name input
        gbc.gridy++;
        JPanel itemNamePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        itemNamePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel itemNameLabel = new JLabel("Item Name (leave empty for any):");
        itemNameLabel.setForeground(Color.WHITE);
        itemNamePanel.add(itemNameLabel);
        
        JTextField itemNameField = new JTextField(15);
        itemNameField.setForeground(Color.WHITE);
        itemNameField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        itemNamePanel.add(itemNameField);
        
        panel.add(itemNamePanel, gbc);
        
        // Count range
        gbc.gridy++;
        JPanel countPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        countPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minCountLabel = new JLabel("Min Count:");
        minCountLabel.setForeground(Color.WHITE);
        countPanel.add(minCountLabel);
        
        SpinnerNumberModel minCountModel = new SpinnerNumberModel(10, 0, Integer.MAX_VALUE, 1);
        JSpinner minCountSpinner = new JSpinner(minCountModel);
        countPanel.add(minCountSpinner);
        
        JLabel maxCountLabel = new JLabel("Max Count:");
        maxCountLabel.setForeground(Color.WHITE);
        countPanel.add(maxCountLabel);
        
        SpinnerNumberModel maxCountModel = new SpinnerNumberModel(10, 0, Integer.MAX_VALUE, 1);
        JSpinner maxCountSpinner = new JSpinner(maxCountModel);
        countPanel.add(maxCountSpinner);
        
        // Link the min and max spinners
        minCountSpinner.addChangeListener(e -> {
            int minValue = (Integer) minCountSpinner.getValue();
            int maxValue = (Integer) maxCountSpinner.getValue();
            if (minValue > maxValue) {
                maxCountSpinner.setValue(minValue);
            }
        });
        
        maxCountSpinner.addChangeListener(e -> {
            int minValue = (Integer) minCountSpinner.getValue();
            int maxValue = (Integer) maxCountSpinner.getValue();
            if (maxValue < minValue) {
                minCountSpinner.setValue(maxValue);
            }
        });
        
        panel.add(countPanel, gbc);
        
        // Options panel
        gbc.gridy++;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JCheckBox includeNotedCheckbox = new JCheckBox("Include noted items");
        includeNotedCheckbox.setForeground(Color.WHITE);
        includeNotedCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        includeNotedCheckbox.setSelected(true);
        optionsPanel.add(includeNotedCheckbox);
        
        JCheckBox randomizeCheckbox = new JCheckBox("Randomize target count");
        randomizeCheckbox.setForeground(Color.WHITE);
        randomizeCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckbox.setSelected(true);
        optionsPanel.add(randomizeCheckbox);
        
        panel.add(optionsPanel, gbc);
        
        // Add a helpful description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Plugin will stop when you have the target number of items");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        gbc.gridy++;
        JLabel regexLabel = new JLabel("Item name supports regex patterns (.*bones.*)");
        regexLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        regexLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(regexLabel, gbc);
        
        // Store the components for later access
        configPanel.putClientProperty("itemNameField", itemNameField);
        configPanel.putClientProperty("minCountSpinner", minCountSpinner);
        configPanel.putClientProperty("maxCountSpinner", maxCountSpinner);
        configPanel.putClientProperty("includeNotedCheckbox", includeNotedCheckbox);
        configPanel.putClientProperty("randomizeCheckbox", randomizeCheckbox);
    }
    public static InventoryItemCountCondition createInventoryItemCountCondition(JPanel configPanel) {
        JTextField itemNameField = (JTextField) configPanel.getClientProperty("itemNameField");
        JSpinner minCountSpinner = (JSpinner) configPanel.getClientProperty("minCountSpinner");
        JSpinner maxCountSpinner = (JSpinner) configPanel.getClientProperty("maxCountSpinner");
        JCheckBox includeNotedCheckbox = (JCheckBox) configPanel.getClientProperty("includeNotedCheckbox");
        JCheckBox randomizeCheckbox = (JCheckBox) configPanel.getClientProperty("randomizeCheckbox");
        
        String itemName = itemNameField.getText().trim();
        int minCount = (Integer) minCountSpinner.getValue();
        int maxCount = (Integer) maxCountSpinner.getValue();
        boolean includeNoted = includeNotedCheckbox.isSelected();
        boolean randomize = randomizeCheckbox.isSelected();
        
        if (randomize && minCount != maxCount) {
            return InventoryItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(minCount)
                    .targetCountMax(maxCount)
                    .includeNoted(includeNoted)
                    .build();
        } else {
            return InventoryItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(minCount)
                    .targetCountMax(minCount)                    
                    .includeNoted(includeNoted)
                    .build();
        }
    }
    /**
     * Creates a panel for configuring Bank Item Count conditions
     */
    public static void createBankItemCountPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel) {
        // Title
        JLabel titleLabel = new JLabel("Bank Item Count:");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);
        
        // Item name input
        gbc.gridy++;
        JPanel itemNamePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        itemNamePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel itemNameLabel = new JLabel("Item Name (leave empty for total items):");
        itemNameLabel.setForeground(Color.WHITE);
        itemNamePanel.add(itemNameLabel);
        
        JTextField itemNameField = new JTextField(15);
        itemNameField.setForeground(Color.WHITE);
        itemNameField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        itemNamePanel.add(itemNameField);
        
        panel.add(itemNamePanel, gbc);
        
        // Count range
        gbc.gridy++;
        JPanel countPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        countPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minCountLabel = new JLabel("Min Count:");
        minCountLabel.setForeground(Color.WHITE);
        countPanel.add(minCountLabel);
        
        SpinnerNumberModel minCountModel = new SpinnerNumberModel(100, 0, Integer.MAX_VALUE, 10);
        JSpinner minCountSpinner = new JSpinner(minCountModel);
        countPanel.add(minCountSpinner);
        
        JLabel maxCountLabel = new JLabel("Max Count:");
        maxCountLabel.setForeground(Color.WHITE);
        countPanel.add(maxCountLabel);
        
        SpinnerNumberModel maxCountModel = new SpinnerNumberModel(100, 0, Integer.MAX_VALUE, 10);
        JSpinner maxCountSpinner = new JSpinner(maxCountModel);
        countPanel.add(maxCountSpinner);
        
        // Link the min and max spinners
        minCountSpinner.addChangeListener(e -> {
            int minValue = (Integer) minCountSpinner.getValue();
            int maxValue = (Integer) maxCountSpinner.getValue();
            if (minValue > maxValue) {
                maxCountSpinner.setValue(minValue);
            }
        });
        
        maxCountSpinner.addChangeListener(e -> {
            int minValue = (Integer) minCountSpinner.getValue();
            int maxValue = (Integer) maxCountSpinner.getValue();
            if (maxValue < minValue) {
                minCountSpinner.setValue(maxValue);
            }
        });
        
        panel.add(countPanel, gbc);
        
        // Options panel
        gbc.gridy++;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JCheckBox randomizeCheckbox = new JCheckBox("Randomize target count");
        randomizeCheckbox.setForeground(Color.WHITE);
        randomizeCheckbox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckbox.setSelected(true);
        optionsPanel.add(randomizeCheckbox);
        
        panel.add(optionsPanel, gbc);
        
        // Add a helpful description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Plugin will stop when you have the target number of items in bank");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        gbc.gridy++;
        JLabel regexLabel = new JLabel("Item name supports regex patterns (.*rune.*)");
        regexLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        regexLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(regexLabel, gbc);
        
        // Store the components for later access
        configPanel.putClientProperty("bankItemNameField", itemNameField);
        configPanel.putClientProperty("bankMinCountSpinner", minCountSpinner);
        configPanel.putClientProperty("bankMaxCountSpinner", maxCountSpinner);
        configPanel.putClientProperty("bankRandomizeCheckbox", randomizeCheckbox);
    }

   

    public static BankItemCountCondition createBankItemCountCondition(JPanel configPanel) {
        JTextField itemNameField = (JTextField) configPanel.getClientProperty("bankItemNameField");
        JSpinner minCountSpinner = (JSpinner) configPanel.getClientProperty("bankMinCountSpinner");
        JSpinner maxCountSpinner = (JSpinner) configPanel.getClientProperty("bankMaxCountSpinner");
        JCheckBox randomizeCheckbox = (JCheckBox) configPanel.getClientProperty("bankRandomizeCheckbox");
        
        String itemName = itemNameField.getText().trim();
        int minCount = (Integer) minCountSpinner.getValue();
        int maxCount = (Integer) maxCountSpinner.getValue();
        boolean randomize = randomizeCheckbox.isSelected();
        
        if (randomize && minCount != maxCount) {
            return BankItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(minCount)
                    .targetCountMax(maxCount)
                    .build();
        } else {
            return BankItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(minCount)
                    .targetCountMax(minCount)                    
                    .build();
        }
    }
    // Improved Item Collection panel
    public static void createItemConfigPanel(JPanel panel, GridBagConstraints gbc, JPanel configPanel, boolean stopConditionPanel) {
        JLabel itemsLabel = new JLabel("Item Names (comma-separated, supports regex):");
        itemsLabel.setForeground(Color.WHITE);
        panel.add(itemsLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        JTextField itemsField = new JTextField();
        itemsField.setToolTipText("Examples: 'Bones', 'Shark|Lobster', '.*rune$' (all items ending with 'rune')");
        panel.add(itemsField, gbc);
        
        // Logical operator selection (AND/OR) - only visible with multiple items
        gbc.gridy++;
        JPanel logicalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        logicalPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel logicalLabel = new JLabel("Multiple items logic:");
        logicalLabel.setForeground(Color.WHITE);
        logicalPanel.add(logicalLabel);
        
        JRadioButton andRadioButton = new JRadioButton("Need ALL items (AND)");
        andRadioButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        andRadioButton.setForeground(Color.WHITE);
        andRadioButton.setSelected(true);
        
        JRadioButton orRadioButton = new JRadioButton("Need ANY item (OR)");
        orRadioButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        orRadioButton.setForeground(Color.WHITE);
        
        ButtonGroup logicGroup = new ButtonGroup();
        logicGroup.add(andRadioButton);
        logicGroup.add(orRadioButton);
        
        logicalPanel.add(andRadioButton);
        logicalPanel.add(orRadioButton);
        
        // Initially hide the panel - will be shown only when there are commas in the text field
        logicalPanel.setVisible(false);
        panel.add(logicalPanel, gbc);
        
        // Listen for changes in the text field to show/hide the logical panel
        itemsField.getDocument().addDocumentListener(new DocumentListener() {
            private void updateLogicalPanelVisibility() {
                String text = itemsField.getText().trim();
                boolean hasMultipleItems = text.contains(",");
                logicalPanel.setVisible(hasMultipleItems);
                panel.revalidate();
                panel.repaint();
            }
            
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateLogicalPanelVisibility();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                updateLogicalPanelVisibility();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateLogicalPanelVisibility();
            }
        });
        
        // Target amount panel
        gbc.gridy++;
        JPanel amountPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        amountPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel amountLabel = new JLabel("Target Amount:");
        amountLabel.setForeground(Color.WHITE);
        amountPanel.add(amountLabel);
        
        SpinnerNumberModel amountModel = new SpinnerNumberModel(100, 1, Integer.MAX_VALUE, 10);
        JSpinner amountSpinner = new JSpinner(amountModel);
        amountSpinner.setPreferredSize(new Dimension(100, amountSpinner.getPreferredSize().height));
        amountPanel.add(amountSpinner);
        
        JCheckBox sameAmountCheckBox = new JCheckBox("Same amount for all items");
        sameAmountCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sameAmountCheckBox.setForeground(Color.WHITE);
        sameAmountCheckBox.setSelected(true);
        sameAmountCheckBox.setVisible(false); // Only show with multiple items
        amountPanel.add(sameAmountCheckBox);
        
        JCheckBox randomizeCheckBox = new JCheckBox("Randomize");
        randomizeCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizeCheckBox.setForeground(Color.WHITE);
        amountPanel.add(randomizeCheckBox);
        
        if (!stopConditionPanel) {
            randomizeCheckBox.setVisible(false);
        }
        
        panel.add(amountPanel, gbc);
        
        // Min/Max panel
        gbc.gridy++;
        JPanel minMaxPanel = new JPanel(new GridLayout(1, 4, 5, 0));
        minMaxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min:");
        minLabel.setForeground(Color.WHITE);
        minMaxPanel.add(minLabel);
        
        SpinnerNumberModel minModel = new SpinnerNumberModel(50, 1, Integer.MAX_VALUE, 10);
        JSpinner minSpinner = new JSpinner(minModel);
        minMaxPanel.add(minSpinner);
        
        JLabel maxLabel = new JLabel("Max:");
        maxLabel.setForeground(Color.WHITE);
        minMaxPanel.add(maxLabel);
        
        SpinnerNumberModel maxModel = new SpinnerNumberModel(150, 1, Integer.MAX_VALUE, 10);
        JSpinner maxSpinner = new JSpinner(maxModel);
        minMaxPanel.add(maxSpinner);
        
        minMaxPanel.setVisible(false);
        panel.add(minMaxPanel, gbc);
        
        // Toggle min/max panel based on randomize checkbox
        randomizeCheckBox.addChangeListener(e -> {
            minMaxPanel.setVisible(randomizeCheckBox.isSelected());
            amountSpinner.setEnabled(!randomizeCheckBox.isSelected());
            
            // If enabling randomize, set min/max from current amount
            if (randomizeCheckBox.isSelected()) {
                int amount = (Integer) amountSpinner.getValue();
                minSpinner.setValue(Math.max(1, amount - 50));
                maxSpinner.setValue(amount + 50);
            }
            
            panel.revalidate();
            panel.repaint();
        });
        
        // Link sameAmountCheckBox visibility to comma presence
        itemsField.getDocument().addDocumentListener(new DocumentListener() {
            private void updateCheckBoxVisibility() {
                String text = itemsField.getText().trim();
                boolean hasMultipleItems = text.contains(",");
                sameAmountCheckBox.setVisible(hasMultipleItems);
                panel.revalidate();
                panel.repaint();
            }
            
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCheckBoxVisibility();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCheckBoxVisibility();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCheckBoxVisibility();
            }
        });
        
        // Add value change listeners for min/max validation
        minSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (min > max) {
                maxSpinner.setValue(min);
            }
        });
        
        maxSpinner.addChangeListener(e -> {
            int min = (Integer) minSpinner.getValue();
            int max = (Integer) maxSpinner.getValue();
            
            if (max < min) {
                minSpinner.setValue(max);
            }
        });
        
        // Description
        gbc.gridy++;
        JLabel descriptionLabel;
        if (stopConditionPanel) {
            descriptionLabel = new JLabel("Plugin will stop when target amount of items is collected");
        } else {
            descriptionLabel = new JLabel("Plugin will only start when you have the required items");
        }
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Add regex help
        gbc.gridy++;
        JLabel regexHelpLabel = new JLabel("Regex help: Use '|' for OR, '.*' for any characters");
        regexHelpLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        regexHelpLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(regexHelpLabel, gbc);
        
        // Store components
        configPanel.putClientProperty("itemsField", itemsField);
        configPanel.putClientProperty("andRadioButton", andRadioButton);
        configPanel.putClientProperty("sameAmountCheckBox", sameAmountCheckBox);
        configPanel.putClientProperty("amountSpinner", amountSpinner);
        configPanel.putClientProperty("minAmountSpinner", minSpinner);
        configPanel.putClientProperty("maxAmountSpinner", maxSpinner);
        configPanel.putClientProperty("randomizeItemAmount", randomizeCheckBox);
    }
    public static Condition createItemCondition(JPanel configPanel) {
        JTextField itemsField = (JTextField) configPanel.getClientProperty("itemsField");
        JRadioButton andRadioButton = (JRadioButton) configPanel.getClientProperty("andRadioButton");
        JCheckBox sameAmountCheckBox = (JCheckBox) configPanel.getClientProperty("sameAmountCheckBox");
        JCheckBox randomizeCheckBox = (JCheckBox) configPanel.getClientProperty("randomizeItemAmount");
        
        String itemNamesString = itemsField.getText().trim();
        if (itemNamesString.isEmpty()) {
            return null; // Invalid item name
        }
        
        // Split by comma and trim each item name
        String[] itemNamesArray = itemNamesString.split(",");
        List<String> itemNames = new ArrayList<>();
        
        for (String itemName : itemNamesArray) {
            itemName = itemName.trim();
            if (!itemName.isEmpty()) {
                itemNames.add(itemName);
            }
        }
        
        if (itemNames.isEmpty()) {
            return null;
        }
        boolean includeNoted = true;//TODO  must be implentincludeNotedCheckbox.isSelected();
        // If only one item, create a simple LootItemCondition
        if (itemNames.size() == 1) {
            if (randomizeCheckBox.isSelected()) {
                JSpinner minAmountSpinner = (JSpinner) configPanel.getClientProperty("minAmountSpinner");
                JSpinner maxAmountSpinner = (JSpinner) configPanel.getClientProperty("maxAmountSpinner");
                
                int minAmount = (Integer) minAmountSpinner.getValue();
                int maxAmount = (Integer) maxAmountSpinner.getValue();
                
                return LootItemCondition.createRandomized(itemNames.get(0), minAmount, maxAmount);
            } else {
                JSpinner amountSpinner = (JSpinner) configPanel.getClientProperty("amountSpinner");
                int amount = (Integer) amountSpinner.getValue();
                
                return new LootItemCondition(itemNames.get(0), amount,includeNoted );
            }
        }
        
        // For multiple items, create a logical condition based on selection
        boolean useAndLogic = andRadioButton.isSelected();
        boolean useSameAmount = sameAmountCheckBox != null && sameAmountCheckBox.isSelected();
        
        if (useSameAmount) {
            // Same target for all items
            if (randomizeCheckBox.isSelected()) {
                JSpinner minAmountSpinner = (JSpinner) configPanel.getClientProperty("minAmountSpinner");
                JSpinner maxAmountSpinner = (JSpinner) configPanel.getClientProperty("maxAmountSpinner");
                
                int minAmount = (Integer) minAmountSpinner.getValue();
                int maxAmount = (Integer) maxAmountSpinner.getValue();
                
                if (useAndLogic) {
                    return LootItemCondition.createAndCondition(itemNames, minAmount, maxAmount);
                } else {
                    return LootItemCondition.createOrCondition(itemNames, minAmount, maxAmount);
                }
            } else {
                JSpinner amountSpinner = (JSpinner) configPanel.getClientProperty("amountSpinner");
                int amount = (Integer) amountSpinner.getValue();
                
                if (useAndLogic) {
                    return LootItemCondition.createAndCondition(itemNames, amount, amount);
                } else {
                    return LootItemCondition.createOrCondition(itemNames, amount, amount);
                }
            }
        } else {
            // Individual targets for each item (for simplicity, we'll use the same target)
            if (randomizeCheckBox.isSelected()) {
                JSpinner minAmountSpinner = (JSpinner) configPanel.getClientProperty("minAmountSpinner");
                JSpinner maxAmountSpinner = (JSpinner) configPanel.getClientProperty("maxAmountSpinner");
                
                int minAmount = (Integer) minAmountSpinner.getValue();
                int maxAmount = (Integer) maxAmountSpinner.getValue();
                
                // Create lists of min/max amounts for each item
                List<Integer> minAmounts = new ArrayList<>();
                List<Integer> maxAmounts = new ArrayList<>();
                
                for (int i = 0; i < itemNames.size(); i++) {
                    minAmounts.add(minAmount);
                    maxAmounts.add(maxAmount);
                }
                
                if (useAndLogic) {
                    return LootItemCondition.createAndCondition(itemNames, minAmounts, maxAmounts);
                } else {
                    return LootItemCondition.createOrCondition(itemNames, minAmounts, maxAmounts);
                }
            } else {
                JSpinner amountSpinner = (JSpinner) configPanel.getClientProperty("amountSpinner");
                int amount = (Integer) amountSpinner.getValue();
                
                // Create lists of amounts for each item
                List<Integer> minAmounts = new ArrayList<>();
                List<Integer> maxAmounts = new ArrayList<>();
                
                for (int i = 0; i < itemNames.size(); i++) {
                    minAmounts.add(amount);
                    maxAmounts.add(amount);
                }
                
                if (useAndLogic) {
                    return LootItemCondition.createAndCondition(itemNames, minAmounts, maxAmounts);
                } else {
                    return LootItemCondition.createOrCondition(itemNames, minAmounts, maxAmounts);
                }
            }
        }
    }
}
