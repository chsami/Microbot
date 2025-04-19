package net.runelite.client.plugins.microbot.bee.chaosaltar;

import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

import java.awt.*;

public class ChaosAltarOverlay extends OverlayPanel {

    private final PanelComponent panelComponent = new PanelComponent();


    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();

        // Header
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Chaos Altar Bot")
                .build());

        // Wilderness Warning
        if (Rs2Pvp.isInWilderness()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("WARNING:")
                    .right("In Wilderness! Keep LiteMode of Player Monitor ON")
                    .build());
        }

        return panelComponent.render(graphics);
    }
}
