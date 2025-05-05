package net.runelite.client.plugins.microbot.util.antiban.ui;

import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;

public class GeneralPanel extends JPanel {

    private final JCheckBox isEnabled = new JCheckBox("Enabled");
    private final JCheckBox universalAntiban = new JCheckBox("Universal Antiban");
    private final JCheckBox useContextualVariability = new JCheckBox("Use Contextual Variability");
    private final JCheckBox devDebug = new JCheckBox("Dev Debug");
    private final JCheckBox overwriteScriptSetting = new JCheckBox("Apply settings to all scripts");
    private final JButton universalAntibanSettings = new JButton("Universal Antiban Settings");

    public GeneralPanel() {

        isEnabled.setToolTipText("Enable the antiban system");
        universalAntiban.setToolTipText("Only enable universal antiban for plugins that hasn't implemented antiban");
        useContextualVariability.setToolTipText("Adjusts antiban behaviors based on the context of the players actions/activity. This is required for the universal antiban to work properly. Also vital for plugins that switch between different activities.");
        devDebug.setToolTipText("Enable debug messages for the antiban system");
        overwriteScriptSetting.setToolTipText("This is a dangerous setting and should be used with caution. It will apply the settings to all scripts, even if they have their own settings.");
        universalAntibanSettings.setToolTipText("Setups the universal antiban settings for plugins that hasn't implemented antiban");

        // Set the layout manager for the panel to GridBagLayout
        setLayout(new GridBagLayout());
        setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        // Create a GridBagConstraints object to define the layout settings for each component
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // Padding around components
        gbc.anchor = GridBagConstraints.WEST; // Align components to the left
        gbc.gridx = 0; // All components will be in column 0
        gbc.gridy = GridBagConstraints.RELATIVE; // Components will be placed in consecutive rows

        // Add the "Enabled" checkbox
        add(isEnabled, gbc);

        // Add the "Universal Antiban" checkbox
        add(universalAntiban, gbc);

        // Add the "Use Contextual Variability" checkbox
        add(useContextualVariability, gbc);

        // Add the "Dev Debug" checkbox
        add(devDebug, gbc);

        // Add the "Apply settings to all scripts (use at your own risk" checkbox
        add(overwriteScriptSetting, gbc);

        // Add the "Universal Antiban Settings" button
        add(universalAntibanSettings, gbc);

        setupActionListeners();
    }

    private void setupActionListeners() {
        isEnabled.addActionListener(e -> Rs2AntibanSettings.antibanEnabled = isEnabled.isSelected());
        universalAntiban.addActionListener(e -> Rs2AntibanSettings.universalAntiban = universalAntiban.isSelected());
        useContextualVariability.addActionListener(e -> Rs2AntibanSettings.contextualVariability = useContextualVariability.isSelected());
        devDebug.addActionListener(e -> Rs2AntibanSettings.devDebug = devDebug.isSelected());
        overwriteScriptSetting.addActionListener(e -> Rs2AntibanSettings.overwriteScriptSettings = overwriteScriptSetting.isSelected());
        universalAntibanSettings.addActionListener(e -> Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup());
    }

    public void updateValues() {
        isEnabled.setSelected(Rs2AntibanSettings.antibanEnabled);
        universalAntiban.setSelected(Rs2AntibanSettings.universalAntiban);
        useContextualVariability.setSelected(Rs2AntibanSettings.contextualVariability);
        devDebug.setSelected(Rs2AntibanSettings.devDebug);
        overwriteScriptSetting.setSelected(Rs2AntibanSettings.overwriteScriptSettings);
    }

}
