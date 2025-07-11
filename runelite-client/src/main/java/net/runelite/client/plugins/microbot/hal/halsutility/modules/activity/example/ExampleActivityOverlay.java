package net.runelite.client.plugins.microbot.hal.halsutility.modules.activity.example;

import net.runelite.client.plugins.microbot.hal.halsutility.modules.activity.example.ExampleActivityModule;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ExampleActivityOverlay extends OverlayPanel {

    private final ExampleActivityModule activityModule;

    @Inject
    public ExampleActivityOverlay(ExampleActivityModule activityModule) {
        this.activityModule = activityModule;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(220, 150));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Example Activity Module")
                    .color(Color.BLUE)
                    .build());

            if (activityModule.isRunning()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status:")
                        .right("Running")
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Activity:")
                        .right("Clue Scroll")
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Step:")
                        .right("3/10")
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Auto Teleport:")
                        .right("Enabled")
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Stamina:")
                        .right("Disabled")
                        .build());
            } else {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status:")
                        .right("Stopped")
                        .build());
            }

        } catch (Exception ex) {
            System.out.println("ExampleActivityOverlay error: " + ex.getMessage());
        }

        return super.render(graphics);
    }
} 