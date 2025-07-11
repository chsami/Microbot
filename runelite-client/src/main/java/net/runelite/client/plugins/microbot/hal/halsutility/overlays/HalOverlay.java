package net.runelite.client.plugins.microbot.hal.halsutility.overlays;

import net.runelite.client.plugins.microbot.hal.halsutility.HalsUtilityandPluginsPlugin;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;

public class HalOverlay extends Overlay {
    private final HalsUtilityandPluginsPlugin plugin;

    @Inject
    public HalOverlay(HalsUtilityandPluginsPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.HIGH);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        graphics.setColor(Color.GREEN);
        graphics.drawString("Hal's Utility Plugin", 10, 20);
        return new Dimension(150, 30);
    }
}
