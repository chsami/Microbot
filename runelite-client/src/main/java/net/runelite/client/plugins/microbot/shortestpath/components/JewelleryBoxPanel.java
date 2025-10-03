package net.runelite.client.plugins.microbot.shortestpath.components;


import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBoxType;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

import static net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin.CONFIG_GROUP;


public class JewelleryBoxPanel extends JPanel {
    String JEWELLERY_BOX = "jewelleryBox";

    private JComboBox<JewelleryBoxType> jewelleryBoxCmb;

    public JewelleryBoxPanel() {
        setBorder(new TitledBorder("Jewellery Box"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets.set(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        // Jewellery box dropdown
        add(new JLabel("Type"), gbc);
        jewelleryBoxCmb = new JComboBox<>(JewelleryBoxType.values());
        gbc.gridx++;
        add(jewelleryBoxCmb, gbc);


        jewelleryBoxCmb.addActionListener(e -> saveJewelleryBoxToConfig());
        jewelleryBoxCmb.setEnabled(false);
        loadJewelleryBox();
    }

    public void detectJewelleryBox() {
        jewelleryBoxCmb.setSelectedItem(JewelleryBoxType.getCurrentJewelleryBoxType());
        saveJewelleryBoxToConfig();
    }

    public void loadJewelleryBox() {
        try {
            JewelleryBoxType loadedBox = Microbot.getConfigManager().getConfiguration(CONFIG_GROUP, JEWELLERY_BOX, JewelleryBoxType.class);
            jewelleryBoxCmb.setSelectedItem(loadedBox);
        } catch (NullPointerException e) {
            Microbot.log("Failed to load jewellery box config");
            jewelleryBoxCmb.setSelectedItem(JewelleryBoxType.NONE);
        }
    }

    private void saveJewelleryBoxToConfig() {
        JewelleryBoxType config = (JewelleryBoxType) jewelleryBoxCmb.getSelectedItem();
        if (config == null) {
            config = JewelleryBoxType.NONE;
        }
        Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, JEWELLERY_BOX, config);
    }

    public Set<PohTeleport> getTeleports() {
        JewelleryBoxType jewelleryBoxType = (JewelleryBoxType) jewelleryBoxCmb.getSelectedItem();
        return jewelleryBoxType == null ? Set.of() : new HashSet<>(jewelleryBoxType.getAvailableTeleports());
    }
}

