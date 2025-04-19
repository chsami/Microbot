package net.runelite.client.plugins.microbot.aiofighter;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.aiofighter.model.Monster;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.client.plugins.microbot.aiofighter.combat.AttackNpcScript.filteredAttackableNpcs;
import static net.runelite.client.plugins.microbot.aiofighter.combat.FlickerScript.currentMonstersAttackingUs;
import static net.runelite.client.ui.overlay.OverlayUtil.renderPolygon;

public class AIOFighterOverlay extends OverlayPanel {

    private static final Color WHITE_TRANSLUCENT = new Color(0, 255, 255, 127);
    private static final Color RED_TRANSLUCENT = new Color(255, 0, 0, 127);
    private final ModelOutlineRenderer modelOutlineRenderer;
    private final int diameter = 100;
    private final Color borderColor = Color.WHITE;
    private final Stroke stroke = new BasicStroke(1);
    AIOFighterConfig config;

    @Inject
    private AIOFighterOverlay(ModelOutlineRenderer modelOutlineRenderer, AIOFighterConfig config) {
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.HIGH);
        setNaughty();
        this.config = config;
    }

    public static void renderMinimapRect(Client client, Graphics2D graphics, Point center, int width, int height, Color color) {
        double angle = client.getCameraYawTarget() * Math.PI / 1024.0d;

        graphics.setColor(color);
        graphics.rotate(angle, center.getX(), center.getY());
        graphics.fillRect(center.getX() - width / 2, center.getY() - height / 2, width, height);
        graphics.rotate(-angle, center.getX(), center.getY());
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (filteredAttackableNpcs == null) return null;

        LocalPoint lp =  LocalPoint.fromWorld(Microbot.getClient(), config.centerLocation());
        if (lp != null) {
            Polygon poly = Perspective.getCanvasTileAreaPoly(Microbot.getClient(), lp, config.attackRadius() * 2);

            if (poly != null)
            {
                renderPolygon(graphics, poly, WHITE_TRANSLUCENT);
            }
        }
        // render safe spot
        LocalPoint sslp = LocalPoint.fromWorld(Microbot.getClient(), config.safeSpot());
        if (sslp != null) {
            Polygon safeSpotPoly = Perspective.getCanvasTileAreaPoly(Microbot.getClient(), sslp, 1);
            if (safeSpotPoly != null && config.toggleSafeSpot()) {
                renderPolygon(graphics, safeSpotPoly, RED_TRANSLUCENT);
            }
        }

        for (Rs2NpcModel npc :
                filteredAttackableNpcs) {
            if (npc != null && npc.getCanvasTilePoly() != null) {
                try {
                    graphics.setColor(Color.CYAN);
                    modelOutlineRenderer.drawOutline(npc, 2, Color.RED, 4);
                    graphics.draw(npc.getCanvasTilePoly());
                } catch (Exception ex) {
                    Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                }
            }
        }

        for (Monster currentMonster: currentMonstersAttackingUs) {
            if (currentMonster != null && currentMonster.npc != null && currentMonster.npc.getCanvasTilePoly() != null) {
                try {
                    graphics.setColor(Color.CYAN);
                    modelOutlineRenderer.drawOutline(currentMonster.npc, 2, Color.RED, 4);
                    graphics.draw(currentMonster.npc.getCanvasTilePoly());
                    graphics.drawString("" + currentMonster.lastAttack,
                            (int) currentMonster.npc.getCanvasTilePoly().getBounds().getCenterX(),
                            (int) currentMonster.npc.getCanvasTilePoly().getBounds().getCenterY());
                } catch(Exception ex) {
                    System.out.println(ex.getMessage());
                }
            }
        }

        return super.render(graphics);
    }
}