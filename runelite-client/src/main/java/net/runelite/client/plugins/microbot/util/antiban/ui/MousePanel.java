package net.runelite.client.plugins.microbot.util.antiban.ui;

import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;

import static net.runelite.client.plugins.microbot.util.antiban.ui.UiHelper.setupSlider;

public class MousePanel extends JPanel
{
    private final JCheckBox useNaturalMouse = new JCheckBox("Use Natural Mouse");
    private final JCheckBox simulateMistakes = new JCheckBox("Simulate Mistakes");
    private final JCheckBox moveMouseOffScreen = new JCheckBox("Move Mouse Off Screen");
    private final JSlider moveMouseOffScreenChance = new JSlider(0, 100, (int) (Rs2AntibanSettings.moveMouseOffScreenChance * 100));
    private final JLabel moveMouseOffScreenChanceLabel = new JLabel("Move Mouse Off Screen (%): " + (int) (Rs2AntibanSettings.moveMouseOffScreenChance * 100));
    private final JCheckBox moveMouseRandomly = new JCheckBox("Move Mouse Randomly");
    private final JSlider moveMouseRandomlyChance = new JSlider(0, 100, (int) (Rs2AntibanSettings.moveMouseRandomlyChance * 100));
    private final JLabel moveMouseRandomlyChanceLabel = new JLabel("Random Mouse Movement (%): " + (int) (Rs2AntibanSettings.moveMouseRandomlyChance * 100));

    // 1) Add new components for Activity Intensity
    private final JLabel mouseSpeedLabel = new JLabel();
    private final JSlider mouseSpeedSlider = new JSlider(0, 4, 1); // default to index=1 (LOW)

