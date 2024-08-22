package net.runelite.client.plugins.microbot.PvPML;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class PvPMLOverlay extends OverlayPanel {

    @Inject
    PvPMLOverlay(PvPMLPlugin plugin)
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
                    .text("PvP ML Plugin V1.0.0")
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            // Assuming PvPMLPlugin has a method isModelRunning() to check model status
            assert getPlugin() != null;
            String modelStatus = ((PvPMLPlugin) getPlugin()).isModelRunning() ? "Running" : "Stopped";
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Model Status: " + modelStatus)
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
