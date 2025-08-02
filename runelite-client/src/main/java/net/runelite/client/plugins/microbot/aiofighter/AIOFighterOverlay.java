package net.runelite.client.plugins.microbot.aiofighter;

import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.geometry.Geometry;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.aiofighter.combat.AttackNpcScript;
import net.runelite.client.plugins.microbot.aiofighter.model.Monster;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;

import static net.runelite.client.plugins.microbot.aiofighter.combat.AttackNpcScript.filteredAttackableNpcs;
import static net.runelite.client.plugins.microbot.aiofighter.combat.FlickerScript.currentMonstersAttackingUsRef;
import static net.runelite.client.ui.overlay.OverlayUtil.renderPolygon;

public class AIOFighterOverlay extends OverlayPanel {

    private static final Color WHITE_TRANSLUCENT = new Color(0, 255, 255, 127);
    private static final Color RED_TRANSLUCENT = new Color(255, 0, 0, 127);
    private static final int MAX_LOCAL_DRAW_LENGTH = 20 * Perspective.LOCAL_TILE_SIZE;
    private final ModelOutlineRenderer modelOutlineRenderer;
    private final int diameter = 100;
    private final Color borderColor = Color.WHITE;
    private final Stroke stroke = new BasicStroke(1);
    private final GeneralPath[] linesToDisplay = new GeneralPath[Constants.MAX_Z];
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
        if(AttackNpcScript.attackableArea == null) return null;
        if (filteredAttackableNpcs.get() == null) return null;

        calculateLinesToDisplay();
        GeneralPath lines = linesToDisplay[Microbot.getClient().getPlane()];
        LocalPoint lp =  LocalPoint.fromWorld(Microbot.getClient(), config.centerLocation());
        if (lp != null) {
            Polygon poly = Perspective.getCanvasTileAreaPoly(Microbot.getClient(), lp, config.attackRadius() * 2);

            if (poly != null)
            {
                //renderPolygon(graphics, poly, WHITE_TRANSLUCENT);
                renderPath(graphics,lines,WHITE_TRANSLUCENT);
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

        for (Rs2NpcModel npc : filteredAttackableNpcs.get()) {
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

        for (Monster currentMonster : currentMonstersAttackingUsRef.get()) {
            if (currentMonster != null && currentMonster.npc != null && currentMonster.npc.getCanvasTilePoly() != null) {
                try {
                    graphics.setColor(Color.CYAN);
                    modelOutlineRenderer.drawOutline(currentMonster.npc, 2, Color.RED, 4);
                    graphics.draw(currentMonster.npc.getCanvasTilePoly());
                    graphics.drawString("" + currentMonster.lastAttack,
                            (int) currentMonster.npc.getCanvasTilePoly().getBounds().getCenterX(),
                            (int) currentMonster.npc.getCanvasTilePoly().getBounds().getCenterY());
                    // draw the attack style
                    if (currentMonster.attackStyle != null) {
                        graphics.drawString(currentMonster.attackStyle.name(),
                                (int) currentMonster.npc.getCanvasTilePoly().getBounds().getCenterX(),
                                (int) currentMonster.npc.getCanvasTilePoly().getBounds().getCenterY() + 15);
                    }
                } catch(Exception ex) {
                    System.out.println(ex.getMessage());
                }
            }
        }

        return super.render(graphics);
    }

    private void renderPath(Graphics2D graphics, GeneralPath path, Color color)
    {
        LocalPoint playerLp = Microbot.getClient().getLocalPlayer().getLocalLocation();
        Rectangle viewArea = new Rectangle(
                playerLp.getX() - MAX_LOCAL_DRAW_LENGTH,
                playerLp.getY() - MAX_LOCAL_DRAW_LENGTH,
                MAX_LOCAL_DRAW_LENGTH * 2,
                MAX_LOCAL_DRAW_LENGTH * 2);

        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(3));

        path = Geometry.clipPath(path, viewArea);
        path = Geometry.filterPath(path, (p1, p2) ->
                Perspective.localToCanvas(Microbot.getClient(), new LocalPoint((int)p1[0], (int)p1[1]), Microbot.getClient().getPlane()) != null &&
                        Perspective.localToCanvas(Microbot.getClient(), new LocalPoint((int)p2[0], (int)p2[1]), Microbot.getClient().getPlane()) != null);
        path = Geometry.transformPath(path, coords ->
        {
            Point point = Perspective.localToCanvas(Microbot.getClient(), new LocalPoint((int)coords[0], (int)coords[1]), Microbot.getClient().getPlane());
            coords[0] = point.getX();
            coords[1] = point.getY();
        });

        graphics.draw(path);
    }

    private Area generateAttackableArea(Rs2WorldArea area) {
        Area attackableArea = new Area();
        Polygon poly = new Polygon();

        // add 4 points to the polygon for the corners of the area
        poly.addPoint(area.getX(), area.getY() );
        poly.addPoint(area.getX() + area.getWidth(), area.getY());
        poly.addPoint(area.getX() + area.getWidth(), area.getY() + area.getHeight());
        poly.addPoint(area.getX(), area.getY() + area.getHeight());
        // create a new area from the polygon
        attackableArea.add(new Area(poly));

        return attackableArea;
    }

    private void calculateLinesToDisplay()
    {

        Rectangle sceneRect = new Rectangle(
                Microbot.getClient().getTopLevelWorldView().getBaseX() + 1, Microbot.getClient().getTopLevelWorldView().getBaseY() + 1,
                Constants.SCENE_SIZE - 2, Constants.SCENE_SIZE - 2);

        for (int i = 0; i < linesToDisplay.length; i++)
        {
            GeneralPath lines = new GeneralPath(generateAttackableArea(AttackNpcScript.attackableArea));
            lines = Geometry.clipPath(lines, sceneRect);
            lines = Geometry.splitIntoSegments(lines, 1);
            lines = Geometry.transformPath(lines, this::transformWorldToLocal);
            linesToDisplay[i] = lines;
        }
    }

    private void transformWorldToLocal(float[] coords)
    {
        final LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient(), (int) coords[0], (int) coords[1]);
        coords[0] = lp.getX() - Perspective.LOCAL_TILE_SIZE / 2f;
        coords[1] = lp.getY() - Perspective.LOCAL_TILE_SIZE / 2f;
    }
}