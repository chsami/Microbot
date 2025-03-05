package net.runelite.client.plugins.microbot.runecrafting.rcx;


import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayLayer;

import javax.inject.Inject;
import java.awt.*;

public class RcxOverlay extends Overlay {
    private final RcxPlugin plugin;

    private Point dragPoint = null; // For drag handling
    private Point overlayPosition = new Point(10, 10); // Default position at top-left

    @Inject
    public RcxOverlay(RcxPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.HIGH);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // Draw draggable background
        graphics.setColor(new Color(0, 0, 0, 150)); // Semi-transparent black
        graphics.fillRect(overlayPosition.x, overlayPosition.y, 200, 100);

        // Draw title bar
        graphics.setColor(Color.DARK_GRAY);
        graphics.fillRect(overlayPosition.x, overlayPosition.y, 200, 20);
        graphics.setColor(Color.WHITE);
        graphics.drawString("FxRc", overlayPosition.x + 10, overlayPosition.y + 15);


        return new Dimension(200, 100);
    }
}

