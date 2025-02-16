package net.runelite.client.plugins.microbot.frosty.threatdetector;

import javax.inject.Inject;
import java.awt.*;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;

public class ThreatDetectorOverlay extends OverlayPanel {
    private final ThreatDetectorConfig config;
    private final Client client;
    private final Color transparent = new Color(0, 0, 0, 0);

    @Inject
    public ThreatDetectorOverlay(ThreatDetectorConfig config, Client client) {
        this.config = config;
        this.client = client;
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config.flashControl() == FlashSpeed.OFF) {
            graphics.setColor(transparent);
        } else {
            graphics.setColor(config.flashColor());
        }
        graphics.fillRect(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
        return client.getCanvas().getSize();
    }
}
