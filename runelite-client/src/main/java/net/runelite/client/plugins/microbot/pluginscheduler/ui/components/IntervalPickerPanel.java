package net.runelite.client.plugins.microbot.pluginscheduler.ui.components;

import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A reusable panel component for configuring time intervals with optional randomization.
 * Allows setting fixed intervals or randomized intervals with min/max values.
 */
public class IntervalPickerPanel extends JPanel {
    private JRadioButton fixedRadioButton;
    private JRadioButton randomizedRadioButton;
    private JPanel fixedPanel;
    private JPanel randomizedPanel;
    private JSpinner hoursSpinner;
    private JSpinner minutesSpinner;
    private JSpinner minHoursSpinner;
    private JSpinner minMinutesSpinner;
    private JSpinner maxHoursSpinner;
    private JSpinner maxMinutesSpinner;
    private JPanel presetPanel;
    private JPanel randomPresetPanel;
    private JComboBox<String> fixedPresetComboBox;
    private JComboBox<String> randomPresetComboBox;
    private List<Consumer<IntervalCondition>> changeListeners = new ArrayList<>();
    
    /**
     * Creates a new IntervalPickerPanel with default values
     */
    public IntervalPickerPanel() {
        this(true);
    }
    
    /**
     * Creates a new IntervalPickerPanel
     * 
     * @param includePresets Whether to include preset buttons
     */
    public IntervalPickerPanel(boolean includePresets) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(new EmptyBorder(5, 5, 5, 5));
        
