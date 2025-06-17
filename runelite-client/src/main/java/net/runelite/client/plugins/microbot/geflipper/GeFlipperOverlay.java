package net.runelite.client.plugins.microbot.geflipper;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

public class GeFlipperOverlay extends OverlayPanel {
    private final GeFlipperPlugin plugin;

    @Inject
    GeFlipperOverlay(GeFlipperPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("GE Flipper")
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(Microbot.status)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Profit:")
                    .right(Integer.toString(plugin.getProfit()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Profit p/h:")
                    .right(Integer.toString(plugin.getProfitPerHour()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Run time:")
                    .right(TimeUtils.getFormattedDurationBetween(plugin.getStartTime(), Instant.now()))
                    .build());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
