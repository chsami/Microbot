package net.runelite.client.plugins.microbot.util.antiban.ui;

import javax.swing.*;
import java.awt.*;

public class BackgroundPanel extends JPanel {
    private final Image backgroundImage;

    public BackgroundPanel(Image backgroundImage) {
        this.backgroundImage = backgroundImage;
        // make sure the panel is opaque so paintComponent is honored
        setOpaque(true);
        setLayout(new BorderLayout());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // draw the image, scaling it to fill the panel’s entire area
        g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
    }
}