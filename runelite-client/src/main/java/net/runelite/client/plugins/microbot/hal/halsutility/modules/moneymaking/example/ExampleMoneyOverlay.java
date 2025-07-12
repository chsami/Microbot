package net.runelite.client.plugins.microbot.hal.halsutility.modules.moneymaking.example;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ExampleMoneyOverlay extends OverlayPanel {

    @Inject
    public ExampleMoneyOverlay() {
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(220, 150));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Example Money Making")
                    .color(Color.YELLOW)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right("Running")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Method:")
                    .right("Flipping")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Min Profit:")
                    .right("100 gp")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total Profit:")
                    .right("45,678 gp")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Items Flipped:")
                    .right("23")
                    .build());

        } catch (Exception ex) {
            System.out.println("ExampleMoneyOverlay error: " + ex.getMessage());
        }

        return super.render(graphics);
    }
} 