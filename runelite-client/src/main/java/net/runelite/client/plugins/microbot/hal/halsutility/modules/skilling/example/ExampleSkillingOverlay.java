package net.runelite.client.plugins.microbot.hal.halsutility.modules.skilling.example;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ExampleSkillingOverlay extends OverlayPanel {

    @Inject
    public ExampleSkillingOverlay() {
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(220, 150));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Example Skilling Module")
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right("Running")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Skill:")
                    .right("Woodcutting")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Auto Drop:")
                    .right("Enabled")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP Gained:")
                    .right("1,234")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Logs Cut:")
                    .right("56")
                    .build());

        } catch (Exception ex) {
            System.out.println("ExampleSkillingOverlay error: " + ex.getMessage());
        }

        return super.render(graphics);
    }
} 