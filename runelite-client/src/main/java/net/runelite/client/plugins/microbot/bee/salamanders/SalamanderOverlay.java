package net.runelite.client.plugins.microbot.bee.salamanders;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

import javax.inject.Inject;
import java.awt.*;

public class SalamanderOverlay extends Overlay {
    private final Client client;
    private final SalamanderConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public SalamanderOverlay(Client client, SalamanderConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(200, 0));
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Salamander Script")
                .right("Running")
                .build());

        // Example additions: You can add trap count, caught count, etc.
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Hunter Level")
                .right(String.valueOf(client.getRealSkillLevel(net.runelite.api.Skill.HUNTER)))
                .build());

        return panelComponent.render(graphics);
    }
}