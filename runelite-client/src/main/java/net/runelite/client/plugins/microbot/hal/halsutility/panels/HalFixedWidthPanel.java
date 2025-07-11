package net.runelite.client.plugins.microbot.hal.halsutility.panels;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;

public class HalFixedWidthPanel extends JPanel {
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
    }
} 