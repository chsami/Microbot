package net.runelite.client.plugins.microbot.util.antiban.ui;

import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;

import static net.runelite.client.plugins.microbot.util.antiban.ui.UiHelper.setupSlider;

public class MicroBreakPanel extends JPanel {
    private final JCheckBox isMicroBreakActive = new JCheckBox("Micro Break Active");
    private final JCheckBox takeMicroBreaks = new JCheckBox("Take Micro Breaks");
    private final JSlider microBreakDurationLow = new JSlider(1, 10, Rs2AntibanSettings.microBreakDurationLow);
    private final JSlider microBreakDurationHigh = new JSlider(1, 30, Rs2AntibanSettings.microBreakDurationHigh);
    private final JSlider microBreakChance = new JSlider(0, 100, (int) (Rs2AntibanSettings.microBreakChance * 100));
    private final JLabel microBreakDurationLowLabel = new JLabel("Micro Break Duration Low (min): " + Rs2AntibanSettings.microBreakDurationLow);
    private final JLabel microBreakDurationHighLabel = new JLabel("Micro Break Duration High (min): " + Rs2AntibanSettings.microBreakDurationHigh);
    private final JLabel microBreakChanceLabel = new JLabel("Micro Break Chance (%): " + (int) (Rs2AntibanSettings.microBreakChance * 100));

    public MicroBreakPanel() {
        // Set the layout manager for the panel to GridBagLayout
        setLayout(new GridBagLayout());
        setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        setupSlider(microBreakDurationLow, 1, 5, 1);
        setupSlider(microBreakDurationHigh, 5, 15, 1);
        setupSlider(microBreakChance, 20, 100, 10);

        // Create a GridBagConstraints object to define the layout settings for each component
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // Padding around components
        gbc.anchor = GridBagConstraints.WEST; // Align components to the left
        gbc.gridx = 0; // All components will be in column 0
        gbc.gridy = GridBagConstraints.RELATIVE; // Components will be placed in consecutive rows

        // Add the "Micro Break Active" checkbox
        add(isMicroBreakActive, gbc);

        // Add the "Take Micro Breaks" checkbox
        add(takeMicroBreaks, gbc);

        // Add the "Micro Break Duration Low" label
        add(microBreakDurationLowLabel, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        // Add the "Micro Break Duration Low" slider
        add(microBreakDurationLow, gbc);

        gbc.fill = GridBagConstraints.NONE;
        // Add the "Micro Break Duration High" label
        add(microBreakDurationHighLabel, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        // Add the "Micro Break Duration High" slider
        add(microBreakDurationHigh, gbc);

        gbc.fill = GridBagConstraints.NONE;
        // Add the "Micro Break Chance" label
        add(microBreakChanceLabel, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        // Add the "Micro Break Chance" slider
        add(microBreakChance, gbc);

        setupActionListeners();
    }

    private void setupActionListeners() {
        isMicroBreakActive.addActionListener(e -> Rs2AntibanSettings.microBreakActive = isMicroBreakActive.isSelected());
        takeMicroBreaks.addActionListener(e -> Rs2AntibanSettings.takeMicroBreaks = takeMicroBreaks.isSelected());
        microBreakDurationLow.addChangeListener(e -> {
            Rs2AntibanSettings.microBreakDurationLow = microBreakDurationLow.getValue();
            microBreakDurationLowLabel.setText("Micro Break Duration Low (min): " + microBreakDurationLow.getValue());
        });
        microBreakDurationHigh.addChangeListener(e -> {
            Rs2AntibanSettings.microBreakDurationHigh = microBreakDurationHigh.getValue();
            microBreakDurationHighLabel.setText("Micro Break Duration High (min): " + microBreakDurationHigh.getValue());
        });
        microBreakChance.addChangeListener(e -> {
            Rs2AntibanSettings.microBreakChance = microBreakChance.getValue() / 100.0;
            microBreakChanceLabel.setText("Micro Break Chance (%): " + microBreakChance.getValue());
        });
    }

    public void updateValues() {
        isMicroBreakActive.setSelected(Rs2AntibanSettings.microBreakActive);
        isMicroBreakActive.setEnabled(false);
        takeMicroBreaks.setSelected(Rs2AntibanSettings.takeMicroBreaks);
        microBreakDurationLow.setValue(Rs2AntibanSettings.microBreakDurationLow);
        microBreakDurationLowLabel.setText("Micro Break Duration Low (min): " + microBreakDurationLow.getValue());
        microBreakDurationHigh.setValue(Rs2AntibanSettings.microBreakDurationHigh);
        microBreakDurationHighLabel.setText("Micro Break Duration High (min): " + microBreakDurationHigh.getValue());
        microBreakChance.setValue((int) (Rs2AntibanSettings.microBreakChance * 100));
        microBreakChanceLabel.setText("Micro Break Chance (%): " + microBreakChance.getValue());
    }
}
