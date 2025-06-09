package net.runelite.client.plugins.microbot.hunterno.birdhunter;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.*;

public class BirdHunterOverlay extends OverlayPanel {
    private final Client client;
    private final BirdHunterConfig config;

    @Inject
    BirdHunterOverlay(Client client, BirdHunterPlugins plugin, BirdHunterConfig config) {
        super(plugin);
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(200, 300));
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Dropping starts at: ")
                .right(String.valueOf(BirdHunterScript.getRandomHandleInventoryTriggerThreshold()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Bury bones start at: ")
                .right(String.valueOf(BirdHunterScript.getRandomBoneThreshold()))
                .build());
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), BirdHunterScript.getInitialStartTile());
        int huntingRadius = config.huntingRadiusValue();
        if (localPoint != null) {
            Polygon tile = Perspective.getCanvasTileAreaPoly(client, localPoint, (huntingRadius * 2) + 1);
            if (tile != null) {
                graphics.drawPolygon(tile);
            }
        }


        return super.render(graphics);
    }
}
