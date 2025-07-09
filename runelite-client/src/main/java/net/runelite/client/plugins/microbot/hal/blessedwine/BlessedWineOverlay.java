package net.runelite.client.plugins.microbot.hal.blessedwine;

import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class BlessedWineOverlay extends OverlayPanel {

    @Inject
    BlessedWineOverlay(BlessedWinePlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(220, 200));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text(getPlugin().getClass().getAnnotation(PluginDescriptor.class).description())
                    .color(Color.MAGENTA)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(BlessedWinePlugin.status)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Loop:")
                    .right(String.valueOf(BlessedWinePlugin.loopCount))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total Loops:")
                    .right(String.valueOf(BlessedWinePlugin.totalLoops))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Wines Left:")
                    .right(String.valueOf(BlessedWinePlugin.totalWinesToBless))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Start XP:")
                    .right(String.valueOf(BlessedWinePlugin.startingXp))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Expected XP:")
                    .right(String.valueOf(BlessedWinePlugin.expectedXp))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Current Gained XP:")
                    .right(String.valueOf(BlessedWinePlugin.endingXp))
                    .build());

        } catch (Exception ex) {
            System.out.println("BlessedWineOverlay error: " + ex.getMessage());
        }

        return super.render(graphics);
    }
}