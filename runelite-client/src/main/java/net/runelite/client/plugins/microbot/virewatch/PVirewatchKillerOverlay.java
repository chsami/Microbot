package net.runelite.client.plugins.microbot.virewatch;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import javax.inject.Inject;
import java.awt.*;
import java.util.stream.Collectors;

import static net.runelite.client.ui.overlay.OverlayUtil.renderPolygon;

public class PVirewatchKillerOverlay extends Overlay {
    private final Client client;

    private final PVirewatchKillerPlugin plugin;
    private final ModelOutlineRenderer modelOutlineRenderer;

    private TileObject alterStatue;

    private final PVirewatchKillerConfig config;
    private final PVirewatchScript script;
    @Inject
    public PVirewatchKillerOverlay(Client client, PVirewatchKillerPlugin plugin, ModelOutlineRenderer modelOutlineRenderer, PVirewatchKillerConfig config, PVirewatchScript script) {
        this.client = client;
        this.plugin = plugin;
        this.modelOutlineRenderer = modelOutlineRenderer;
        this.config = config;
        this.script = script;

        setNaughty();
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.HIGH);

    }


    @Override
    public Dimension render(Graphics2D graphics) {

        alterStatue = Rs2GameObject.findObjectById(39234);

        if (alterStatue != null && !config.disableStatueOutline()) {
            renderOutline(alterStatue);
            renderOutline(graphics, alterStatue);
        }

        if (plugin.fightArea != null && !config.disableFightArea()) {
            for (int x = plugin.fightArea.getX(); x < plugin.fightArea.getX() + plugin.fightArea.getWidth(); x++) {
                for (int y = plugin.fightArea.getY(); y < plugin.fightArea.getY() + plugin.fightArea.getHeight(); y++) {
                    WorldPoint worldPoint = new WorldPoint(x, y, plugin.fightArea.getPlane());
                    drawTile(graphics, worldPoint, Color.YELLOW);
                }
            }
        }

        // render start location
        if (plugin.startingLocation != null) {
            LocalPoint startTile = LocalPoint.fromWorld(Microbot.getClient(), plugin.startingLocation);
            if (startTile != null) {
                Polygon safeSpotPoly = Perspective.getCanvasTileAreaPoly(Microbot.getClient(), startTile, 1);
                if (safeSpotPoly != null) {
                    renderPolygon(graphics, safeSpotPoly, Color.RED);
                }
            }
        }

        if (!config.disableNPCOutline()) {
            for (net.runelite.api.NPC npc : Rs2Npc.getAttackableNpcs("Vyrewatch Sentinel").collect(Collectors.toList())) {
                if (npc != null && npc.getCanvasTilePoly() != null) {
                    if (!plugin.fightArea.contains(npc.getWorldLocation())) continue;

                    try {
                        modelOutlineRenderer.drawOutline(npc, 2, Color.ORANGE, 4);
                        graphics.draw(npc.getCanvasTilePoly());
                    } catch (Exception ex) {
                        Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                    }
                }
            }
        }

        return null;
    }

    private void drawTile(Graphics2D graphics, WorldPoint point, Color color)
    {
        LocalPoint localPoint = LocalPoint.fromWorld(client, point);
        if (localPoint != null)
        {
            Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
            if (poly != null)
            {
                graphics.setColor(color);
                graphics.draw(poly);
            }
        }
    }

    private void renderOutline(TileObject gameObject)
    {
        if (gameObject != null)
        {
            modelOutlineRenderer.drawOutline(alterStatue, 2, Color.GREEN, 4);
        }
    }

    private void renderOutline(Graphics2D graphics, TileObject gameObject)
    {
        LocalPoint localPoint = gameObject.getLocalLocation();
        Polygon poly = gameObject.getCanvasTilePoly();
        if (poly != null && localPoint != null)
        {
            graphics.setColor(Color.GREEN);
            graphics.setStroke(new BasicStroke(2));
            graphics.draw(poly);
        }
    }
}
