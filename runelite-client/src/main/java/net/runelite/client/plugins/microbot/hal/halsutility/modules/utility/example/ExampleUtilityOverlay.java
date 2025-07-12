package net.runelite.client.plugins.microbot.hal.halsutility.modules.utility.example;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ExampleUtilityOverlay extends OverlayPanel {

    @Inject
    public ExampleUtilityOverlay() {
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(220, 150));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Example Utility Module")
                    .color(Color.MAGENTA)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right("Running")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Type:")
                    .right("Auto Clicker")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Interval:")
                    .right("1000ms")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Randomized:")
                    .right("Yes")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Clicks:")
                    .right("1,234")
                    .build());

        } catch (Exception ex) {
            System.out.println("ExampleUtilityOverlay error: " + ex.getMessage());
        }

        return super.render(graphics);
    }
} 