        initComponents(includePresets);
    }
    
    private void initComponents(boolean includePresets) {
        // Interval type selection (fixed vs randomized)
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        ButtonGroup typeGroup = new ButtonGroup();
        
        fixedRadioButton = new JRadioButton("Fixed Interval");
        fixedRadioButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        fixedRadioButton.setForeground(Color.WHITE);
        fixedRadioButton.setSelected(true);
        
        randomizedRadioButton = new JRadioButton("Random Interval");
        randomizedRadioButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        randomizedRadioButton.setForeground(Color.WHITE);
        
        typeGroup.add(fixedRadioButton);
        typeGroup.add(randomizedRadioButton);
        
        typePanel.add(fixedRadioButton);
        typePanel.add(randomizedRadioButton);
        
        add(typePanel);
        add(Box.createVerticalStrut(5));
        
        // Fixed interval panel
        fixedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fixedPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel hoursLabel = new JLabel("Hours:");
        hoursLabel.setForeground(Color.WHITE);
        
        SpinnerNumberModel hoursModel = new SpinnerNumberModel(0, 0, 24, 1);
        hoursSpinner = new JSpinner(hoursModel);
        hoursSpinner.setPreferredSize(new Dimension(60, hoursSpinner.getPreferredSize().height));
        
        JLabel minutesLabel = new JLabel("Minutes:");
        minutesLabel.setForeground(Color.WHITE);
        
        SpinnerNumberModel minutesModel = new SpinnerNumberModel(30, 0, 59, 1);
        minutesSpinner = new JSpinner(minutesModel);
        minutesSpinner.setPreferredSize(new Dimension(60, minutesSpinner.getPreferredSize().height));
        
        fixedPanel.add(hoursLabel);
        fixedPanel.add(hoursSpinner);
        fixedPanel.add(minutesLabel);
        fixedPanel.add(minutesSpinner);
        
        add(fixedPanel);
        
        // Randomized interval panel
        randomizedPanel = new JPanel();
        randomizedPanel.setLayout(new BoxLayout(randomizedPanel, BoxLayout.Y_AXIS));
        randomizedPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Min interval
        JPanel minPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        minPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel minLabel = new JLabel("Min Interval - Hours:");
        minLabel.setForeground(Color.WHITE);
        
        SpinnerNumberModel minHoursModel = new SpinnerNumberModel(0, 0, 24, 1);
        minHoursSpinner = new JSpinner(minHoursModel);
        minHoursSpinner.setPreferredSize(new Dimension(60, minHoursSpinner.getPreferredSize().height));
        
        JLabel minMinutesLabel = new JLabel("Minutes:");
        minMinutesLabel.setForeground(Color.WHITE);
        
        SpinnerNumberModel minMinutesModel = new SpinnerNumberModel(30, 0, 59, 1);
        minMinutesSpinner = new JSpinner(minMinutesModel);
        minMinutesSpinner.setPreferredSize(new Dimension(60, minMinutesSpinner.getPreferredSize().height));
        
        minPanel.add(minLabel);
        minPanel.add(minHoursSpinner);
        minPanel.add(minMinutesLabel);
        minPanel.add(minMinutesSpinner);
        
        // Max interval
        JPanel maxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        maxPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel maxLabel = new JLabel("Max Interval - Hours:");
        maxLabel.setForeground(Color.WHITE);
        
        SpinnerNumberModel maxHoursModel = new SpinnerNumberModel(1, 0, 24, 1);
        maxHoursSpinner = new JSpinner(maxHoursModel);
        maxHoursSpinner.setPreferredSize(new Dimension(60, maxHoursSpinner.getPreferredSize().height));
        
        JLabel maxMinutesLabel = new JLabel("Minutes:");
        maxMinutesLabel.setForeground(Color.WHITE);
        
        SpinnerNumberModel maxMinutesModel = new SpinnerNumberModel(0, 0, 59, 1);
        maxMinutesSpinner = new JSpinner(maxMinutesModel);
        maxMinutesSpinner.setPreferredSize(new Dimension(60, maxMinutesSpinner.getPreferredSize().height));
        
        maxPanel.add(maxLabel);
        maxPanel.add(maxHoursSpinner);
        maxPanel.add(maxMinutesLabel);
        maxPanel.add(maxMinutesSpinner);
        
        randomizedPanel.add(minPanel);
        randomizedPanel.add(maxPanel);
        randomizedPanel.setVisible(false);
        
        add(randomizedPanel);
        
        // Add presets if requested
        if (includePresets) {
            add(Box.createVerticalStrut(5));
            
            // Fixed presets
            presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            presetPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            
            JLabel presetsLabel = new JLabel("Presets:");
            presetsLabel.setForeground(Color.WHITE);
            presetPanel.add(presetsLabel);
            
            String[][] presets = {                
                {"Select a preset...", "0", "0"},
                {"15m", "0", "15"},                                
                {"30m", "0", "30"},
                {"45m", "0", "45"},
                {"1h", "1", "0"},
                {"1h30m", "1", "30"},
                {"2h", "2", "0"},
                {"2h30m", "2", "30"},
                {"3h", "3", "0"},                
                {"3h30m", "3", "30"},
                {"4h", "4", "0"},
                {"4h30m", "4", "30"},                
                {"6h", "6", "0"}
            };
            
            fixedPresetComboBox = new JComboBox<>();
            for (String[] preset : presets) {
                fixedPresetComboBox.addItem(preset[0]);
            }
            fixedPresetComboBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
            fixedPresetComboBox.setForeground(Color.WHITE);
            fixedPresetComboBox.setPreferredSize(new Dimension(150, fixedPresetComboBox.getPreferredSize().height));
            fixedPresetComboBox.addActionListener(e -> {
                int selectedIndex = fixedPresetComboBox.getSelectedIndex();
                if (selectedIndex > 0) { // Skip the "Select a preset..." option
                    fixedRadioButton.setSelected(true);
                    updatePanelVisibility();
                    
                    // Apply preset values
                    String[] preset = presets[selectedIndex];
                    hoursSpinner.setValue(Integer.parseInt(preset[1]));
                    minutesSpinner.setValue(Integer.parseInt(preset[2]));
                    
                    // Also set reasonable min/max values
                    setMinMaxFromFixed();
                    
                    // Notify listeners
                    notifyChangeListeners();
                }
            });
            presetPanel.add(fixedPresetComboBox);
            
            add(presetPanel);
            
            // Random presets
            randomPresetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            randomPresetPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            
            JLabel randomPresetsLabel = new JLabel("Random Presets:");
            randomPresetsLabel.setForeground(Color.WHITE);
            randomPresetPanel.add(randomPresetsLabel);
            
            String[][] randomPresets = {
                {"Select a preset...", "0", "0", "0", "0"},
                {"1-5m", "0", "1", "0", "5"},
                {"5-10m", "0", "5", "0", "10"},
                {"10-15m", "0", "10", "0", "15"},
                {"15-30m", "0", "15", "0", "30"},
                {"30-60m", "0", "30", "1", "0"},
                {"45m-1h15m", "0", "45", "1", "15"},
                {"1-2h", "1", "0", "2", "0"},
                {"1-3h", "1", "0", "3", "0"},
                {"2-3h", "2", "0", "3", "0"},                
                {"2-4h", "2", "0", "4", "0"},
            };
            
            randomPresetComboBox = new JComboBox<>();
            for (String[] preset : randomPresets) {
                randomPresetComboBox.addItem(preset[0]);
            }
            randomPresetComboBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
            randomPresetComboBox.setForeground(Color.WHITE);
            randomPresetComboBox.setPreferredSize(new Dimension(150, randomPresetComboBox.getPreferredSize().height));
            randomPresetComboBox.addActionListener(e -> {
                int selectedIndex = randomPresetComboBox.getSelectedIndex();
                if (selectedIndex > 0) { // Skip the "Select a preset..." option
                    randomizedRadioButton.setSelected(true);
                    updatePanelVisibility();
                    
                    // Apply preset values to min
                    String[] preset = randomPresets[selectedIndex];
                    minHoursSpinner.setValue(Integer.parseInt(preset[1]));
                    minMinutesSpinner.setValue(Integer.parseInt(preset[2]));
                    
                    // Apply preset values to max
                    maxHoursSpinner.setValue(Integer.parseInt(preset[3]));
                    maxMinutesSpinner.setValue(Integer.parseInt(preset[4]));
                    
                    // Set fixed to average
                    setFixedFromMinMax();
                    
                    // Notify listeners
                    notifyChangeListeners();
                }
            });
            randomPresetPanel.add(randomPresetComboBox);
            
            randomPresetPanel.setVisible(false);
            add(randomPresetPanel);
        }
        
        // Add toggle listeners
        fixedRadioButton.addActionListener(e -> {
            updatePanelVisibility();
            notifyChangeListeners();
        });
        
        randomizedRadioButton.addActionListener(e -> {
            updatePanelVisibility();
            
            // Initialize min/max values if switching to random
            if (randomizedRadioButton.isSelected()) {
                setMinMaxFromFixed();
            } else {
                setFixedFromMinMax();
            }
            
            notifyChangeListeners();
        });
        
        // Add spinner change listeners
        hoursSpinner.addChangeListener(e -> {
            if (fixedRadioButton.isSelected()) {
                setMinMaxFromFixed();
            }
            notifyChangeListeners();
        });
        
        minutesSpinner.addChangeListener(e -> {
            if (fixedRadioButton.isSelected()) {
                setMinMaxFromFixed();
            }
            notifyChangeListeners();
        });
        
        // Add min/max validation
        minHoursSpinner.addChangeListener(e -> {
            validateMinMaxInterval(true);
            if (randomizedRadioButton.isSelected()) {
                setFixedFromMinMax();
            }
            notifyChangeListeners();
        });
        
        minMinutesSpinner.addChangeListener(e -> {
            validateMinMaxInterval(true);
            if (randomizedRadioButton.isSelected()) {
                setFixedFromMinMax();
            }
            notifyChangeListeners();
        });
        
        maxHoursSpinner.addChangeListener(e -> {
            validateMinMaxInterval(false);
            if (randomizedRadioButton.isSelected()) {
                setFixedFromMinMax();
            }
            notifyChangeListeners();
        });
        
        maxMinutesSpinner.addChangeListener(e -> {
            validateMinMaxInterval(false);
            if (randomizedRadioButton.isSelected()) {
                setFixedFromMinMax();
            }
            notifyChangeListeners();
        });
    }
    
    /**
     * Updates panel visibility based on the selected radio button
     */
    private void updatePanelVisibility() {
        boolean useFixed = fixedRadioButton.isSelected();
        fixedPanel.setVisible(useFixed);
        randomizedPanel.setVisible(!useFixed);
        
        if (presetPanel != null && randomPresetPanel != null) {
            presetPanel.setVisible(useFixed);
            randomPresetPanel.setVisible(!useFixed);
            
            // Reset combo boxes to first item when switching modes
            if (useFixed && fixedPresetComboBox != null) {
                fixedPresetComboBox.setSelectedIndex(0);
            } else if (!useFixed && randomPresetComboBox != null) {
                randomPresetComboBox.setSelectedIndex(0);
            }
        }
        
        revalidate();
        repaint();
    }
    
    /**
     * Validates that min <= max for the interval and adjusts if needed
     * 
     * @param isMinUpdated Whether the min value was updated (to determine which value to adjust)
     */
    private void validateMinMaxInterval(boolean isMinUpdated) {
        int minHours = (Integer) minHoursSpinner.getValue();
        int minMinutes = (Integer) minMinutesSpinner.getValue();
        int maxHours = (Integer) maxHoursSpinner.getValue();
        int maxMinutes = (Integer) maxMinutesSpinner.getValue();
        
        int minTotalMinutes = minHours * 60 + minMinutes;
        int maxTotalMinutes = maxHours * 60 + maxMinutes;
        
        if (isMinUpdated) {
            // If min was updated and exceeds max, adjust max
            if (minTotalMinutes > maxTotalMinutes) {
                maxHoursSpinner.setValue(minHours);
                maxMinutesSpinner.setValue(minMinutes);
            }
        } else {
            // If max was updated and is less than min, adjust min
            if (maxTotalMinutes < minTotalMinutes) {
                minHoursSpinner.setValue(maxHours);
                minMinutesSpinner.setValue(maxMinutes);
            }
        }
    }
    
    /**
     * Sets min/max values based on the fixed value (with some variation)
     */
    private void setMinMaxFromFixed() {
        int hours = (Integer) hoursSpinner.getValue();
        int minutes = (Integer) minutesSpinner.getValue();
        int totalMinutes = hours * 60 + minutes;
        
        // Set min to ~70% of fixed value
        int minTotalMinutes = Math.max(1, (int)(totalMinutes * 0.7));
        minHoursSpinner.setValue(minTotalMinutes / 60);
        minMinutesSpinner.setValue(minTotalMinutes % 60);
        
        // Set max to ~130% of fixed value
        int maxTotalMinutes = (int)(totalMinutes * 1.3);
        maxHoursSpinner.setValue(maxTotalMinutes / 60);
        maxMinutesSpinner.setValue(maxTotalMinutes % 60);
    }
    
    /**
     * Sets the fixed value based on the average of min and max
     */
    private void setFixedFromMinMax() {
        int minHours = (Integer) minHoursSpinner.getValue();
        int minMinutes = (Integer) minMinutesSpinner.getValue();
        int maxHours = (Integer) maxHoursSpinner.getValue();
        int maxMinutes = (Integer) maxMinutesSpinner.getValue();
        
        int minTotalMinutes = minHours * 60 + minMinutes;
        int maxTotalMinutes = maxHours * 60 + maxMinutes;
        int avgTotalMinutes = (minTotalMinutes + maxTotalMinutes) / 2;
        
        hoursSpinner.setValue(avgTotalMinutes / 60);
        minutesSpinner.setValue(avgTotalMinutes % 60);
    }
    
    /**
     * Creates an IntervalCondition based on the current settings
     * 
     * @return The configured IntervalCondition
     */
    public IntervalCondition createIntervalCondition() {
        boolean useFixed = fixedRadioButton.isSelected();
        
        if (useFixed) {
            // Get fixed duration
            int hours = (Integer) hoursSpinner.getValue();
            int minutes = (Integer) minutesSpinner.getValue();
            Duration duration = Duration.ofHours(hours).plusMinutes(minutes);
            
            return new IntervalCondition(duration);
        } else {
            // Get min/max durations for randomized interval
            int minHours = (Integer) minHoursSpinner.getValue();
            int minMinutes = (Integer) minMinutesSpinner.getValue();
            int maxHours = (Integer) maxHoursSpinner.getValue();
            int maxMinutes = (Integer) maxMinutesSpinner.getValue();
            
            Duration minDuration = Duration.ofHours(minHours).plusMinutes(minMinutes);
            Duration maxDuration = Duration.ofHours(maxHours).plusMinutes(maxMinutes);
            
            return IntervalCondition.createRandomized(minDuration, maxDuration);
        }
    }
    
    /**
     * Configures this panel based on an existing IntervalCondition
     * 
     * @param condition The interval condition to use for configuration
     */
    public void setIntervalCondition(IntervalCondition condition) {
        if (condition == null) {
            return;
        }
        
        // Check if this is a randomized min-max interval or a fixed interval
        boolean isRandomized = condition.isRandomize();
        
        if (isRandomized) {
            // Set to randomized mode
            randomizedRadioButton.setSelected(true);
            
            // Get duration values for min interval
            long minTotalMinutes = condition.getMinInterval().toMinutes();
            long minHours = minTotalMinutes / 60;
            long minMinutes = minTotalMinutes % 60;
            
            // Get duration values for max interval
            long maxTotalMinutes = condition.getMaxInterval().toMinutes();
            long maxHours = maxTotalMinutes / 60;
            long maxMinutes = maxTotalMinutes % 60;
            
            // Set values on spinners
            minHoursSpinner.setValue((int)minHours);
            minMinutesSpinner.setValue((int)minMinutes);
            maxHoursSpinner.setValue((int)maxHours);
            maxMinutesSpinner.setValue((int)maxMinutes);
            
            // Also update the fixed spinner with the average value
            long avgTotalMinutes = (minTotalMinutes + maxTotalMinutes) / 2;
            hoursSpinner.setValue((int)(avgTotalMinutes / 60));
            minutesSpinner.setValue((int)(avgTotalMinutes % 60));
        } else {
            // Use fixed mode
            fixedRadioButton.setSelected(true);
            
            // Get duration values from the base interval
            long totalMinutes = condition.getInterval().toMinutes();
            long hours = totalMinutes / 60;
            long minutes = totalMinutes % 60;
            
            // Set values on fixed spinners
            hoursSpinner.setValue((int)hours);
            minutesSpinner.setValue((int)minutes);
            
            // Set min/max values based on fixed value
            setMinMaxFromFixed();
        }
        
        // Update panel visibility
        updatePanelVisibility();
    }
    
    /**
     * Adds a change listener that will be notified when the interval configuration changes
     * 
     * @param listener A consumer that receives the updated IntervalCondition
     */
    public void addChangeListener(Consumer<IntervalCondition> listener) {
        changeListeners.add(listener);
    }
    
    /**
     * Notifies all change listeners with the current interval condition
     */
    private void notifyChangeListeners() {
        IntervalCondition condition = createIntervalCondition();
        for (Consumer<IntervalCondition> listener : changeListeners) {
            listener.accept(condition);
        }
    }
    
    /**
     * Gets the current fixed hours value
     */
    public int getFixedHours() {
        return (Integer) hoursSpinner.getValue();
    }
    
    /**
     * Gets the current fixed minutes value
     */
    public int getFixedMinutes() {
        return (Integer) minutesSpinner.getValue();
    }
    
    /**
     * Gets whether randomized interval mode is selected
     */
    public boolean isRandomized() {
        return randomizedRadioButton.isSelected();
    }
    
    /**
     * Sets the enabled state of all components
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        fixedRadioButton.setEnabled(enabled);
        randomizedRadioButton.setEnabled(enabled);
        hoursSpinner.setEnabled(enabled && fixedRadioButton.isSelected());
        minutesSpinner.setEnabled(enabled && fixedRadioButton.isSelected());
        minHoursSpinner.setEnabled(enabled && randomizedRadioButton.isSelected());
        minMinutesSpinner.setEnabled(enabled && randomizedRadioButton.isSelected());
        maxHoursSpinner.setEnabled(enabled && randomizedRadioButton.isSelected());
        maxMinutesSpinner.setEnabled(enabled && randomizedRadioButton.isSelected());
        
        // Update preset components if they exist
        if (fixedPresetComboBox != null) {
            fixedPresetComboBox.setEnabled(enabled);
        }
        
        if (randomPresetComboBox != null) {
            randomPresetComboBox.setEnabled(enabled);
        }
    }
}