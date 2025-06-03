package net.runelite.client.plugins.microbot.BurgerLooter;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

import javax.inject.Inject;
import java.awt.*;

public class BurgerLooterOverlay extends Overlay {
    private final PanelComponent panel = new PanelComponent();
    private final BurgerLooterPlugin plugin;

    @Inject
    public BurgerLooterOverlay(BurgerLooterPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panel.getChildren().clear();
        panel.setPreferredSize(new Dimension(400, 120));
        panel.getChildren().add(LineComponent.builder()
            .left("BurgerLooter State:")
            .right(plugin.getStateName())
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Total Looted:")
            .right(String.valueOf(plugin.getTotalLooted()))
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Time Ran:")
            .right(plugin.getRunningTime())
            .build());
        return panel.render(graphics);
    }
}
