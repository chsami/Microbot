package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.client.ui.overlay.OverlayUtil.renderPolygon;

public class AutoWoodcuttingOverlay extends OverlayPanel {
    private static final Color WHITE_TRANSLUCENT = new Color(255, 255, 255, 127);
    private final AutoWoodcuttingConfig config;

    @Inject
    AutoWoodcuttingOverlay(AutoWoodcuttingPlugin plugin, AutoWoodcuttingConfig config)
    {
        super(plugin);
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.HIGH);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {

            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro Woodcutting V" + AutoWoodcuttingScript.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .build());

            if (config.distanceToStray() < 21) {
                LocalPoint lp =  LocalPoint.fromWorld(Microbot.getClient(), AutoWoodcuttingScript.initPlayerLoc(config));
                if (lp != null) {
                    Polygon poly = Perspective.getCanvasTileAreaPoly(Microbot.getClient(), lp, config.distanceToStray() * 2);

                    if (poly != null)
                    {
                        renderPolygon(graphics, poly, WHITE_TRANSLUCENT);
                    }
                }
            }

        } catch(Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
