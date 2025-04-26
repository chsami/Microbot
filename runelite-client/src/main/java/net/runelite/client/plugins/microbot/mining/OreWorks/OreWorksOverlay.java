package net.runelite.client.plugins.microbot.OreWorks;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class OreWorksOverlay extends OverlayPanel {

    @Inject
    OreWorksOverlay(OreWorksPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 100));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("OreWorks v" + OreWorksScript.version)
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            if (Microbot.status != null && !Microbot.status.isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(Microbot.status)
                        .build());
            }

        } catch (Exception ex) {
            System.out.println("Overlay render error: " + ex.getMessage());
        }
        return super.render(graphics);
    }
}