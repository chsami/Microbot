package net.runelite.client.plugins.microbot.combathotkeys;

import com.google.common.base.Strings;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.awt.*;

public class CombatHotkeysOverlay extends Overlay {

    CombatHotkeysConfig config;
    @Inject
    CombatHotkeysOverlay(CombatHotkeysPlugin plugin, CombatHotkeysConfig config)
    {

        super(plugin);
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setNaughty();
        setLayer(OverlayLayer.ABOVE_SCENE);
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
//            panelComponent.setPreferredSize(new Dimension(150, 250));
//            panelComponent.getChildren().add(TitleComponent.builder()
//                    .text("Combat Hotkeys V0.0.1")
//                    .color(Color.GREEN)
//                    .build());

            if(config.yesDance()) {
                drawTile(graphics, config.tile1(), Color.GREEN, "Tile 1", new BasicStroke(2));
                drawTile(graphics, config.tile2(), Color.GREEN, "Tile 2", new BasicStroke(2));
            }
        } catch(Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return null;
    }
    private void drawTile(Graphics2D graphics, WorldPoint point, Color color, @Nullable String label, Stroke borderStroke)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (point.distanceTo(playerLocation) >= 32)
        {
            return;
        }

        LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient(), point);
        if (lp == null)
        {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(Microbot.getClient(), lp);
        if (poly != null)
        {
            OverlayUtil.renderPolygon(graphics, poly, color, new Color(0, 0, 0, 50), borderStroke);
        }

        if (!Strings.isNullOrEmpty(label))
        {
            Point canvasTextLocation = Perspective.getCanvasTextLocation(Microbot.getClient(), graphics, lp, label, 0);
            if (canvasTextLocation != null)
            {
                OverlayUtil.renderTextLocation(graphics, canvasTextLocation, label, color);
            }
        }
    }
}
