package net.runelite.client.plugins.microbot.hal.halsutility.modules.bossing.example;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ExampleBossingOverlay extends OverlayPanel {

    @Inject
    public ExampleBossingOverlay() {
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(220, 150));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Example Bossing Module")
                    .color(Color.RED)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right("Running")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Boss:")
                    .right("Zulrah")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Prayer Flick:")
                    .right("Enabled")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Auto Eat:")
                    .right("Enabled")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kills:")
                    .right("12")
                    .build());

        } catch (Exception ex) {
            System.out.println("ExampleBossingOverlay error: " + ex.getMessage());
        }

        return super.render(graphics);
    }
} 