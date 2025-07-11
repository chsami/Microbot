package net.runelite.client.plugins.microbot.hal.halsutility.modules.skilling.blessedwine;

import net.runelite.client.plugins.microbot.hal.halsutility.modules.skilling.blessedwine.BlessedWineModule;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class BlessedWineOverlay extends OverlayPanel {

    private final BlessedWineModule blessedWineModule;

    @Inject
    public BlessedWineOverlay(BlessedWineModule blessedWineModule) {
        this.blessedWineModule = blessedWineModule;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(220, 200));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Blessed Wine Module")
                    .color(Color.MAGENTA)
                    .build());

            // Get status from the static fields if the module is running
            if (blessedWineModule.isRunning()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status:")
                        .right(BlessedWineModule.status)
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Loop:")
                        .right(String.valueOf(BlessedWineModule.loopCount))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Total Loops:")
                        .right(String.valueOf(BlessedWineModule.totalLoops))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Wines Left:")
                        .right(String.valueOf(BlessedWineModule.totalWinesToBless))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Start XP:")
                        .right(String.valueOf(BlessedWineModule.startingXp))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Expected XP:")
                        .right(String.valueOf(BlessedWineModule.expectedXp))
                        .build());

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Current Gained XP:")
                        .right(String.valueOf(BlessedWineModule.endingXp))
                        .build());
            } else {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status:")
                        .right("Stopped")
                        .build());
            }

        } catch (Exception ex) {
            System.out.println("BlessedWineOverlay error: " + ex.getMessage());
        }

        return super.render(graphics);
    }
} 