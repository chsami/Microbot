package net.runelite.client.plugins.microbot.barrows;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.example.ExamplePlugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.inject.Named;
import java.awt.*;

public class BarrowsOverlay extends OverlayPanel {

    @Inject
    BarrowsOverlay(BarrowsPlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Barrows V1.0.0")
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .build());

            // Add chests count
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Chests looted:")
                    .right(Integer.toString(BarrowsScript.ChestsOpened))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Pieces found:")
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(BarrowsScript.barrowsPieces.toString())
                    .build());



        } catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
