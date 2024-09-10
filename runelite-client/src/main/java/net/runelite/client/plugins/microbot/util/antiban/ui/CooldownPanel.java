package net.runelite.client.plugins.microbot.util.antiban.ui;

import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;

import static net.runelite.client.plugins.microbot.util.antiban.ui.UiHelper.setupSlider;

public class CooldownPanel extends JPanel {
    private final JCheckBox isActionCooldownActive = new JCheckBox("Action Cooldown Active");
    private final JSlider actionCooldownChance = new JSlider(0, 100, (int) (Rs2AntibanSettings.actionCooldownChance * 100));
    private final JSlider timeout = new JSlider(0, 60, Rs2Antiban.getTIMEOUT());
    private final JLabel actionCooldownChanceLabel = new JLabel("Action Cooldown Chance (%): " + (int) (Rs2AntibanSettings.actionCooldownChance * 100));
    private final JLabel timeoutLabel = new JLabel("Timeout (min): " + Rs2Antiban.getTIMEOUT());

    public CooldownPanel() {
        // Set the layout manager for the panel to GridBagLayout
        setLayout(new GridBagLayout());
        setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        setupSlider(actionCooldownChance, 20, 100, 10);
        setupSlider(timeout, 10, 60, 5);

        // Create a GridBagConstraints object to define the layout settings for each component
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // Padding around components
        gbc.anchor = GridBagConstraints.WEST; // Align components to the left
        gbc.gridx = 0; // All components will be in column 0
        gbc.gridy = GridBagConstraints.RELATIVE; // Components will be placed in consecutive rows

        // Add the "Action Cooldown Active" checkbox
        add(isActionCooldownActive, gbc);

        // Add the "Action Cooldown Chance" label
        add(actionCooldownChanceLabel, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        // Add the "Action Cooldown Chance" slider
        add(actionCooldownChance, gbc);

        gbc.fill = GridBagConstraints.NONE;
        // Add the "Timeout" label
        add(timeoutLabel, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        // Add the "Timeout" slider
        add(timeout, gbc);

        setupActionListeners();
    }

    private void setupActionListeners() {
        isActionCooldownActive.addActionListener(e -> Rs2AntibanSettings.actionCooldownActive = isActionCooldownActive.isSelected());
        actionCooldownChance.addChangeListener(e -> {
            Rs2AntibanSettings.actionCooldownChance = actionCooldownChance.getValue() / 100.0;
            actionCooldownChanceLabel.setText("Action Cooldown Chance (%): " + actionCooldownChance.getValue());
        });
        timeout.addChangeListener(e -> {
            Rs2Antiban.setTIMEOUT(timeout.getValue());
            timeoutLabel.setText("Timeout (min): " + timeout.getValue());
        });
    }

    public void updateValues() {
        isActionCooldownActive.setSelected(Rs2AntibanSettings.actionCooldownActive);
        isActionCooldownActive.setEnabled(false);
        actionCooldownChance.setValue((int) (Rs2AntibanSettings.actionCooldownChance * 100));
        actionCooldownChanceLabel.setText("Action Cooldown Chance (%): " + actionCooldownChance.getValue());
        timeout.setValue(Rs2Antiban.getTIMEOUT());
        timeoutLabel.setText("Timeout (min): " + timeout.getValue());

    }
}
