package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class GeFlipperOverlay extends OverlayPanel {
    @Inject
    GeFlipperOverlay(GeFlipperPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.HIGH);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(200, 300));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("GE Flipper " + GeFlipperScript.VERSION)
                .color(Color.GREEN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left(Microbot.status)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("GP: " + GeFlipperScript.currentGp)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Profit: " + GeFlipperScript.profit + " gp")
                .build());
        long elapsed = System.currentTimeMillis() - GeFlipperScript.startTime;
        double hours = elapsed / 3600000.0;
        double perHour = hours <= 0 ? 0 : GeFlipperScript.profit / hours;
        panelComponent.getChildren().add(LineComponent.builder()
                .left(String.format("Profit p/h: %.0f", perHour))
                .build());

        return super.render(graphics);
    }
}
