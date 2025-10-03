package net.runelite.client.plugins.microbot.shortestpath.components;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.poh.data.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin.CONFIG_GROUP;

public class CheckboxPanel extends JPanel {

    String MOUNTED_GLORY = "mountedGlory";
    String MOUNTED_DIGSITE = "mountedDigsite";
    String MOUNTED_XERICS = "mountedXerics";
    String MOUNTED_MYTHS = "mountedMyths";
    String FAIRY_RING = "fairyRing";
    String WILDY_OBELISK = "wildyObelisk";
    String SPIRIT_TREE = "spiritTree";

    private final JCheckBox mountedGloryCb;
    private final JCheckBox mountedDigsiteCb;
    private final JCheckBox mountedXericsCb;
    private final JCheckBox mountedMythCapeCb;
    public final JCheckBox fairyRingCb;
    public final JCheckBox spiritTreeCb;
    private final JCheckBox wildernessObeliskCb;

    public CheckboxPanel() {
        setBorder(new TitledBorder("House Features"));
        GridBagLayout layout = new GridBagLayout();
        setLayout(layout);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets.set(2, 4, 2, 4);

        mountedGloryCb = new JCheckBox("Mounted Amulet of Glory");
        mountedGloryCb.setEnabled(false);
        add(mountedGloryCb, gbc);

        gbc.gridy++;
        mountedDigsiteCb = new JCheckBox("Mounted Digsite Pendant");
        mountedDigsiteCb.setEnabled(false);
        add(mountedDigsiteCb, gbc);

        gbc.gridy++;
        mountedXericsCb = new JCheckBox("Mounted Xeric's Talisman");
        mountedXericsCb.setEnabled(false);
        add(mountedXericsCb, gbc);

        gbc.gridy++;
        mountedMythCapeCb = new JCheckBox("Mounted Mythical Cape");
        mountedMythCapeCb.setEnabled(false);
        add(mountedMythCapeCb, gbc);

        gbc.gridy++;
        fairyRingCb = new JCheckBox("Fairy ring");
        fairyRingCb.setEnabled(false);
        add(fairyRingCb, gbc);

        gbc.gridy++;
        spiritTreeCb = new JCheckBox("Spirit tree");
        spiritTreeCb.setEnabled(false);
        add(spiritTreeCb, gbc);

        gbc.gridy++;
        wildernessObeliskCb = new JCheckBox("Wilderness obelisk");
        wildernessObeliskCb.setEnabled(false);
        wildernessObeliskCb.setVisible(false);
        add(wildernessObeliskCb, gbc);

        hookListeners();
        loadConfigs();
    }

    private void hookListeners() {
        mountedGloryCb.addActionListener(e -> setBool(MOUNTED_GLORY, mountedGloryCb.isSelected()));
        mountedDigsiteCb.addActionListener(e -> setBool(MOUNTED_DIGSITE, mountedDigsiteCb.isSelected()));
        mountedXericsCb.addActionListener(e -> setBool(MOUNTED_XERICS, mountedXericsCb.isSelected()));
        mountedMythCapeCb.addActionListener(e -> setBool(MOUNTED_MYTHS, mountedMythCapeCb.isSelected()));
        fairyRingCb.addActionListener(e -> setBool(FAIRY_RING, fairyRingCb.isSelected()));
        spiritTreeCb.addActionListener(e -> setBool(SPIRIT_TREE, spiritTreeCb.isSelected()));
        wildernessObeliskCb.addActionListener(e -> setBool(WILDY_OBELISK, wildernessObeliskCb.isSelected()));
    }

    private void setBool(String key, boolean value) {
        Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, key, value);
    }

    private boolean getBool(String key) {
        try {
            return Microbot.getConfigManager().getConfiguration(CONFIG_GROUP, key, Boolean.class);
        } catch (NullPointerException e) {
            Microbot.log("Failed to poh checkbox config for " + key);
            return false;
        }
    }

    private void saveConfigs() {
        setBool(MOUNTED_GLORY, mountedGloryCb.isSelected());
        setBool(MOUNTED_DIGSITE, mountedDigsiteCb.isSelected());
        setBool(MOUNTED_XERICS, mountedXericsCb.isSelected());
        setBool(MOUNTED_MYTHS, mountedMythCapeCb.isSelected());
        setBool(FAIRY_RING, fairyRingCb.isSelected());
        setBool(SPIRIT_TREE, spiritTreeCb.isSelected());
        setBool(WILDY_OBELISK, wildernessObeliskCb.isSelected());
    }

    private void loadConfigs() {
        mountedGloryCb.setSelected(getBool(MOUNTED_GLORY));
        mountedDigsiteCb.setSelected(getBool(MOUNTED_DIGSITE));
        mountedXericsCb.setSelected(getBool(MOUNTED_XERICS));
        mountedMythCapeCb.setSelected(getBool(MOUNTED_MYTHS));
        fairyRingCb.setSelected(getBool(FAIRY_RING));
        spiritTreeCb.setSelected(getBool(SPIRIT_TREE));
        wildernessObeliskCb.setSelected(getBool(WILDY_OBELISK));
    }

    public void detectPohFacilities() {
        mountedGloryCb.setSelected(MountedGlory.getObject() != null);
        mountedDigsiteCb.setSelected(MountedDigsite.getObject() != null);
        mountedXericsCb.setSelected(MountedXerics.getObject() != null);
        mountedMythCapeCb.setSelected(MountedMythical.getObject() != null);
        fairyRingCb.setSelected(PohTeleports.getFairyRings() != null);
        spiritTreeCb.setSelected(PohTeleports.getSpiritTree() != null);
        saveConfigs();
    }


    public Set<PohTeleport> getTeleports() {
        Set<PohTeleport> teleports = new HashSet<>();
        if (mountedDigsiteCb.isSelected()) {
            teleports.addAll(Arrays.asList(MountedDigsite.values()));
        }
        if (mountedXericsCb.isSelected()) {
            teleports.addAll(Arrays.asList(MountedXerics.values()));
        }
        if (mountedGloryCb.isSelected()) {
            teleports.addAll(Arrays.asList(MountedGlory.values()));
        }
        if (mountedMythCapeCb.isSelected()) {
            teleports.addAll(Arrays.asList(MountedMythical.values()));
        }
        return teleports;
    }
}