    public MousePanel()
    {
        useNaturalMouse.setToolTipText("Simulate human-like mouse movements");
        simulateMistakes.setToolTipText("Simulate mistakes in mouse movements");
        moveMouseOffScreen.setToolTipText("Move the mouse off screen if activity cooldown is active");
        moveMouseOffScreenChance.setToolTipText("Chance to move the mouse off screen when activity cooldown is active");
        moveMouseRandomly.setToolTipText("Move the mouse randomly when activity cooldown is active");
        moveMouseRandomlyChance.setToolTipText("Chance to move the mouse randomly when activity cooldown is active");

        // Configure the new mouseSpeedSlider
        mouseSpeedSlider.setToolTipText("Controls the overall mouse speed/intensity");
        mouseSpeedSlider.setPaintTicks(true);
        mouseSpeedSlider.setPaintLabels(true);
        // This helper can be used if you'd like consistent look, or you can do it manually:
        setupSlider(mouseSpeedSlider, 0, 4, 1);

        setLayout(new GridBagLayout());
        setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        setupSlider(moveMouseRandomlyChance, 20, 100, 10);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;

        // Add the "Use Natural Mouse" checkbox
        add(useNaturalMouse, gbc);

        // Add a gap
        gbc.insets = new Insets(20, 5, 5, 5);
        add(Box.createVerticalStrut(15), gbc);

        gbc.insets = new Insets(5, 5, 5, 5);
        add(simulateMistakes, gbc);

        add(moveMouseOffScreen, gbc);
        add(moveMouseOffScreenChanceLabel, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(moveMouseOffScreenChance, gbc);

        gbc.fill = GridBagConstraints.NONE;
        add(moveMouseRandomly, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(moveMouseRandomlyChanceLabel, gbc);
        add(moveMouseRandomlyChance, gbc);

        // 2) Add new label and slider for "Mouse Speed" (ActivityIntensity)
        gbc.fill = GridBagConstraints.NONE;
        add(mouseSpeedLabel, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(mouseSpeedSlider, gbc);

        setupActionListeners();

        // Make sure the default values on the UI match the current settings
        updateValues();
    }

    private void setupActionListeners()
    {
        useNaturalMouse.addActionListener(e -> {
            Rs2AntibanSettings.naturalMouse = useNaturalMouse.isSelected();
            Rs2AntibanSettings.saveToProfile();
        });
        simulateMistakes.addActionListener(e -> {
            Rs2AntibanSettings.simulateMistakes = simulateMistakes.isSelected();
            Rs2AntibanSettings.saveToProfile();
        });
        moveMouseOffScreen.addActionListener(e -> {
            Rs2AntibanSettings.moveMouseOffScreen = moveMouseOffScreen.isSelected();
            Rs2AntibanSettings.saveToProfile();
        });
        moveMouseOffScreenChance.addChangeListener(e -> {
            Rs2AntibanSettings.moveMouseOffScreenChance = moveMouseOffScreenChance.getValue() / 100.0;
            moveMouseOffScreenChanceLabel.setText("Move Mouse Off Screen (%): " + moveMouseOffScreenChance.getValue());
            if (!moveMouseOffScreenChance.getValueIsAdjusting()) {
                Rs2AntibanSettings.saveToProfile();
            }
        });
        moveMouseRandomly.addActionListener(e -> {
            Rs2AntibanSettings.moveMouseRandomly = moveMouseRandomly.isSelected();
            Rs2AntibanSettings.saveToProfile();
        });
        moveMouseRandomlyChance.addChangeListener(e -> {
            Rs2AntibanSettings.moveMouseRandomlyChance = moveMouseRandomlyChance.getValue() / 100.0;
            moveMouseRandomlyChanceLabel.setText("Random Mouse Movement (%): " + moveMouseRandomlyChance.getValue());
            if (!moveMouseRandomlyChance.getValueIsAdjusting()) {
                Rs2AntibanSettings.saveToProfile();
            }
        });

        // 3) When mouseSpeedSlider changes, update the ActivityIntensity
        mouseSpeedSlider.addChangeListener(e -> {
            ActivityIntensity intensity = getActivityIntensityFromIndex(mouseSpeedSlider.getValue());
            Rs2Antiban.setActivityIntensity(intensity);
            mouseSpeedLabel.setText("Mouse Speed: " + intensity.getName()); // or getName(), etc.
        });
    }

    public void updateValues()
    {
        useNaturalMouse.setSelected(Rs2AntibanSettings.naturalMouse);
        simulateMistakes.setSelected(Rs2AntibanSettings.simulateMistakes);
        moveMouseOffScreen.setSelected(Rs2AntibanSettings.moveMouseOffScreen);
        moveMouseOffScreenChance.setValue((int) (Rs2AntibanSettings.moveMouseOffScreenChance * 100));
        moveMouseRandomly.setSelected(Rs2AntibanSettings.moveMouseRandomly);
        moveMouseRandomlyChance.setValue((int) (Rs2AntibanSettings.moveMouseRandomlyChance * 100));
        moveMouseRandomlyChanceLabel.setText("Random Mouse Movement (%): " + moveMouseRandomlyChance.getValue());

        // 4) Sync the ActivityIntensity slider + label with current settings
        ActivityIntensity currentIntensity = Rs2Antiban.getActivityIntensity();
        mouseSpeedSlider.setValue(getIndexFromActivityIntensity(currentIntensity));
        mouseSpeedLabel.setText("Mouse Speed: " + currentIntensity.getName());
    }

    // Helper to convert ActivityIntensity -> slider index
    private int getIndexFromActivityIntensity(ActivityIntensity intensity)
    {
        switch (intensity)
        {
            case VERY_LOW: return 0;
            case LOW: return 1;
            case MODERATE: return 2;
            case HIGH: return 3;
            case EXTREME: return 4;
            default: return 1;
        }
    }

    // Helper to convert slider index -> ActivityIntensity
    private ActivityIntensity getActivityIntensityFromIndex(int index)
    {
        switch (index)
        {
            case 0: return ActivityIntensity.VERY_LOW;
            case 1: return ActivityIntensity.LOW;
            case 2: return ActivityIntensity.MODERATE;
            case 3: return ActivityIntensity.HIGH;
            case 4: return ActivityIntensity.EXTREME;
            default: return ActivityIntensity.LOW;
        }
    }
}
